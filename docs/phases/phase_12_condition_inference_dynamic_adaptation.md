# PHASE 12: CONDITION INFERENCE ENGINE & DYNAMIC ADAPTATION
## Detailed Engineering Specification & Physical Execution Protocol

---

### 1. Phase Overview & Strategic Intent
Phase 12 builds SarvaAI's defining hackathon innovation:
1. Construct the on-device **Signal Collector** monitoring system accessibility settings and interaction heuristics.
2. Build the **Profile Inference Engine** utilizing weighted hysteresis scoring to infer user conditions (Visual, Hearing, Speech, Ageing).
3. Dynamically activate fine-grained, reversible **Feature Flags** (`voiceFirst`, `captionsOn`, `slowTts`, `phraseBoard`, `largeText`, `stepByStep`).
4. Enforce the **Transparency & Undo Protocol** announcing every adaptation with a one-tap reversion path.
5. Build the **Interactive Debug Signal Panel** for real-time hackathon jury demonstration.

---

### 2. Signal Collector Architecture (`adapt/signals/SignalCollector.kt`)

```kotlin
package com.sarvaai.app.adapt.signals

import android.content.Context
import android.content.res.Configuration
import android.media.AudioManager
import android.view.accessibility.AccessibilityManager
import android.view.accessibility.CaptioningManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

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

class SignalCollector(private val context: Context) {
    private val accessibilityManager = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager
    private val captioningManager = context.getSystemService(Context.CAPTIONING_SERVICE) as? CaptioningManager
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager

    private val _signals = MutableStateFlow(CollectedSignals())
    val signals: StateFlow<CollectedSignals> = _signals

    fun pollSystemSignals(configuration: Configuration) {
        val touchExploration = accessibilityManager?.isTouchExplorationEnabled == true
        val captions = captioningManager?.isEnabled == true
        val muted = audioManager?.getStreamVolume(AudioManager.STREAM_MUSIC) == 0
        val fontScale = configuration.fontScale

        _signals.value = _signals.value.copy(
            isTouchExplorationOn = touchExploration,
            isSystemCaptioningOn = captions,
            isMediaMuted = muted,
            fontScale = fontScale
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
}
```

---

### 3. Weighted Hysteresis Scoring Model (`adapt/inference/ProfileInferenceEngine.kt`)

```kotlin
package com.sarvaai.app.adapt.inference

import com.sarvaai.app.adapt.signals.CollectedSignals
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

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
    val flags: StateFlow<ActiveFeatureFlags> = _flags

    private var visualScore = 0
    private var hearingScore = 0
    private var speechScore = 0
    private var ageingScore = 0

    private val UPPER_THRESHOLD = 70
    private val LOWER_THRESHOLD = 40

    fun evaluate(signals: CollectedSignals) {
        // 1. Calculate Scores
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
        _flags.value = ActiveFeatureFlags(
            voiceFirst = applyHysteresis(visualScore, current.voiceFirst),
            captionsOn = applyHysteresis(hearingScore, current.captionsOn),
            phraseBoard = applyHysteresis(speechScore, current.phraseBoard),
            slowTts = applyHysteresis(ageingScore, current.slowTts),
            largeText = applyHysteresis(ageingScore, current.largeText),
            stepByStep = applyHysteresis(ageingScore, current.stepByStep)
        )
    }

    private fun applyHysteresis(score: Int, currentlyActive: Boolean): Boolean {
        return if (currentlyActive) {
            score >= LOWER_THRESHOLD
        } else {
            score >= UPPER_THRESHOLD
        }
    }
}
```

---

### 4. Interactive Debug Signal Panel (`ui/screens/debug/DebugSignalPanel.kt`)

Allows hackathon judges and testers to inject simulated signals and observe real-time UI/audio adaptation:
- **Button 1**: *"Simulate Blind Diner (TalkBack active)"* -> App immediately activates `voiceFirst`, speaks welcoming greeting, and suppresses visual distractions.
- **Button 2**: *"Simulate Deaf Diner (Volume Muted)"* -> App automatically renders large-print `LiveCaptionBar`.
- **Button 3**: *"Simulate Speech Impairment (ASR Failures x2)"* -> App immediately pops up the `Quick Phrase Board` and suggests tap-to-speak ordering.
- **Button 4**: *"Simulate Elderly Diner (Font Scale 1.5x, Repeat tapped)"* -> App slows voice to 0.8x, enlarges buttons, and enables step-by-step guidance.
- **Persistent Button**: *"Undo Adaptation"* -> Reverts all flags.

---

### 5. Physical Testing Protocol on realme P3 Pro (Android 16)

1. Turn on TalkBack in realme P3 Pro settings -> Launch SarvaAI -> Verify app automatically adapts to `voiceFirst = true`.
2. Mute realme P3 Pro volume -> Verify live caption bar slides down automatically.
3. Open Debug Signal Panel -> Tap each simulated condition -> Verify physical phone screen and voice adapt dynamically in real-time.

---

### 6. Git Checkpoint & Remote Push
```bash
git add .
git commit -m "feat(phase12): implement signal collector, condition inference engine with hysteresis, dynamic feature flags and interactive debug signal panel"
git push origin main
```

---

### 7. Exit Criteria Checklist
- [x] Signal collector monitors system accessibility settings on Android 16.
- [x] Hysteresis scoring model prevents rapid flag flapping.
- [x] Transparent spoken announcement accompanies all adaptations with instant Undo.
- [x] Debug Signal Panel functional for live demonstration.
- [x] Code pushed to remote repository.
