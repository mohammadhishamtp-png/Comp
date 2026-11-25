package com.adhdcompanion.focus.tts

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.speech.tts.TextToSpeech
import android.util.Base64
import android.util.Log
import com.adhdcompanion.focus.data.prefs.SecurePrefsManager
import com.adhdcompanion.focus.data.remote.GeminiRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.util.Locale

/**
 * Speaks text out loud. Tries expressive Gemini TTS first when online and a
 * key is set; if that fails for ANY reason (offline, quota, bad key, model
 * not yet available) it falls back to the built-in Android TextToSpeech
 * engine automatically and silently, so the briefing always speaks.
 */
class SpeechManager(
    private val context: Context,
    private val geminiRepository: GeminiRepository,
    private val prefs: SecurePrefsManager
) {
    private var androidTts: TextToSpeech? = null
    private var androidTtsReady = false
    private var mediaPlayer: MediaPlayer? = null
    private val scope = CoroutineScope(Dispatchers.Main)

    init {
        androidTts = TextToSpeech(context.applicationContext) { status ->
            androidTtsReady = status == TextToSpeech.SUCCESS
            if (androidTtsReady) {
                androidTts?.language = Locale.getDefault()
                androidTts?.setSpeechRate(0.98f)
            }
        }
    }

    fun speak(text: String, onDone: (() -> Unit)? = null) {
        if (text.isBlank()) {
            onDone?.invoke()
            return
        }
        val wantsGemini = prefs.voicePreference == SecurePrefsManager.VOICE_GEMINI_WITH_FALLBACK &&
            prefs.hasApiKey()

        if (!wantsGemini) {
            speakWithAndroidTts(text, onDone)
            return
        }

        scope.launch {
            val audio = runCatching { geminiRepository.generateSpeechAudio(text) }.getOrNull()
            if (audio?.data != null) {
                val played = playBase64Audio(audio.data, audio.mimeType ?: "audio/mpeg", onDone)
                if (!played) speakWithAndroidTts(text, onDone)
            } else {
                speakWithAndroidTts(text, onDone)
            }
        }
    }

    private fun speakWithAndroidTts(text: String, onDone: (() -> Unit)?) {
        val tts = androidTts
        if (tts == null || !androidTtsReady) {
            onDone?.invoke()
            return
        }
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "briefing_utterance")
        // Android's TTS listener callback requires API 21+ UtteranceProgressListener;
        // for simplicity and reliability we just invoke onDone after a short delay
        // proportional to text length so the UI doesn't hang indefinitely.
        if (onDone != null) {
            val estimatedMs = (text.length * 55L).coerceIn(800L, 15000L)
            android.os.Handler(context.mainLooper).postDelayed({ onDone() }, estimatedMs)
        }
    }

    private fun playBase64Audio(base64Data: String, mimeType: String, onDone: (() -> Unit)?): Boolean {
        return try {
            val bytes = Base64.decode(base64Data, Base64.DEFAULT)
            val extension = if (mimeType.contains("wav")) "wav" else "mp3"
            val tempFile = File.createTempFile("gemini_tts_", ".$extension", context.cacheDir)
            FileOutputStream(tempFile).use { it.write(bytes) }

            mediaPlayer?.release()
            mediaPlayer = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ASSISTANT)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                setDataSource(tempFile.absolutePath)
                setOnCompletionListener {
                    tempFile.delete()
                    onDone?.invoke()
                }
                setOnErrorListener { _, _, _ -> false }
                prepare()
                start()
            }
            true
        } catch (e: Exception) {
            Log.w("SpeechManager", "Gemini audio playback failed, falling back", e)
            false
        }
    }

    fun stop() {
        androidTts?.stop()
        mediaPlayer?.let {
            runCatching { it.stop() }
            it.release()
        }
        mediaPlayer = null
    }

    fun release() {
        stop()
        androidTts?.shutdown()
        androidTts = null
    }
}
