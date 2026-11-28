package com.adhdcompanion.focus.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import com.adhdcompanion.focus.model.SoundscapeType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

/**
 * Pure Kotlin procedural AudioTrack soundscape synthesizer.
 * Works 100% offline with zero bundled audio files:
 *  - Brown Noise: integrated white noise with leaky DC blocker for warm deep focus masking
 *  - Soft Rain: multi-band filtered noise + subtle randomized raindrop envelope modulation
 *  - 40Hz Focus: warm low-pass pink/brown bed layered with gentle 40Hz binaural/AM gamma pulse
 */
class FocusSoundscapeEngine {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _selectedType = MutableStateFlow(SoundscapeType.NONE)
    val selectedType: StateFlow<SoundscapeType> = _selectedType.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _volume = MutableStateFlow(0.55f)
    val volume: StateFlow<Float> = _volume.asStateFlow()

    private var playbackJob: Job? = null

    fun selectAndToggle(type: SoundscapeType) {
        if (type == SoundscapeType.NONE) {
            stop()
            _selectedType.value = SoundscapeType.NONE
            return
        }
        if (_selectedType.value == type && _isPlaying.value) {
            stop()
        } else {
            play(type)
        }
    }

    fun play(type: SoundscapeType) {
        if (type == SoundscapeType.NONE) {
            stop()
            _selectedType.value = SoundscapeType.NONE
            return
        }
        playbackJob?.cancel()
        _selectedType.value = type
        _isPlaying.value = true

        playbackJob = scope.launch {
            val sampleRate = 22050
            val chunkSamples = 2048
            val minBuf = AudioTrack.getMinBufferSize(
                sampleRate,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            ).coerceAtLeast(chunkSamples * 2)

            val track = runCatching {
                AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                            .build()
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(sampleRate)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build()
                    )
                    .setBufferSizeInBytes(minBuf * 2)
                    .setTransferMode(AudioTrack.MODE_STREAM)
                    .build()
            }.getOrNull()

            if (track == null) {
                _isPlaying.value = false
                return@launch
            }

            val buffer = ShortArray(chunkSamples)
            val rng = Random(System.nanoTime())

            // DSP filter states
            var brownState = 0.0
            var rainLow = 0.0
            var rainPrev = 0.0
            var dropEnv = 0.0
            var phase40Hz = 0.0
            var phaseCarrier = 0.0

            try {
                track.play()
                while (isActive && _isPlaying.value) {
                    val masterVol = _volume.value.coerceIn(0f, 1f)
                    for (i in 0 until chunkSamples) {
                        val white = rng.nextDouble(-1.0, 1.0)
                        val sampleDouble = when (type) {
                            SoundscapeType.BROWN_NOISE -> {
                                brownState = (brownState * 0.985) + (white * 0.065)
                                brownState = brownState.coerceIn(-0.95, 0.95)
                                brownState * 0.72
                            }
                            SoundscapeType.SOFT_RAIN -> {
                                // Smooth pink-ish wash + occasional soft droplet pings
                                rainLow = (rainLow * 0.94) + (white * 0.08)
                                val highSplash = (white - rainPrev) * 0.10
                                rainPrev = white
                                if (rng.nextInt(900) == 0) {
                                    dropEnv = 0.28
                                } else {
                                    dropEnv *= 0.992
                                }
                                ((rainLow * 0.85) + highSplash + (white * dropEnv * 0.35))
                                    .coerceIn(-0.95, 0.95)
                            }
                            SoundscapeType.TONE_40HZ -> {
                                // Warm 180Hz sine carrier softly amplitude-modulated at 40Hz over quiet brown bed
                                brownState = (brownState * 0.985) + (white * 0.045)
                                brownState = brownState.coerceIn(-0.8, 0.8)
                                phaseCarrier += (2.0 * PI * 180.0) / sampleRate
                                phase40Hz += (2.0 * PI * 40.0) / sampleRate
                                if (phaseCarrier > 2.0 * PI) phaseCarrier -= 2.0 * PI
                                if (phase40Hz > 2.0 * PI) phase40Hz -= 2.0 * PI
                                val mod = 0.55 + 0.45 * sin(phase40Hz)
                                val tone = sin(phaseCarrier) * mod * 0.22
                                (brownState * 0.45 + tone).coerceIn(-0.95, 0.95)
                            }
                            SoundscapeType.NONE -> 0.0
                        }
                        val scaled = (sampleDouble * masterVol * Short.MAX_VALUE).toInt()
                            .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
                        buffer[i] = scaled.toShort()
                    }
                    track.write(buffer, 0, chunkSamples)
                }
            } catch (_: Throwable) {
                // Swallow clean cancellation / audio focus interruptions
            } finally {
                runCatching {
                    track.pause()
                    track.flush()
                    track.release()
                }
            }
        }
    }

    fun setVolume(value: Float) {
        _volume.value = value.coerceIn(0f, 1f)
    }

    fun stop() {
        _isPlaying.value = false
        playbackJob?.cancel()
        playbackJob = null
    }

    fun release() {
        stop()
    }
}
