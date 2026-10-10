package com.sarvaai.app

import com.sarvaai.app.adapt.inference.ActiveFeatureFlags
import com.sarvaai.app.adapt.inference.ProfileInferenceEngine
import com.sarvaai.app.adapt.signals.CollectedSignals
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class Phase12DynamicAdaptationTest {

    private lateinit var engine: ProfileInferenceEngine

    @Before
    fun setUp() {
        engine = ProfileInferenceEngine()
    }

    @Test
    fun testDefaultFlagsAreInactive() {
        val flags = engine.flags.value
        assertFalse(flags.voiceFirst)
        assertFalse(flags.captionsOn)
        assertFalse(flags.slowTts)
        assertFalse(flags.phraseBoard)
        assertFalse(flags.largeText)
        assertFalse(flags.stepByStep)
        assertFalse(engine.isUserOverridden.value)
    }

    @Test
    fun testTalkBackActivationTriggersVoiceFirst() {
        // Touch exploration adds 80 points (> 70 threshold)
        val signals = CollectedSignals(isTouchExplorationOn = true)
        engine.evaluate(signals)

        assertEquals(80, engine.visualScore)
        assertTrue(engine.flags.value.voiceFirst)
        assertNotNull(engine.lastAnnouncedReason.value)
        assertTrue(engine.lastAnnouncedReason.value!!.contains("Voice-first"))
    }

    @Test
    fun testHearingImpairmentTriggersCaptions() {
        // System captions (80) + Media muted (40) = 120 points (> 70 threshold)
        val signals = CollectedSignals(isSystemCaptioningOn = true, isMediaMuted = true)
        engine.evaluate(signals)

        assertEquals(120, engine.hearingScore)
        assertTrue(engine.flags.value.captionsOn)
        assertNotNull(engine.lastAnnouncedReason.value)
        assertTrue(engine.lastAnnouncedReason.value!!.contains("caption"))
    }

    @Test
    fun testSpeechDifficultyTriggersPhraseBoard() {
        // Consecutive ASR failures >= 3 adds 60 + 40 = 100 points (> 70 threshold)
        val signals = CollectedSignals(failedAsrConsecutiveCount = 3)
        engine.evaluate(signals)

        assertEquals(100, engine.speechScore)
        assertTrue(engine.flags.value.phraseBoard)
        assertNotNull(engine.lastAnnouncedReason.value)
        assertTrue(engine.lastAnnouncedReason.value!!.contains("phrase board"))
    }

    @Test
    fun testElderlyProfileTriggersLargeTextSlowTtsAndStepByStep() {
        // Font scale >= 1.3 (50) + Repeat taps >= 2 (40) = 90 points (> 70 threshold)
        val signals = CollectedSignals(fontScale = 1.4f, repeatTapCount = 3)
        engine.evaluate(signals)

        assertEquals(90, engine.ageingScore)
        assertTrue(engine.flags.value.largeText)
        assertTrue(engine.flags.value.slowTts)
        assertTrue(engine.flags.value.stepByStep)
    }

    @Test
    fun testHysteresisThresholdsPreventFlapping() {
        // Step 1: Activate voiceFirst with score 80 (> 70)
        engine.evaluate(CollectedSignals(isTouchExplorationOn = true))
        assertTrue(engine.flags.value.voiceFirst)

        // Step 2: Touch exploration turns off, but score drops to 0 (< 40)
        engine.evaluate(CollectedSignals(isTouchExplorationOn = false, touchlessElapsedSec = 0))
        assertFalse(engine.flags.value.voiceFirst)

        // Step 3: Idle for 15s (score 30). Because score 30 < 70, voiceFirst should NOT activate yet.
        engine.evaluate(CollectedSignals(touchlessElapsedSec = 16))
        assertEquals(30, engine.visualScore)
        assertFalse(engine.flags.value.voiceFirst)
    }

    @Test
    fun testUndoAdaptationProtocol() {
        // Activate multiple adaptations
        engine.evaluate(CollectedSignals(isTouchExplorationOn = true, isSystemCaptioningOn = true))
        assertTrue(engine.flags.value.voiceFirst)
        assertTrue(engine.flags.value.captionsOn)

        // User triggers Undo
        engine.undoAdaptation()
        assertTrue(engine.isUserOverridden.value)
        assertFalse(engine.flags.value.voiceFirst)
        assertFalse(engine.flags.value.captionsOn)

        // Subsequent signal evaluations should be ignored due to user override
        engine.evaluate(CollectedSignals(isTouchExplorationOn = true))
        assertFalse(engine.flags.value.voiceFirst)

        // Reset override resumes dynamic evaluation
        engine.resetOverride()
        assertFalse(engine.isUserOverridden.value)
        engine.evaluate(CollectedSignals(isTouchExplorationOn = true))
        assertTrue(engine.flags.value.voiceFirst)
    }

    @Test
    fun testManualFlagSetting() {
        val customFlags = ActiveFeatureFlags(
            voiceFirst = true,
            captionsOn = true,
            largeText = true
        )
        engine.setManualFlags(customFlags)

        assertTrue(engine.isUserOverridden.value)
        assertEquals(customFlags, engine.flags.value)
    }
}
