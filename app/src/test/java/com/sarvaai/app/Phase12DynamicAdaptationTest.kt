package com.sarvaai.app

import com.sarvaai.app.adapt.inference.ActiveFeatureFlags
import com.sarvaai.app.adapt.inference.ProfileInferenceEngine
import com.sarvaai.app.adapt.signals.CollectedSignals
import com.sarvaai.app.adapt.signals.SignalCollector
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
        // Font scale >= 1.2 (40) + Font scale >= 1.35 (30) + Repeat taps >= 2 (40) = 110 points (> 70 threshold)
        val signals = CollectedSignals(fontScale = 1.4f, repeatTapCount = 3)
        engine.evaluate(signals)

        assertEquals(110, engine.ageingScore)
        assertTrue(engine.flags.value.largeText)
        assertTrue(engine.flags.value.slowTts)
        assertTrue(engine.flags.value.stepByStep)
        assertEquals(
            "Ageing condition inferred: Large text, unhurried 0.8x voice, and Step-by-Step guided help activated.",
            engine.lastAnnouncedReason.value
        )
    }

    @Test
    fun testAgeingInferenceFromFontScaleOnly() {
        // Font scale 1.35 triggers >= 1.2 (40) and >= 1.35 (30) = 70 points (>= 70 threshold)
        val signals = CollectedSignals(fontScale = 1.35f)
        engine.evaluate(signals)

        assertEquals(70, engine.ageingScore)
        assertTrue(engine.flags.value.largeText)
        assertTrue(engine.flags.value.slowTts)
        assertTrue(engine.flags.value.stepByStep)
        assertEquals(
            "Ageing condition inferred: Large text, unhurried 0.8x voice, and Step-by-Step guided help activated.",
            engine.lastAnnouncedReason.value
        )

        // Fresh engine with font scale 1.2 alone yields 40 points (< 70 threshold), does not trigger
        val freshEngine = ProfileInferenceEngine()
        freshEngine.evaluate(CollectedSignals(fontScale = 1.2f))
        assertEquals(40, freshEngine.ageingScore)
        assertFalse(freshEngine.flags.value.largeText)
        assertFalse(freshEngine.flags.value.slowTts)
        assertFalse(freshEngine.flags.value.stepByStep)
    }

    @Test
    fun testAgeingInferenceFromDwellPauseAndFontScale() {
        // Font scale >= 1.2 (40) + Average pause duration >= 3000ms (30) = 70 points (>= 70 threshold)
        val signals = CollectedSignals(fontScale = 1.2f, averagePauseDurationMs = 3000L)
        engine.evaluate(signals)

        assertEquals(70, engine.ageingScore)
        assertTrue(engine.flags.value.largeText)
        assertTrue(engine.flags.value.slowTts)
        assertTrue(engine.flags.value.stepByStep)
        assertEquals(
            "Ageing condition inferred: Large text, unhurried 0.8x voice, and Step-by-Step guided help activated.",
            engine.lastAnnouncedReason.value
        )
    }

    @Test
    fun testAgeingInferenceFromRepeatTapsAndDwellPause() {
        // Repeat taps >= 2 (40) + Average pause duration >= 3000ms (30) = 70 points (>= 70 threshold)
        val signals = CollectedSignals(repeatTapCount = 2, averagePauseDurationMs = 3500L)
        engine.evaluate(signals)

        assertEquals(70, engine.ageingScore)
        assertTrue(engine.flags.value.largeText)
        assertTrue(engine.flags.value.slowTts)
        assertTrue(engine.flags.value.stepByStep)
    }

    @Test
    fun testAgeingInferenceFromRepeatTapsAndFontScale() {
        // Repeat taps >= 2 (40) + Font scale >= 1.2 (40) = 80 points (>= 70 threshold)
        val signals = CollectedSignals(repeatTapCount = 2, fontScale = 1.25f)
        engine.evaluate(signals)

        assertEquals(80, engine.ageingScore)
        assertTrue(engine.flags.value.largeText)
        assertTrue(engine.flags.value.slowTts)
        assertTrue(engine.flags.value.stepByStep)
    }

    @Test
    fun testAgeingHysteresisBehavior() {
        // Step 1: Activate ageing adaptations with score 70 (>= 70 UPPER_THRESHOLD)
        engine.evaluate(CollectedSignals(fontScale = 1.35f))
        assertEquals(70, engine.ageingScore)
        assertTrue(engine.flags.value.largeText)
        assertTrue(engine.flags.value.slowTts)
        assertTrue(engine.flags.value.stepByStep)

        // Step 2: Signals drop to score 40 (fontScale = 1.2f).
        // Since currently active and 40 >= 40 LOWER_THRESHOLD, flags remain active due to hysteresis
        engine.evaluate(CollectedSignals(fontScale = 1.2f))
        assertEquals(40, engine.ageingScore)
        assertTrue(engine.flags.value.largeText)
        assertTrue(engine.flags.value.slowTts)
        assertTrue(engine.flags.value.stepByStep)

        // Step 3: Signals drop to score 30 (averagePauseDurationMs = 3000L only).
        // Since 30 < 40 LOWER_THRESHOLD, flags deactivate
        engine.evaluate(CollectedSignals(averagePauseDurationMs = 3000L))
        assertEquals(30, engine.ageingScore)
        assertFalse(engine.flags.value.largeText)
        assertFalse(engine.flags.value.slowTts)
        assertFalse(engine.flags.value.stepByStep)

        // Step 4: Signals rise back to score 40 (fontScale = 1.2f).
        // Since currently inactive and 40 < 70 UPPER_THRESHOLD, flags must NOT activate
        engine.evaluate(CollectedSignals(fontScale = 1.2f))
        assertEquals(40, engine.ageingScore)
        assertFalse(engine.flags.value.largeText)
        assertFalse(engine.flags.value.slowTts)
        assertFalse(engine.flags.value.stepByStep)
    }

    @Test
    fun testToggleManualAgeingMode() {
        // Activate manual ageing mode override
        engine.toggleManualAgeingMode(true)
        assertTrue(engine.isUserOverridden.value)
        assertTrue(engine.flags.value.slowTts)
        assertTrue(engine.flags.value.largeText)
        assertTrue(engine.flags.value.stepByStep)
        assertEquals(
            "Ageing condition inferred: Large text, unhurried 0.8x voice, and Step-by-Step guided help activated.",
            engine.lastAnnouncedReason.value
        )

        // In manual override mode, signal evaluations are ignored
        engine.evaluate(CollectedSignals())
        assertTrue(engine.flags.value.slowTts)
        assertTrue(engine.flags.value.largeText)
        assertTrue(engine.flags.value.stepByStep)

        // Deactivate manual ageing mode override
        engine.toggleManualAgeingMode(false)
        assertFalse(engine.isUserOverridden.value)
        assertFalse(engine.flags.value.slowTts)
        assertFalse(engine.flags.value.largeText)
        assertFalse(engine.flags.value.stepByStep)
        assertNull(engine.lastAnnouncedReason.value)
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

    @Test
    fun testSignalCollectorDwellHesitationAndAgeingSignals() {
        val collector = SignalCollector()

        // 1. Dwell hesitation accumulation
        collector.recordDwellHesitation(4000L)
        assertEquals(4000L, collector.signals.value.averagePauseDurationMs)
        collector.recordDwellHesitation(2000L)
        assertEquals(3000L, collector.signals.value.averagePauseDurationMs)

        // 2. Repeat taps recording
        collector.recordRepeatTap()
        collector.recordRepeatTap()
        assertEquals(2, collector.signals.value.repeatTapCount)

        // 3. Inject simulated signals with Ageing telemetry
        val simulated = CollectedSignals(
            fontScale = 1.4f,
            repeatTapCount = 3,
            averagePauseDurationMs = 3500L
        )
        collector.injectSimulatedSignals(simulated)
        assertEquals(1.4f, collector.signals.value.fontScale, 0.001f)
        assertEquals(3, collector.signals.value.repeatTapCount)
        assertEquals(3500L, collector.signals.value.averagePauseDurationMs)

        // Evaluate injected signals through engine
        engine.evaluate(collector.signals.value)
        assertTrue(engine.flags.value.largeText)
        assertTrue(engine.flags.value.slowTts)
        assertTrue(engine.flags.value.stepByStep)

        // 4. Reset All
        collector.resetAll()
        assertEquals(1.0f, collector.signals.value.fontScale, 0.001f)
        assertEquals(0, collector.signals.value.repeatTapCount)
        assertEquals(0L, collector.signals.value.averagePauseDurationMs)
    }
}
