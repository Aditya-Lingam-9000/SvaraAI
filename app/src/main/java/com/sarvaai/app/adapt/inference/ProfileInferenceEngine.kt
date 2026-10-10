package com.sarvaai.app.adapt.inference

import com.sarvaai.app.adapt.signals.CollectedSignals
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class ActiveFeatureFlags(
    val voiceFirst: Boolean = false,
    val captionsOn: Boolean = false,
    val slowTts: Boolean = false,
    val phraseBoard: Boolean = false,
    val largeText: Boolean = false,
    val stepByStep: Boolean = false
)

class ProfileInferenceEngine {
    private val _flags = MutableStateFlow(ActiveFeatureFlags())
    val flags: StateFlow<ActiveFeatureFlags> = _flags.asStateFlow()

    private val _lastAnnouncedReason = MutableStateFlow<String?>(null)
    val lastAnnouncedReason: StateFlow<String?> = _lastAnnouncedReason.asStateFlow()

    private val _isUserOverridden = MutableStateFlow(false)
    val isUserOverridden: StateFlow<Boolean> = _isUserOverridden.asStateFlow()

    var visualScore: Int = 0
        private set
    var hearingScore: Int = 0
        private set
    var speechScore: Int = 0
        private set
    var ageingScore: Int = 0
        private set

    companion object {
        const val UPPER_THRESHOLD = 70
        const val LOWER_THRESHOLD = 40
    }

    fun evaluate(signals: CollectedSignals) {
        if (_isUserOverridden.value) return

        // 1. Calculate weighted scores
        visualScore = 0
        if (signals.isTouchExplorationOn) visualScore += 80
        if (signals.touchlessElapsedSec >= 15) visualScore += 30

        hearingScore = 0
        if (signals.isSystemCaptioningOn) hearingScore += 80
        if (signals.isMediaMuted) hearingScore += 40

        speechScore = 0
        if (signals.failedAsrConsecutiveCount >= 2) speechScore += 60
        if (signals.failedAsrConsecutiveCount >= 3) speechScore += 40

        ageingScore = 0
        if (signals.fontScale >= 1.3f) ageingScore += 50
        if (signals.repeatTapCount >= 2) ageingScore += 40

        // 2. Apply Hysteresis
        val current = _flags.value
        val newVoiceFirst = applyHysteresis(visualScore, current.voiceFirst)
        val newCaptionsOn = applyHysteresis(hearingScore, current.captionsOn)
        val newPhraseBoard = applyHysteresis(speechScore, current.phraseBoard)
        val newSlowTts = applyHysteresis(ageingScore, current.slowTts)
        val newLargeText = applyHysteresis(ageingScore, current.largeText)
        val newStepByStep = applyHysteresis(ageingScore, current.stepByStep)

        // Generate transparency message if new adaptation activated
        if (!current.voiceFirst && newVoiceFirst) {
            _lastAnnouncedReason.value = "Screen reader detected. Voice-first mode activated."
        } else if (!current.captionsOn && newCaptionsOn) {
            _lastAnnouncedReason.value = "Audio mute or system captions detected. Live caption bar activated."
        } else if (!current.phraseBoard && newPhraseBoard) {
            _lastAnnouncedReason.value = "Speech recognition difficulty detected. Quick phrase board suggested."
        } else if (!current.largeText && newLargeText) {
            _lastAnnouncedReason.value = "Large font scale detected. High-contrast large text and slower audio activated."
        }

        _flags.value = ActiveFeatureFlags(
            voiceFirst = newVoiceFirst,
            captionsOn = newCaptionsOn,
            phraseBoard = newPhraseBoard,
            slowTts = newSlowTts,
            largeText = newLargeText,
            stepByStep = newStepByStep
        )
    }

    private fun applyHysteresis(score: Int, currentlyActive: Boolean): Boolean {
        return if (currentlyActive) {
            score >= LOWER_THRESHOLD
        } else {
            score >= UPPER_THRESHOLD
        }
    }

    fun undoAdaptation() {
        _isUserOverridden.value = true
        _flags.value = ActiveFeatureFlags()
        _lastAnnouncedReason.value = "Adaptations reverted by user."
    }

    fun setManualFlags(flags: ActiveFeatureFlags) {
        _isUserOverridden.value = true
        _flags.value = flags
    }

    fun resetOverride() {
        _isUserOverridden.value = false
        _lastAnnouncedReason.value = null
    }

    fun clearAnnouncement() {
        _lastAnnouncedReason.value = null
    }
}
