package com.sarvaai.app.audio.vad

import kotlin.math.sqrt

enum class VadStatus {
    CALIBRATING,
    SILENCE,
    SPEECH_ACTIVE,
    SPEECH_ENDED
}

class EnergyVad(
    private val sampleRate: Int = 16000,
    private val frameDurationMs: Int = 20,
    private var silenceTimeoutMs: Long = 1200L,
    private val maxUtteranceDurationMs: Long = 12000L
) {
    private var noiseFloorRms = 100.0
    private var speechStarted = false
    private var speechStartTimeMs = 0L
    private var lastSpeechFrameTimeMs = 0L
    private var totalFramesProcessed = 0
    private var speechFramesConsecutive = 0

    fun setSilenceTimeout(timeoutMs: Long) {
        this.silenceTimeoutMs = timeoutMs
    }

    fun processFrame(pcmBytes: ByteArray, numBytes: Int): VadStatus {
        val now = System.currentTimeMillis()
        val numSamples = numBytes / 2
        if (numSamples <= 0) return VadStatus.SILENCE

        var sumSquares = 0.0
        for (i in 0 until numSamples) {
            val sample = (pcmBytes[i * 2 + 1].toInt() shl 8) or (pcmBytes[i * 2].toInt() and 0xFF)
            sumSquares += (sample * sample).toDouble()
        }
        val rms = sqrt(sumSquares / numSamples.coerceAtLeast(1))

        // Initial 300ms (15 frames) of silence calibration
        if (totalFramesProcessed < 15) {
            noiseFloorRms = (noiseFloorRms * totalFramesProcessed + rms) / (totalFramesProcessed + 1)
            totalFramesProcessed++
            return VadStatus.CALIBRATING
        }

        val speechThreshold = (noiseFloorRms * 2.5).coerceAtLeast(350.0)

        if (rms > speechThreshold) {
            speechFramesConsecutive++
            if (speechFramesConsecutive >= 2) {
                if (!speechStarted) {
                    speechStarted = true
                    speechStartTimeMs = now
                }
                lastSpeechFrameTimeMs = now
                return VadStatus.SPEECH_ACTIVE
            }
        } else {
            speechFramesConsecutive = 0
        }

        if (speechStarted) {
            val silenceDuration = now - lastSpeechFrameTimeMs
            val totalDuration = now - speechStartTimeMs

            if (silenceDuration > silenceTimeoutMs || totalDuration > maxUtteranceDurationMs) {
                speechStarted = false
                return VadStatus.SPEECH_ENDED
            }
            return VadStatus.SPEECH_ACTIVE
        }

        return VadStatus.SILENCE
    }

    fun reset() {
        speechStarted = false
        speechFramesConsecutive = 0
        totalFramesProcessed = 0
        noiseFloorRms = 100.0
    }
}
