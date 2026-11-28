package com.adhdcompanion.focus.data.ai

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.util.Base64
import com.adhdcompanion.focus.data.prefs.SecurePrefsManager
import com.adhdcompanion.focus.model.DoubtSolution
import com.adhdcompanion.focus.model.EnergyLevel
import com.google.gson.Gson
import com.google.gson.JsonParser
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.concurrent.TimeUnit

enum class DoubtBackend(val badgeLabel: String) {
    GROQ_GPT_OSS("Groq GPT-OSS"),
    GEMINI_FLASH_LITE("Gemini Flash-Lite"),
    OFFLINE_LITERT("Offline LiteRT")
}

sealed interface HybridDoubtEvent {
    data class Status(val backend: DoubtBackend, val message: String) : HybridDoubtEvent
    data class Delta(val backend: DoubtBackend, val fullText: String) : HybridDoubtEvent
    data class Completed(
        val backend: DoubtBackend,
        val fullText: String,
        val solution: DoubtSolution,
        val note: String? = null
    ) : HybridDoubtEvent

    data class Error(
        val message: String,
        val needsOfflineModel: Boolean = false
    ) : HybridDoubtEvent
}

private class HttpStatusException(val code: Int, message: String) : IOException(message)

/**
 * Hybrid Doubt Solver Engine with deterministic routing & progressive streaming:
 *  1. Online + Image -> Gemini (`gemini-3.5-flash-lite` streamGenerateContent),
 *     fallback to Local LiteRT-LM (text-only) or clear HTTP error.
 *  2. Online + Text -> Groq (`openai/gpt-oss-120b`, stream=true, max_tokens=1200, >6s timeout / 429 / 5xx)
 *     -> automatic fallback to Gemini (`gemini-3.5-flash-lite`) -> automatic fallback to Local LiteRT-LM.
 *  3. Offline -> Local LiteRT-LM (`filesDir/offline_model.litertlm`), or prompts user to import `.litertlm`.
 */
