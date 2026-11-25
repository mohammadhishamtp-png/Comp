package com.adhdcompanion.focus.alarm

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Handler
import android.os.Looper
import android.util.Log
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

data class AlarmTriggerEvent(
    val label: String,
    val autoSpeak: Boolean,
    val triggeredAtMillis: Long = System.currentTimeMillis()
)

object AlarmEventBus {
    private val _events = MutableSharedFlow<AlarmTriggerEvent>(replay = 0, extraBufferCapacity = 4)
    val events: SharedFlow<AlarmTriggerEvent> = _events.asSharedFlow()

    private val _isRinging = MutableStateFlow(false)
    val isRinging: StateFlow<Boolean> = _isRinging.asStateFlow()

    fun emit(event: AlarmTriggerEvent) {
        _events.tryEmit(event)
    }

    fun setRinging(ringing: Boolean) {
        _isRinging.value = ringing
    }
}

/**
 * Plays the default system alarm sound on AudioAttributes.USAGE_ALARM
 * (falling back to TYPE_NOTIFICATION if no alarm ringtone is configured),
 * and automatically stops after 15 seconds or when dismissed/opened.
 */
object AlarmSoundPlayer {
    private var mediaPlayer: MediaPlayer? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private val autoStopRunnable = Runnable { stop() }

    @Synchronized
    fun playAlarmChime(context: Context, maxDurationMs: Long = 15_000L) {
        stop()

        val uris = listOfNotNull(
            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM),
            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION),
            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
        )

        val appContext = context.applicationContext
        val alarmAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ALARM)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

        for (uri in uris) {
            try {
                val player = MediaPlayer().apply {
                    setAudioAttributes(alarmAttributes)
                    setDataSource(appContext, uri)
                    isLooping = true
                    prepare()
                    start()
                }
                mediaPlayer = player
                AlarmEventBus.setRinging(true)
                mainHandler.postDelayed(autoStopRunnable, maxDurationMs)
                return
            } catch (e: Exception) {
                Log.w("AlarmSoundPlayer", "Failed to play alarm URI $uri, trying fallback", e)
            }
        }
        AlarmEventBus.setRinging(false)
    }

    @Synchronized
    fun stop() {
        mainHandler.removeCallbacks(autoStopRunnable)
        mediaPlayer?.let { player ->
            runCatching {
                if (player.isPlaying) player.stop()
            }
            runCatching { player.release() }
        }
        mediaPlayer = null
        AlarmEventBus.setRinging(false)
    }
}
