package com.sarvaai.app.adapt.signals

import android.content.Context
import android.content.res.Configuration
import android.media.AudioManager
import android.view.accessibility.AccessibilityManager
import android.view.accessibility.CaptioningManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class CollectedSignals(
    val isTouchExplorationOn: Boolean = false,
    val isSystemCaptioningOn: Boolean = false,
    val isMediaMuted: Boolean = false,
    val fontScale: Float = 1.0f,
    val failedAsrConsecutiveCount: Int = 0,
    val repeatTapCount: Int = 0,
    val averagePauseDurationMs: Long = 0L,
    val touchlessElapsedSec: Int = 0
)

class SignalCollector(private val context: Context? = null) {
    private val accessibilityManager = context?.getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager
    private val captioningManager = context?.getSystemService(Context.CAPTIONING_SERVICE) as? CaptioningManager
    private val audioManager = context?.getSystemService(Context.AUDIO_SERVICE) as? AudioManager

    private val _signals = MutableStateFlow(CollectedSignals())
    val signals: StateFlow<CollectedSignals> = _signals.asStateFlow()

    private var dwellPauseCount = 0
    private var totalDwellPauseMs = 0L

    fun pollSystemSignals(
        configuration: Configuration? = null,
        repeatTapCount: Int? = null,
        averagePauseDurationMs: Long? = null
    ) {
        val touchExploration = accessibilityManager?.isTouchExplorationEnabled == true
        val captions = captioningManager?.isEnabled == true
        val muted = audioManager?.getStreamVolume(AudioManager.STREAM_MUSIC) == 0
        val fontScale = configuration?.fontScale ?: _signals.value.fontScale

        val newRepeatTaps = repeatTapCount ?: _signals.value.repeatTapCount
        val newAvgPause = averagePauseDurationMs ?: _signals.value.averagePauseDurationMs
        if (averagePauseDurationMs != null) {
            dwellPauseCount = if (averagePauseDurationMs > 0) 1 else 0
            totalDwellPauseMs = averagePauseDurationMs
        }

        _signals.value = _signals.value.copy(
            isTouchExplorationOn = touchExploration,
            isSystemCaptioningOn = captions,
            isMediaMuted = muted,
            fontScale = fontScale,
            repeatTapCount = newRepeatTaps,
            averagePauseDurationMs = newAvgPause
        )
    }

    fun recordAsrFailure() {
        val current = _signals.value.failedAsrConsecutiveCount
        _signals.value = _signals.value.copy(failedAsrConsecutiveCount = current + 1)
    }

    fun resetAsrFailure() {
        _signals.value = _signals.value.copy(failedAsrConsecutiveCount = 0)
    }

    fun recordRepeatTap() {
        val current = _signals.value.repeatTapCount
        _signals.value = _signals.value.copy(repeatTapCount = current + 1)
    }

    fun resetRepeatTap() {
        _signals.value = _signals.value.copy(repeatTapCount = 0)
    }

    fun recordDwellHesitation(pauseMs: Long) {
        if (dwellPauseCount == 0 && _signals.value.averagePauseDurationMs > 0L) {
            dwellPauseCount = 1
            totalDwellPauseMs = _signals.value.averagePauseDurationMs
        }
        dwellPauseCount++
        totalDwellPauseMs += pauseMs
        val avg = totalDwellPauseMs / dwellPauseCount
        _signals.value = _signals.value.copy(averagePauseDurationMs = avg)
    }

    fun resetDwellHesitation() {
        dwellPauseCount = 0
        totalDwellPauseMs = 0L
        _signals.value = _signals.value.copy(averagePauseDurationMs = 0L)
    }

    fun updateTouchlessElapsed(seconds: Int) {
        _signals.value = _signals.value.copy(touchlessElapsedSec = seconds)
    }

    fun injectSimulatedSignals(simulated: CollectedSignals) {
        _signals.value = simulated
        dwellPauseCount = if (simulated.averagePauseDurationMs > 0L) 1 else 0
        totalDwellPauseMs = simulated.averagePauseDurationMs
    }

    fun resetAll() {
        dwellPauseCount = 0
        totalDwellPauseMs = 0L
        _signals.value = CollectedSignals()
    }
}