class HybridDoubtEngine(
    context: Context,
    private val prefs: SecurePrefsManager,
    private val localEngine: LocalLiteRtEngine
) {
    private val appContext = context.applicationContext
    private val gson = Gson()
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    private val groqClient = OkHttpClient.Builder()
        .connectTimeout(6, TimeUnit.SECONDS)
        .writeTimeout(6, TimeUnit.SECONDS)
        .readTimeout(6, TimeUnit.SECONDS)
        .callTimeout(25, TimeUnit.SECONDS)
        .build()

    private val geminiClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .callTimeout(60, TimeUnit.SECONDS)
        .build()

    fun isOnline(): Boolean {
        val cm = appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
        val activeNetwork = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(activeNetwork) ?: return false
        val hasInternet = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        val validated = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        return hasInternet && validated
    }

    fun solveDoubtStream(
        doubt: String,
        subject: String?,
        energyLevel: EnergyLevel?,
        imageUri: Uri?
    ): Flow<HybridDoubtEvent> = channelFlow {
        val userPrompt = buildUserPrompt(doubt, subject, energyLevel)

        // c) Offline: route directly to Local LiteRT-LM
        if (!isOnline()) {
            if (localEngine.hasModel()) {
                send(HybridDoubtEvent.Status(DoubtBackend.OFFLINE_LITERT, "Offline — running on-device LiteRT-LM..."))
                val note = if (imageUri != null) "Offline LiteRT analyzed your question text only." else null
                runLocalFallback(userPrompt, note) { event -> send(event) }
            } else {
                send(
                    HybridDoubtEvent.Error(
                        message = "You are offline and no .litertlm model is loaded yet. Tap 'Select Offline Model (.litertlm)' to import one.",
                        needsOfflineModel = true
                    )
                )
            }
            return@channelFlow
        }

        // a) Online + Image attached: compress to ~1024px JPEG -> Gemini 3.5 Flash-Lite
        if (imageUri != null) {
            val geminiKey = prefs.geminiApiKey
            if (!geminiKey.isNullOrBlank()) {
                send(HybridDoubtEvent.Status(DoubtBackend.GEMINI_FLASH_LITE, "Analyzing image with Gemini Flash-Lite..."))
                try {
                    val base64Jpeg = compressImageToBase64Jpeg(imageUri)
                    val fullText = streamGemini(
                        apiKey = geminiKey,
                        userPrompt = userPrompt,
                        base64Jpeg = base64Jpeg
                    ) { accumulated ->
                        send(HybridDoubtEvent.Delta(DoubtBackend.GEMINI_FLASH_LITE, accumulated))
                    }
                    send(
                        HybridDoubtEvent.Completed(
                            backend = DoubtBackend.GEMINI_FLASH_LITE,
                            fullText = fullText,
                            solution = parseDoubtSolution(fullText)
                        )
                    )
                    return@channelFlow
                } catch (ce: CancellationException) {
                    throw ce
                } catch (err: Exception) {
                    val codeNote = (err as? HttpStatusException)?.let { "HTTP ${it.code}" } ?: (err.message ?: "Request failed")
                    if (localEngine.hasModel()) {
                        send(
                            HybridDoubtEvent.Status(
                                DoubtBackend.OFFLINE_LITERT,
                                "Gemini image call failed ($codeNote) — falling back to offline text analysis..."
                            )
                        )
                        runLocalFallback(
                            userPrompt,
                            note = "Gemini image request failed ($codeNote); analyzed question text offline."
                        ) { event -> send(event) }
                    } else {
                        send(
                            HybridDoubtEvent.Error(
                                message = "Gemini Flash-Lite image error ($codeNote). Check your Gemini key or import an offline .litertlm model."
                            )
                        )
                    }
                    return@channelFlow
                }
            } else {
                if (localEngine.hasModel()) {
                    send(
                        HybridDoubtEvent.Status(
                            DoubtBackend.OFFLINE_LITERT,
                            "No Gemini key for image analysis — analyzing text offline..."
                        )
                    )
                    runLocalFallback(
                        userPrompt,
                        note = "Add a Gemini API key in Settings for image analysis. Analyzed text offline."
                    ) { event -> send(event) }
                } else {
                    send(
                        HybridDoubtEvent.Error(
                            message = "Image analysis requires a Gemini API key in Settings (GPT-OSS is text-only)."
                        )
                    )
                }
                return@channelFlow
            }
        }

        // b) Online + Text-only: Groq GPT-OSS primary -> Gemini Flash-Lite fallback -> Local LiteRT fallback
        var groqFailureNote: String? = null
        val groqKey = prefs.groqApiKey
        if (!groqKey.isNullOrBlank()) {
            send(HybridDoubtEvent.Status(DoubtBackend.GROQ_GPT_OSS, "Streaming from Groq GPT-OSS..."))
            try {
                val fullText = streamGroq(apiKey = groqKey, userPrompt = userPrompt) { accumulated ->
                    send(HybridDoubtEvent.Delta(DoubtBackend.GROQ_GPT_OSS, accumulated))
                }
                send(
                    HybridDoubtEvent.Completed(
                        backend = DoubtBackend.GROQ_GPT_OSS,
                        fullText = fullText,
                        solution = parseDoubtSolution(fullText)
                    )
                )
                return@channelFlow
            } catch (ce: CancellationException) {
                throw ce
            } catch (err: Exception) {
                groqFailureNote = (err as? HttpStatusException)?.let { "Groq HTTP ${it.code}" }
                    ?: "Groq timed out or failed"
            }
        }

        val geminiKey = prefs.geminiApiKey
        var geminiFailureNote: String? = null
        if (!geminiKey.isNullOrBlank()) {
            val statusMsg = if (groqFailureNote != null) {
                "$groqFailureNote — falling back to Gemini Flash-Lite..."
            } else {
                "Streaming from Gemini Flash-Lite..."
            }
            send(HybridDoubtEvent.Status(DoubtBackend.GEMINI_FLASH_LITE, statusMsg))
            try {
                val fullText = streamGemini(
                    apiKey = geminiKey,
                    userPrompt = userPrompt,
                    base64Jpeg = null
                ) { accumulated ->
                    send(HybridDoubtEvent.Delta(DoubtBackend.GEMINI_FLASH_LITE, accumulated))
                }
                send(
                    HybridDoubtEvent.Completed(
                        backend = DoubtBackend.GEMINI_FLASH_LITE,
                        fullText = fullText,
                        solution = parseDoubtSolution(fullText),
                        note = groqFailureNote?.let { "Switched from $it" }
                    )
                )
                return@channelFlow
            } catch (ce: CancellationException) {
                throw ce
            } catch (err: Exception) {
                geminiFailureNote = (err as? HttpStatusException)?.let { "Gemini HTTP ${it.code}" }
                    ?: (err.message ?: "Gemini failed")
            }
        }

        if (localEngine.hasModel()) {
            val fallbackReason = listOfNotNull(groqFailureNote, geminiFailureNote).joinToString(" & ")
                .ifBlank { "No cloud API keys configured" }
            send(HybridDoubtEvent.Status(DoubtBackend.OFFLINE_LITERT, "$fallbackReason — running Offline LiteRT..."))
            runLocalFallback(
                userPrompt = userPrompt,
                note = "Fallback used ($fallbackReason)"
            ) { event -> send(event) }
            return@channelFlow
        }

        val missingKeys = groqKey.isNullOrBlank() && geminiKey.isNullOrBlank()
        val finalError = if (missingKeys) {
            "Add a Groq or Gemini API key in Settings, or tap 'Select Offline Model (.litertlm)' to solve doubts locally."
        } else {
            val details = listOfNotNull(groqFailureNote, geminiFailureNote).joinToString(" | ")
            "Cloud backends unavailable ($details). Check your API keys or import an offline .litertlm model."
        }
        send(HybridDoubtEvent.Error(message = finalError, needsOfflineModel = missingKeys))
    }.flowOn(Dispatchers.IO)

    private suspend fun runLocalFallback(
        userPrompt: String,
        note: String?,
        emit: suspend (HybridDoubtEvent) -> Unit
    ) {
        try {
            val sb = StringBuilder()
            localEngine.streamGenerate(SYSTEM_PROMPT_CONCISE, userPrompt).collect { token ->
                sb.append(token)
                emit(HybridDoubtEvent.Delta(DoubtBackend.OFFLINE_LITERT, sb.toString()))
            }
            val fullText = sb.toString().trim()
            if (fullText.isBlank()) {
                emit(HybridDoubtEvent.Error("Offline LiteRT returned an empty response. Try another prompt or model."))
            } else {
                emit(
                    HybridDoubtEvent.Completed(
                        backend = DoubtBackend.OFFLINE_LITERT,
                        fullText = fullText,
                        solution = parseDoubtSolution(fullText),
                        note = note
                    )
                )
            }
        } catch (ce: CancellationException) {
            throw ce
        } catch (t: Throwable) {
            emit(
                HybridDoubtEvent.Error(
                    message = t.message ?: "Offline LiteRT failed to run."
                )
            )
        }
    }

    private suspend fun streamGroq(
        apiKey: String,
        userPrompt: String,
        onAccumulated: suspend (String) -> Unit
    ): String {
        val payload = mapOf(
            "model" to GROQ_MODEL,
            "stream" to true,
            "max_tokens" to 1200,
            "temperature" to 0.5,
            "messages" to listOf(
                mapOf("role" to "system", "content" to SYSTEM_PROMPT_CONCISE),
                mapOf("role" to "user", "content" to userPrompt)
            )
        )
        val request = Request.Builder()
            .url(GROQ_CHAT_URL)
            .header("Authorization", "Bearer ${apiKey.trim()}")
            .header("Accept", "text/event-stream")
            .post(gson.toJson(payload).toRequestBody(jsonMediaType))
            .build()

        val call = groqClient.newCall(request)
        val cancelHandle = currentCoroutineContext()[Job]?.invokeOnCompletion {
            if (it != null) call.cancel()
        }
        return try {
            call.execute().use { response ->
                if (!response.isSuccessful) {
                    val errBody = runCatching { response.body?.string() }.getOrNull()
                    throw HttpStatusException(
                        response.code,
                        "HTTP ${response.code}${errBody?.take(180)?.let { ": $it" } ?: ""}"
                    )
                }
                val source = response.body?.source()
                    ?: throw IOException("Empty response body from Groq")

                val accumulated = StringBuilder()
                while (!source.exhausted()) {
                    currentCoroutineContext().ensureActive()
                    val line = source.readUtf8Line() ?: break
                    if (!line.startsWith("data:")) continue
                    val data = line.removePrefix("data:").trim()
                    if (data == "[DONE]") break
                    if (data.isBlank()) continue

                val deltaText = runCatching {
                    val root = JsonParser.parseString(data).asJsonObject
                    val choices = root.getAsJsonArray("choices") ?: return@runCatching null
                    if (choices.size() == 0) return@runCatching null
                    val delta = choices[0].asJsonObject.getAsJsonObject("delta") ?: return@runCatching null
                    delta.get("content")?.takeIf { !it.isJsonNull }?.asString
                }.getOrNull()

                if (!deltaText.isNullOrEmpty()) {
                    accumulated.append(deltaText)
                    onAccumulated(accumulated.toString())
                }
            }
                val result = accumulated.toString().trim()
                if (result.isBlank()) throw IOException("Groq stream completed with empty output")
                result
            }
        } finally {
            cancelHandle?.dispose()
        }
    }

    private suspend fun streamGemini(
        apiKey: String,
        userPrompt: String,
        base64Jpeg: String?,
        onAccumulated: suspend (String) -> Unit
    ): String {
        val userParts = mutableListOf<Map<String, Any>>()
        userParts.add(mapOf("text" to userPrompt))
        if (!base64Jpeg.isNullOrBlank()) {
            userParts.add(
                mapOf(
                    "inline_data" to mapOf(
                        "mime_type" to "image/jpeg",
                        "data" to base64Jpeg
                    )
                )
            )
        }

        val payload = mapOf(
            "system_instruction" to mapOf(
                "parts" to listOf(mapOf("text" to SYSTEM_PROMPT_CONCISE))
            ),
            "contents" to listOf(
                mapOf(
                    "role" to "user",
                    "parts" to userParts
                )
            ),
            "generationConfig" to mapOf(
                "temperature" to 0.5,
                "maxOutputTokens" to 1200
            )
        )

        val url = "https://generativelanguage.googleapis.com/v1beta/models/$GEMINI_MODEL:streamGenerateContent?alt=sse&key=${apiKey.trim()}"
        val request = Request.Builder()
            .url(url)
            .header("Accept", "text/event-stream")
            .post(gson.toJson(payload).toRequestBody(jsonMediaType))
            .build()

        val call = geminiClient.newCall(request)
        val cancelHandle = currentCoroutineContext()[Job]?.invokeOnCompletion {
            if (it != null) call.cancel()
        }
        return try {
            call.execute().use { response ->
                if (!response.isSuccessful) {
                    val errBody = runCatching { response.body?.string() }.getOrNull()
                    val msg = extractGeminiErrorMessage(errBody) ?: "HTTP ${response.code}"
                    throw HttpStatusException(response.code, msg)
                }
                val source = response.body?.source()
                    ?: throw IOException("Empty response body from Gemini")

                val accumulated = StringBuilder()
                while (!source.exhausted()) {
                    currentCoroutineContext().ensureActive()
                    val line = source.readUtf8Line() ?: break
                    if (!line.startsWith("data:")) continue
                    val data = line.removePrefix("data:").trim()
                    if (data.isBlank() || data == "[DONE]") continue

                val chunkText = runCatching {
                    val root = JsonParser.parseString(data).asJsonObject
                    val candidates = root.getAsJsonArray("candidates") ?: return@runCatching null
                    if (candidates.size() == 0) return@runCatching null
                    val content = candidates[0].asJsonObject.getAsJsonObject("content") ?: return@runCatching null
                    val parts = content.getAsJsonArray("parts") ?: return@runCatching null
                    buildString {
                        for (part in parts) {
                            val t = part.asJsonObject.get("text")?.takeIf { !it.isJsonNull }?.asString
                            if (!t.isNullOrEmpty()) append(t)
                        }
                    }
                }.getOrNull()

                if (!chunkText.isNullOrEmpty()) {
                    accumulated.append(chunkText)
                    onAccumulated(accumulated.toString())
                }
            }
                val result = accumulated.toString().trim()
                if (result.isBlank()) throw IOException("Gemini stream returned empty text")
                result
            }
        } finally {
            cancelHandle?.dispose()
        }
    }

    private fun extractGeminiErrorMessage(raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        return runCatching {
            JsonParser.parseString(raw).asJsonObject
                .getAsJsonObject("error")
                ?.get("message")
                ?.asString
        }.getOrNull()
    }

    private suspend fun compressImageToBase64Jpeg(uri: Uri): String = withContext(Dispatchers.IO) {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        appContext.contentResolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, bounds)
        } ?: throw IOException("Unable to read selected image")

        val maxSide = 1024
        var sampleSize = 1
        while (bounds.outWidth / sampleSize > maxSide * 2 || bounds.outHeight / sampleSize > maxSide * 2) {
            sampleSize *= 2
        }

        val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sampleSize }
        val decoded = appContext.contentResolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, decodeOpts)
        } ?: throw IOException("Unable to decode selected image")

        var scaled: Bitmap? = null
        try {
            val w = decoded.width
            val h = decoded.height
            val targetBitmap = if (w > maxSide || h > maxSide) {
                val ratio = minOf(maxSide.toFloat() / w, maxSide.toFloat() / h)
                val targetW = (w * ratio).toInt().coerceAtLeast(1)
                val targetH = (h * ratio).toInt().coerceAtLeast(1)
                Bitmap.createScaledBitmap(decoded, targetW, targetH, true).also { scaled = it }
            } else {
                decoded
            }

            val out = ByteArrayOutputStream()
            targetBitmap.compress(Bitmap.CompressFormat.JPEG, 85, out)
            Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
        } finally {
            if (scaled != null && scaled !== decoded) {
                scaled?.recycle()
            }
            decoded.recycle()
        }
    }

    private fun buildUserPrompt(
        doubt: String,
        subject: String?,
        energyLevel: EnergyLevel?
    ): String = buildString {
        if (!subject.isNullOrBlank()) append("Subject: ${subject.trim()}\n")
        if (energyLevel != null) append("Energy: $energyLevel\n")
        append("Doubt:\n${doubt.trim()}")
    }

    internal fun parseDoubtSolution(rawText: String): DoubtSolution {
        val cleaned = rawText.trim()
            .removePrefix("```markdown")
            .removePrefix("```text")
            .removePrefix("```")
            .removeSuffix("```")
            .trim()

        val intuition = extractSection(
            cleaned,
            startMarkers = listOf("CORE INTUITION:", "CORE INTUITION", "INTUITION:"),
            endMarkers = listOf("STEP-BY-STEP:", "STEP-BY-STEP", "STEPS:", "2-MINUTE MICRO-STEP:", "2-MINUTE MICRO-STEP", "MICRO-STEP:")
        )
        val stepsBlock = extractSection(
            cleaned,
            startMarkers = listOf("STEP-BY-STEP:", "STEP-BY-STEP", "STEPS:"),
            endMarkers = listOf("2-MINUTE MICRO-STEP:", "2-MINUTE MICRO-STEP", "MICRO-STEP:")
        )
        val microStep = extractSection(
            cleaned,
            startMarkers = listOf("2-MINUTE MICRO-STEP:", "2-MINUTE MICRO-STEP", "MICRO-STEP:"),
            endMarkers = emptyList()
        )

        val stepsList = stepsBlock
            ?.lines()
            ?.map { it.trim() }
            ?.filter { it.isNotBlank() }
            ?.map { it.replace(Regex("^(\\d+[.)]|[-*•])\\s*"), "").trim() }
            ?.filter { it.isNotBlank() }
            ?: emptyList()

        return if (!intuition.isNullOrBlank() || stepsList.isNotEmpty() || !microStep.isNullOrBlank()) {
            DoubtSolution(
                coreIntuition = intuition?.ifBlank { cleaned } ?: cleaned,
                stepByStep = stepsList,
                twoMinuteMicroStep = microStep?.ifBlank {
                    "Spend 2 minutes writing one concrete example of this idea in your notes."
                } ?: "Spend 2 minutes writing one concrete example of this idea in your notes."
            )
        } else {
            val paragraphs = cleaned.split(Regex("\\n\\s*\\n")).map { it.trim() }.filter { it.isNotBlank() }
            DoubtSolution(
                coreIntuition = paragraphs.firstOrNull() ?: cleaned,
                stepByStep = paragraphs.drop(1).take(5),
                twoMinuteMicroStep = "Write down the main idea in one sentence and try one quick example for 2 minutes."
            )
        }
    }

    private fun extractSection(
        text: String,
        startMarkers: List<String>,
        endMarkers: List<String>
    ): String? {
        val normalized = text.replace("**", "").replace("## ", "").replace("### ", "")
        val lower = normalized.lowercase()

        var startIdx = -1
        var matchedMarkerLen = 0
        for (marker in startMarkers) {
            val idx = lower.indexOf(marker.lowercase())
            if (idx != -1 && (startIdx == -1 || idx < startIdx)) {
                startIdx = idx
                matchedMarkerLen = marker.length
            }
        }
        if (startIdx == -1) return null

        val contentStart = startIdx + matchedMarkerLen
        var endIdx = normalized.length
        for (marker in endMarkers) {
            val idx = lower.indexOf(marker.lowercase(), startIndex = contentStart)
            if (idx != -1 && idx < endIdx) {
                endIdx = idx
            }
        }
        return normalized.substring(contentStart, endIdx).trim()
    }

    companion object {
        private const val GROQ_CHAT_URL = "https://api.groq.com/openai/v1/chat/completions"
        private const val GROQ_MODEL = "openai/gpt-oss-120b"
        private const val GEMINI_MODEL = "gemini-3.5-flash-lite"

        private const val SYSTEM_PROMPT_CONCISE =
            "Calm ADHD study tutor. No fluff, no guilt. Reply using ONLY these 3 headers:\n" +
                "CORE INTUITION:\n2-3 plain-language sentences.\n" +
                "STEP-BY-STEP:\n1. short step\n2. short step\n" +
                "2-MINUTE MICRO-STEP:\nOne tiny 2-minute physical practice action."
    }
}
