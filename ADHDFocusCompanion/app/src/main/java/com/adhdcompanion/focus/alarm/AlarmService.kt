package com.adhdcompanion.focus.alarm

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.adhdcompanion.focus.data.local.AppDatabase
import com.adhdcompanion.focus.data.local.toDomain
import com.adhdcompanion.focus.model.DayBlock
import com.adhdcompanion.focus.notification.NotificationHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale

data class TtsAvailabilityStatus(
    val hasEngine: Boolean,
    val hasVoiceData: Boolean,
    val message: String?
)

/**
 * Foreground media-playback service that holds a partial WakeLock, plays the
 * default system alarm sound on USAGE_ALARM at ducked background volume, waits
 * for TextToSpeech initialization, reads ALL of today's Room blocks aloud in
 * max-length-safe chunks via QUEUE_ADD, and runs continuously until dismissed.
 */
class AlarmService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val mainHandler = Handler(Looper.getMainLooper())

    private var wakeLock: PowerManager.WakeLock? = null
    private var mediaPlayer: MediaPlayer? = null
    private var tts: TextToSpeech? = null
    private var isTtsReady = false
    private var isDismissed = false

    private var currentLabel: String = "Morning focus briefing"
    private var shouldAutoSpeak: Boolean = true
    private var pendingScheduleChunks: List<String> = emptyList()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: ACTION_START_ALARM
        if (action == ACTION_DISMISS_ALARM) {
            dismissAndStop()
            return START_NOT_STICKY
        }

        isDismissed = false
        currentLabel = intent?.getStringExtra(EXTRA_LABEL)?.takeIf { it.isNotBlank() }
            ?: "Morning focus briefing"
        shouldAutoSpeak = intent?.getBooleanExtra(EXTRA_AUTO_SPEAK, true) ?: true

        // 1. Immediately enter foreground with mediaPlayback type so Android/HyperOS keeps audio alive
        val notification = NotificationHelper.buildAlarmNotification(
            context = this,
            title = "Focus Companion Alarm",
            body = currentLabel,
            autoSpeak = shouldAutoSpeak
        )
        ServiceCompat.startForeground(
            this,
            NotificationHelper.ALARM_NOTIFICATION_ID,
            notification,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
            } else {
                0
            }
        )

        // 2. Hold partial WakeLock until dismissed
        acquireWakeLock()

        // 3. Notify UI that alarm is actively ringing
        AlarmEventBus.setRinging(true)

        // 4. Start looping default system alarm audio at ducked volume on USAGE_ALARM
        startLoopingAlarmTone()

        // 5. Fetch full schedule from Room and speak via TextToSpeech on USAGE_ALARM
        if (shouldAutoSpeak) {
            prepareAndSpeakFullSchedule()
        }

        return START_NOT_STICKY
    }

    private fun acquireWakeLock() {
        val current = wakeLock
        if (current != null && current.isHeld) return
        val pm = getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return
        wakeLock = pm.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "ADHDFocusCompanion:AlarmServiceWakeLock"
        ).apply {
            setReferenceCounted(false)
            // 30-minute safety cap; explicitly released on dismiss/destroy
            acquire(30 * 60_000L)
        }
    }

    private fun releaseWakeLock() {
        wakeLock?.let { wl ->
            if (wl.isHeld) {
                runCatching { wl.release() }
            }
        }
        wakeLock = null
    }

    private fun startLoopingAlarmTone() {
        stopMediaPlayer()

        val uris = listOfNotNull(
            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM),
            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION),
            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
        )
        val alarmAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ALARM)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

        val bgVolume = if (shouldAutoSpeak) DUCKED_ALARM_VOLUME else 0.85f
        for (uri in uris) {
            try {
                val player = MediaPlayer().apply {
                    setAudioAttributes(alarmAttributes)
                    setDataSource(applicationContext, uri)
                    isLooping = true
                    setVolume(bgVolume, bgVolume)
                    prepare()
                    start()
                }
                mediaPlayer = player
                return
            } catch (e: Exception) {
                Log.w(TAG, "Failed to start MediaPlayer for URI $uri", e)
            }
        }
    }

    private fun stopMediaPlayer() {
        mediaPlayer?.let { player ->
            runCatching {
                if (player.isPlaying) player.stop()
            }
            runCatching { player.release() }
        }
        mediaPlayer = null
    }

    private fun prepareAndSpeakFullSchedule() {
        ensureTtsInitialized()
        serviceScope.launch {
            val blocks = withContext(Dispatchers.IO) {
                val todayEpoch = LocalDate.now(ZoneId.systemDefault()).toEpochDay()
                AppDatabase.getInstance(applicationContext)
                    .blockDao()
                    .getBlocksForDay(todayEpoch)
                    .map { it.toDomain() }
                    .sortedBy { it.startMinuteOfDay }
            }
            if (isDismissed) return@launch

            val fullScript = formatFullScheduleForSpeech(currentLabel, blocks)
            val maxLen = runCatching { TextToSpeech.getMaxSpeechInputLength() }
                .getOrDefault(3500)
                .coerceAtLeast(300)
            val chunks = splitIntoSpeechChunks(fullScript, (maxLen - 40).coerceAtLeast(250))

            pendingScheduleChunks = chunks
            if (isTtsReady) {
                queueChunksOnTts(chunks)
            }
        }
    }

    private fun ensureTtsInitialized() {
        if (tts != null) return

        val speechAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ALARM)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            .build()

        tts = TextToSpeech(applicationContext) { status ->
            mainHandler.post {
                if (isDismissed) return@post
                if (status == TextToSpeech.SUCCESS) {
                    val engine = tts ?: return@post
                    engine.setAudioAttributes(speechAttributes)
                    val locale = Locale.getDefault()
                    val langResult = engine.setLanguage(locale)
                    if (langResult == TextToSpeech.LANG_MISSING_DATA ||
                        langResult == TextToSpeech.LANG_NOT_SUPPORTED
                    ) {
                        Log.w(TAG, "TTS language missing/unsupported for $locale; trying US English fallback")
                        engine.setLanguage(Locale.US)
                    }
                    engine.setSpeechRate(0.96f)
                    isTtsReady = true

                    val chunks = pendingScheduleChunks
                    if (chunks.isNotEmpty()) {
                        queueChunksOnTts(chunks)
                    }
                } else {
                    isTtsReady = false
                    Log.e(TAG, "TextToSpeech failed to initialize (status=$status); keeping alarm tone active")
                    // Restore normal volume on alarm tone if TTS unavailable
                    runCatching { mediaPlayer?.setVolume(0.85f, 0.85f) }
                }
            }
        }
    }

    private fun queueChunksOnTts(chunks: List<String>) {
        val engine = tts ?: return
        if (!isTtsReady || isDismissed || chunks.isEmpty()) return

        val lastUtteranceId = "schedule_chunk_${chunks.lastIndex}"
        engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                runCatching { mediaPlayer?.setVolume(DUCKED_ALARM_VOLUME, DUCKED_ALARM_VOLUME) }
            }

            override fun onDone(utteranceId: String?) {
                if (utteranceId == lastUtteranceId && !isDismissed) {
                    // Schedule finished speaking; keep soft alarm tone looping until dismissed
                    runCatching { mediaPlayer?.setVolume(0.42f, 0.42f) }
                }
            }

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                Log.w(TAG, "TTS error on utterance $utteranceId")
            }

            override fun onError(utteranceId: String?, errorCode: Int) {
                Log.w(TAG, "TTS error $errorCode on utterance $utteranceId")
            }
        })

        // Flush any old speech once, then queue all schedule chunks with QUEUE_ADD
        engine.stop()
        val params = Bundle()
        chunks.forEachIndexed { index, chunk ->
            engine.speak(chunk, TextToSpeech.QUEUE_ADD, params, "schedule_chunk_$index")
        }
    }

    private fun dismissAndStop() {
        isDismissed = true
        pendingScheduleChunks = emptyList()
        stopMediaPlayer()
        tts?.let { engine ->
            runCatching { engine.stop() }
            runCatching { engine.shutdown() }
        }
        tts = null
        isTtsReady = false
        AlarmEventBus.setRinging(false)
        NotificationHelper.cancelAlarmNotification(this)
        releaseWakeLock()
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        isDismissed = true
        stopMediaPlayer()
        tts?.let { engine ->
            runCatching { engine.stop() }
            runCatching { engine.shutdown() }
        }
        tts = null
        isTtsReady = false
        AlarmEventBus.setRinging(false)
        releaseWakeLock()
        serviceScope.cancel()
        mainHandler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    companion object {
        private const val TAG = "AlarmService"
        private const val DUCKED_ALARM_VOLUME = 0.16f

        const val ACTION_START_ALARM = "com.adhdcompanion.focus.alarm.ACTION_START_ALARM"
        const val ACTION_DISMISS_ALARM = "com.adhdcompanion.focus.alarm.ACTION_DISMISS_ALARM"
        const val EXTRA_LABEL = "extra_label"
        const val EXTRA_AUTO_SPEAK = "extra_auto_speak"

        fun startAlarm(context: Context, label: String, autoSpeak: Boolean) {
            val intent = Intent(context, AlarmService::class.java).apply {
                action = ACTION_START_ALARM
                putExtra(EXTRA_LABEL, label)
                putExtra(EXTRA_AUTO_SPEAK, autoSpeak)
            }
            ContextCompat.startForegroundService(context.applicationContext, intent)
        }

        fun dismissAlarm(context: Context) {
            val appCtx = context.applicationContext
            AlarmEventBus.setRinging(false)
            NotificationHelper.cancelAlarmNotification(appCtx)
            AlarmSoundPlayer.stopLocalOnly()
            val stopIntent = Intent(appCtx, AlarmService::class.java)
            runCatching { appCtx.stopService(stopIntent) }
        }

        internal fun formatFullScheduleForSpeech(label: String, blocks: List<DayBlock>): String {
            if (blocks.isEmpty()) {
                return "$label You have no scheduled focus blocks saved for today yet. " +
                    "Open the Planner tab whenever you're ready to lay out a gentle day."
            }

            return buildString {
                append(label.trim().trimEnd('.'))
                append(". Here is your full schedule for today, with ${blocks.size} ")
                append(if (blocks.size == 1) "block. " else "blocks. ")

                blocks.forEachIndexed { index, block ->
                    val startText = formatSpokenTime(block.startMinuteOfDay)
                    val statusNote = if (block.isCompleted) " Already completed. " else " "
                    append("Block ${index + 1}, at $startText for ${block.durationMinutes} minutes: ${block.title}.$statusNote")
                    if (block.why.isNotBlank()) {
                        append(block.why.trim().trimEnd('.'))
                        append(". ")
                    }
                    if (block.microStep.isNotBlank() && !block.isCompleted) {
                        append("Tiny first step: ")
                        append(block.microStep.trim().trimEnd('.'))
                        append(". ")
                    }
                }
                append("Take one calm breath and start with just the first tiny step. Tap dismiss whenever you are ready.")
            }
        }

        internal fun splitIntoSpeechChunks(text: String, maxChunkLength: Int): List<String> {
            val trimmed = text.trim()
            if (trimmed.isEmpty()) return emptyList()
            if (trimmed.length <= maxChunkLength) return listOf(trimmed)

            val chunks = mutableListOf<String>()
            var start = 0
            while (start < trimmed.length) {
                val hardEnd = (start + maxChunkLength).coerceAtMost(trimmed.length)
                if (hardEnd == trimmed.length) {
                    chunks.add(trimmed.substring(start).trim())
                    break
                }

                // Prefer breaking at sentence boundary, then space
                val sentenceBreak = maxOf(
                    trimmed.lastIndexOf(". ", hardEnd - 1),
                    trimmed.lastIndexOf("! ", hardEnd - 1),
                    trimmed.lastIndexOf("? ", hardEnd - 1)
                )
                val splitAt = when {
                    sentenceBreak > start + (maxChunkLength / 3) -> sentenceBreak + 1
                    else -> {
                        val spaceBreak = trimmed.lastIndexOf(' ', hardEnd - 1)
                        if (spaceBreak > start) spaceBreak else hardEnd
                    }
                }
                val part = trimmed.substring(start, splitAt).trim()
                if (part.isNotEmpty()) chunks.add(part)
                start = splitAt
                while (start < trimmed.length && trimmed[start] == ' ') start++
            }
            return chunks
        }

        private fun formatSpokenTime(minuteOfDay: Int): String {
            val safe = minuteOfDay.coerceIn(0, 23 * 60 + 59)
            val h24 = safe / 60
            val min = safe % 60
            val amPm = if (h24 < 12) "AM" else "PM"
            val h12 = when (val h = h24 % 12) {
                0 -> 12
                else -> h
            }
            return if (min == 0) {
                "$h12 $amPm"
            } else {
                String.format(Locale.US, "%d:%02d %s", h12, min, amPm)
            }
        }

        fun checkTtsAvailability(
            context: Context,
            onResult: (TtsAvailabilityStatus) -> Unit
        ) {
            val main = Handler(Looper.getMainLooper())
            var probeTts: TextToSpeech? = null
            try {
                probeTts = TextToSpeech(context.applicationContext) { status ->
                    main.post {
                        val engine = probeTts
                        if (status != TextToSpeech.SUCCESS || engine == null) {
                            onResult(
                                TtsAvailabilityStatus(
                                    hasEngine = false,
                                    hasVoiceData = false,
                                    message = "No active Text-to-Speech engine found on this device. Install or enable Google Speech Services in Android TTS settings so alarms can speak your schedule."
                                )
                            )
                            runCatching { engine?.shutdown() }
                            return@post
                        }

                        val hasEngines = runCatching { engine.engines.isNotEmpty() }.getOrDefault(true)
                        val langCheck = runCatching { engine.isLanguageAvailable(Locale.getDefault()) }
                            .getOrDefault(TextToSpeech.LANG_AVAILABLE)
                        val fallbackCheck = runCatching { engine.isLanguageAvailable(Locale.US) }
                            .getOrDefault(TextToSpeech.LANG_AVAILABLE)

                        val voiceOk = langCheck >= TextToSpeech.LANG_AVAILABLE ||
                            fallbackCheck >= TextToSpeech.LANG_AVAILABLE

                        val msg = when {
                            !hasEngines -> "No Text-to-Speech engine is installed. Spoken schedule alarms require a TTS engine."
                            !voiceOk -> "No offline TTS voice data is installed for your language. Download voice data in Android TTS settings."
                            else -> null
                        }

                        onResult(
                            TtsAvailabilityStatus(
                                hasEngine = hasEngines,
                                hasVoiceData = voiceOk,
                                message = msg
                            )
                        )
                        runCatching { engine.shutdown() }
                    }
                }
            } catch (t: Throwable) {
                main.post {
                    onResult(
                        TtsAvailabilityStatus(
                            hasEngine = false,
                            hasVoiceData = false,
                            message = "Unable to bind Text-to-Speech service (${t.message ?: "unavailable"})."
                        )
                    )
                }
            }
        }
    }
}
