package com.adhdcompanion.focus.alarm

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Base64
import android.util.Log
import com.adhdcompanion.focus.data.prefs.SecurePrefsManager
import com.adhdcompanion.focus.data.remote.GeminiRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Locale

/**
 * Online-first Gemini voice engine for due task announcements.
 * Plays generated Gemini speech on AudioAttributes.USAGE_ALARM so it rides the
 * alarm stream alongside ducked chime audio, and returns false immediately when
 * offline, unconfigured, timed out, or errored so AlarmService falls back to
 * local Android TextToSpeech without delay.
 */
class GeminiVoiceReminderEngine(
    context: Context,
    private val prefs: SecurePrefsManager = SecurePrefsManager(context),
    private val geminiRepository: GeminiRepository = GeminiRepository(prefs)
) {
    private val appContext = context.applicationContext
    private var mediaPlayer: MediaPlayer? = null
    private var activeTempFile: File? = null

    fun isOnline(): Boolean {
        val cm = appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return false
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    suspend fun speakTaskReminder(
        spokenText: String,
        onFinished: () -> Unit,
        onPlaybackError: () -> Unit
    ): Boolean {
        if (spokenText.isBlank()) return false
        if (prefs.voicePreference == SecurePrefsManager.VOICE_ANDROID_ONLY) return false
        if (!prefs.hasApiKey() || !isOnline()) return false

        val audio = withTimeoutOrNull(GEMINI_TTS_TIMEOUT_MS) {
            runCatching { geminiRepository.generateSpeechAudio(spokenText) }.getOrNull()
        }
        val base64Data = audio?.data?.takeIf { it.isNotBlank() } ?: return false
        val mimeType = audio.mimeType ?: "audio/mpeg"

        return withContext(Dispatchers.Main) {
            playEncodedAudio(
                base64Data = base64Data,
                mimeType = mimeType,
                onFinished = onFinished,
                onPlaybackError = onPlaybackError
            )
        }
    }

    @Synchronized
    private fun playEncodedAudio(
        base64Data: String,
        mimeType: String,
        onFinished: () -> Unit,
        onPlaybackError: () -> Unit
    ): Boolean {
        stop()

        var tempFile: File? = null
        return try {
            val decoded = Base64.decode(base64Data, Base64.DEFAULT)
            val lowerMime = mimeType.lowercase(Locale.US)
            val isRawPcm = lowerMime.contains("l16") || lowerMime.contains("pcm")
            val bytes: ByteArray
            val extension: String
            if (isRawPcm) {
                val sampleRate = parseSampleRate(lowerMime)
                bytes = pcm16MonoToWav(decoded, sampleRate)
                extension = "wav"
            } else {
                bytes = decoded
                extension = if (lowerMime.contains("wav") || lowerMime.contains("wave")) "wav" else "mp3"
            }

            tempFile = File.createTempFile("task_gemini_tts_", ".$extension", appContext.cacheDir)
            FileOutputStream(tempFile).use { it.write(bytes) }
            activeTempFile = tempFile

            val alarmSpeechAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()

            val player = MediaPlayer().apply {
                setAudioAttributes(alarmSpeechAttributes)
                setDataSource(tempFile.absolutePath)
                setOnCompletionListener {
                    cleanupTempFile()
                    onFinished()
                }
                setOnErrorListener { _, _, _ ->
                    stop()
                    onPlaybackError()
                    true
                }
                prepare()
                start()
            }
            mediaPlayer = player
            true
        } catch (e: Exception) {
            Log.w(TAG, "Gemini voice playback failed, falling back to Android TTS", e)
            tempFile?.let { runCatching { it.delete() } }
            stop()
            false
        }
    }

    @Synchronized
    fun stop() {
        mediaPlayer?.let { player ->
            runCatching {
                if (player.isPlaying) player.stop()
            }
            runCatching { player.release() }
        }
        mediaPlayer = null
        cleanupTempFile()
    }

    private fun cleanupTempFile() {
        activeTempFile?.let { file ->
            runCatching { if (file.exists()) file.delete() }
        }
        activeTempFile = null
    }

    private fun parseSampleRate(mimeType: String): Int {
        val match = Regex("rate=(\\d+)").find(mimeType)
        return match?.groupValues?.get(1)?.toIntOrNull() ?: 24000
    }

    private fun pcm16MonoToWav(pcm: ByteArray, sampleRate: Int): ByteArray {
        val channels = 1
        val bitsPerSample = 16
        val byteRate = sampleRate * channels * bitsPerSample / 8
        val blockAlign = channels * bitsPerSample / 8
        val dataLen = pcm.size
        val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN).apply {
            put("RIFF".toByteArray(Charsets.US_ASCII))
            putInt(36 + dataLen)
            put("WAVE".toByteArray(Charsets.US_ASCII))
            put("fmt ".toByteArray(Charsets.US_ASCII))
            putInt(16)
            putShort(1)
            putShort(channels.toShort())
            putInt(sampleRate)
            putInt(byteRate)
            putShort(blockAlign.toShort())
            putShort(bitsPerSample.toShort())
            put("data".toByteArray(Charsets.US_ASCII))
            putInt(dataLen)
        }.array()
        return header + pcm
    }

    companion object {
        private const val TAG = "GeminiVoiceReminder"
        private const val GEMINI_TTS_TIMEOUT_MS = 8_000L
    }
}
