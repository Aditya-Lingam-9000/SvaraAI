package com.sarvaai.app

import com.sarvaai.app.audio.vad.EnergyVad
import com.sarvaai.app.audio.vad.VadStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class EnergyVadTest {

    private lateinit var vad: EnergyVad

    @Before
    fun setUp() {
        vad = EnergyVad(sampleRate = 16000, frameDurationMs = 20)
    }

    private fun createPcmFrame(amplitude: Short, numSamples: Int = 320): ByteArray {
        val bytes = ByteArray(numSamples * 2)
        for (i in 0 until numSamples) {
            bytes[i * 2] = (amplitude.toInt() and 0xFF).toByte()
            bytes[i * 2 + 1] = ((amplitude.toInt() shr 8) and 0xFF).toByte()
        }
        return bytes
    }

    @Test
    fun testInitialCalibrationFrames() {
        val silentFrame = createPcmFrame(0)
        for (i in 0 until 10) {
            val status = vad.processFrame(silentFrame, silentFrame.size)
            assertEquals("Frame $i must be CALIBRATING", VadStatus.CALIBRATING, status)
        }

        // Frame 11 onwards should be SILENCE for quiet audio
        val status = vad.processFrame(silentFrame, silentFrame.size)
        assertEquals("Frame 11 should be SILENCE", VadStatus.SILENCE, status)
    }

    @Test
    fun testRequiresThreeConsecutiveFramesForSpeechTrigger() {
        val silentFrame = createPcmFrame(50) // Ambient low noise
        for (i in 0 until 10) {
            vad.processFrame(silentFrame, silentFrame.size)
        }

        val loudSpeechFrame = createPcmFrame(2500) // Loud speech above 2000 threshold

        // Frame 1 of speech: should remain SILENCE (isolated spike rejection)
        val status1 = vad.processFrame(loudSpeechFrame, loudSpeechFrame.size)
        assertEquals("1st speech frame should not trigger speech yet", VadStatus.SILENCE, status1)

        // Frame 2 of speech: should still remain SILENCE
        val status2 = vad.processFrame(loudSpeechFrame, loudSpeechFrame.size)
        assertEquals("2nd speech frame should not trigger speech yet", VadStatus.SILENCE, status2)

        // Frame 3 of speech: 3 consecutive frames => triggers SPEECH_ACTIVE
        val status3 = vad.processFrame(loudSpeechFrame, loudSpeechFrame.size)
        assertEquals("3rd consecutive speech frame must trigger SPEECH_ACTIVE", VadStatus.SPEECH_ACTIVE, status3)

        // Frame 4 of speech: continues SPEECH_ACTIVE
        val status4 = vad.processFrame(loudSpeechFrame, loudSpeechFrame.size)
        assertEquals("4th consecutive speech frame continues SPEECH_ACTIVE", VadStatus.SPEECH_ACTIVE, status4)
    }

    @Test
    fun testSpikeInterruptionResetsConsecutiveFrameCount() {
        val silentFrame = createPcmFrame(50)
        for (i in 0 until 10) {
            vad.processFrame(silentFrame, silentFrame.size)
        }

        val loudFrame = createPcmFrame(2500)

        // Frame 1 loud
        assertEquals(VadStatus.SILENCE, vad.processFrame(loudFrame, loudFrame.size))
        // Frame 2 loud
        assertEquals(VadStatus.SILENCE, vad.processFrame(loudFrame, loudFrame.size))
        // Frame 3 silent (spike ends early)
        assertEquals(VadStatus.SILENCE, vad.processFrame(silentFrame, silentFrame.size))

        // Next frame loud is Frame 1 again
        assertEquals(VadStatus.SILENCE, vad.processFrame(loudFrame, loudFrame.size))
        // Frame 2 loud
        assertEquals(VadStatus.SILENCE, vad.processFrame(loudFrame, loudFrame.size))
        // Frame 3 loud => now triggers SPEECH_ACTIVE
        assertEquals(VadStatus.SPEECH_ACTIVE, vad.processFrame(loudFrame, loudFrame.size))
    }

    @Test
    fun testLowLevelEchoOrAmbientNoiseBelow350DoesNotTrigger() {
        val quietFrame = createPcmFrame(50)
        for (i in 0 until 10) {
            vad.processFrame(quietFrame, quietFrame.size)
        }

        // Noise floor is 50. noiseFloor * 2.2 = 110, but clamped to minimum 350.0.
        // A low earcon echo at amplitude 280 should NOT trigger speech.
        val lowEchoFrame = createPcmFrame(280)
        for (i in 0 until 5) {
            val status = vad.processFrame(lowEchoFrame, lowEchoFrame.size)
            assertEquals("Low energy frame below minimum 350 threshold should be SILENCE", VadStatus.SILENCE, status)
        }
    }

    @Test
    fun testResetRestoresInitialState() {
        val silentFrame = createPcmFrame(0)
        for (i in 0 until 10) {
            vad.processFrame(silentFrame, silentFrame.size)
        }

        val loudFrame = createPcmFrame(2500)
        vad.processFrame(loudFrame, loudFrame.size)
        vad.processFrame(loudFrame, loudFrame.size)
        vad.processFrame(loudFrame, loudFrame.size)

        vad.reset()

        // After reset, first frame must be CALIBRATING again
        val statusAfterReset = vad.processFrame(silentFrame, silentFrame.size)
        assertEquals(VadStatus.CALIBRATING, statusAfterReset)
    }
}
