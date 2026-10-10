package com.sarvaai.app.audio.earcons

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlin.math.sin

enum class EarconType {
    LISTEN_START,      // Pleasant chime when mic activates
    LISTEN_STOP,       // Soft closing tone when speech ends
    ORDER_CONFIRMED,   // Success chime (triad chord)
    ERROR_ALERT        // Warning descending tone
}

class EarconEngine(
    private val context: Context,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
) {
    companion object {
        private const val TAG = "EarconEngine"
        private const val SAMPLE_RATE = 44100
    }

    private fun generateSineTone(freqHz: Double, durationMs: Int, volume: Double = 0.3): ShortArray {
        val numSamples = (SAMPLE_RATE * durationMs / 1000.0).toInt()
        val buffer = ShortArray(numSamples)
        for (i in 0 until numSamples) {
            val t = i.toDouble() / SAMPLE_RATE
            // Apply subtle envelope (fade-in and fade-out to prevent audio click)
            val envelope = when {
                i < numSamples * 0.1 -> i / (numSamples * 0.1)
                i > numSamples * 0.8 -> (numSamples - i) / (numSamples * 0.2)
                else -> 1.0
            }
            val sample = sin(2.0 * Math.PI * freqHz * t) * envelope * volume * Short.MAX_VALUE
            buffer[i] = sample.toInt().toShort()
        }
        return buffer
    }

    private fun generateChord(freqs: List<Double>, durationMs: Int, volume: Double = 0.25): ShortArray {
        val numSamples = (SAMPLE_RATE * durationMs / 1000.0).toInt()
        val buffer = ShortArray(numSamples)
        for (i in 0 until numSamples) {
            val t = i.toDouble() / SAMPLE_RATE
            val envelope = when {
                i < numSamples * 0.1 -> i / (numSamples * 0.1)
                i > numSamples * 0.7 -> (numSamples - i) / (numSamples * 0.3)
                else -> 1.0
            }
            var mixedSample = 0.0
            for (freq in freqs) {
                mixedSample += sin(2.0 * Math.PI * freq * t)
            }
            mixedSample = (mixedSample / freqs.size) * envelope * volume * Short.MAX_VALUE
            buffer[i] = mixedSample.toInt().toShort()
        }
        return buffer
    }

    private fun playPcm(samples: ShortArray) {
        try {
            val audioTrack = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(SAMPLE_RATE)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setBufferSizeInBytes(samples.size * 2)
                .setTransferMode(AudioTrack.MODE_STATIC)
                .build()

            audioTrack.write(samples, 0, samples.size)
            audioTrack.play()
            audioTrack.setNotificationMarkerPosition(samples.size)
            audioTrack.setPlaybackPositionUpdateListener(object : AudioTrack.OnPlaybackPositionUpdateListener {
                override fun onPeriodicNotification(track: AudioTrack?) {}
                override fun onMarkerReached(track: AudioTrack?) {
                    try {
                        track?.stop()
                        track?.release()
                    } catch (_: Throwable) {}
                }
            })
        } catch (t: Throwable) {
            Log.w(TAG, "Failed to play acoustic earcon", t)
        }
    }

    fun play(type: EarconType) {
        scope.launch {
            try {
                val samples = when (type) {
                    EarconType.LISTEN_START -> {
                        // High melodic chime (880Hz A5)
                        generateSineTone(880.0, 110, 0.35)
                    }
                    EarconType.LISTEN_STOP -> {
                        // Soft lowering tone (587Hz -> D5)
                        generateSineTone(587.33, 90, 0.25)
                    }
                    EarconType.ORDER_CONFIRMED -> {
                        // C Major triad (C5=523Hz, E5=659Hz, G5=784Hz)
                        generateChord(listOf(523.25, 659.25, 783.99), 350, 0.3)
                    }
                    EarconType.ERROR_ALERT -> {
                        // Low dissonant tone (261Hz + 277Hz beat)
                        generateChord(listOf(261.63, 277.18), 220, 0.3)
                    }
                }
                playPcm(samples)
            } catch (t: Throwable) {
                Log.w(TAG, "Earcon error", t)
            }
        }
    }
}
