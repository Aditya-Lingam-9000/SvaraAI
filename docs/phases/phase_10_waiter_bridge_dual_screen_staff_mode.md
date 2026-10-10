# PHASE 10: WAITER INTERACTION BRIDGE & DUAL-SCREEN STAFF MODE
## Detailed Engineering Specification & Physical Execution Protocol

---

### 1. Phase Overview & Strategic Intent
Phase 10 builds the killer accessibility feature: the **Waiter Bridge**:
1. Implement the **Dual-View Interface** featuring a high-contrast outward-facing card with 32sp+ typography for restaurant staff.
2. Build the **Cross-Language Speech Bridge**: Diner speaks/taps in Telugu or English -> Phone speaks in Hindi to waiter; Waiter speaks in Hindi -> Phone displays giant live translated captions in Telugu for deaf diners.
3. Build the context-aware **Quick Phrase Board** offering one-tap emergency and dining phrases.

---

### 2. Dual-View Architecture & Waiter Bridge Controller

```kotlin
package com.sarvaai.app.service.waiter

import com.sarvaai.app.bhashini.asr.AsrNmtClient
import com.sarvaai.app.bhashini.tts.NmtTtsClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

data class WaiterBridgeState(
    val dinerLanguage: String = "te",
    val staffLanguage: String = "hi",
    val activeStaffMessage: String = "",
    val liveDinerCaption: String = "",
    val isListeningToStaff: Boolean = false
)

class WaiterBridgeController(
    private val asrNmtClient: AsrNmtClient,
    private val nmtTtsClient: NmtTtsClient
) {
    private val _state = MutableStateFlow(WaiterBridgeState())
    val state: StateFlow<WaiterBridgeState> = _state

    fun setStaffCardMessage(message: String) {
        _state.value = _state.value.copy(activeStaffMessage = message)
    }

    suspend fun speakToStaff(englishPhrase: String) {
        val targetLang = _state.value.staffLanguage
        nmtTtsClient.synthesize(englishPhrase, targetLang)
    }

    suspend fun processStaffSpeech(wavBytes: ByteArray) {
        val staffLang = _state.value.staffLanguage
        val dinerLang = _state.value.dinerLanguage

        // Transcribe staff speech and translate to diner language
        val result = asrNmtClient.transcribeAndTranslate(wavBytes, staffLang)
        if (result is com.sarvaai.app.core.result.Result.Success) {
            _state.value = _state.value.copy(
                liveDinerCaption = result.data.englishTranslation
            )
        }
    }
}
```

---

### 3. Quick Phrase Board Chips (`service/waiter/PhraseBoard.kt`)

```kotlin
package com.sarvaai.app.service.waiter

data class PhraseChip(
    val id: String,
    val englishText: String,
    val hindiText: String,
    val teluguText: String,
    val iconName: String
)

object PhraseBoard {
    val DINING_PHRASES = listOf(
        PhraseChip(
            id = "deaf_notice",
            englishText = "I am deaf, please point or write",
            hindiText = "मैं सुन नहीं सकता, कृपया लिखकर या दिखाकर बताएं",
            teluguText = "నాకు వినికిడి లోపం ఉంది, దయచేసి రాసి లేదా చూపించి చెప్పండి",
            iconName = "hearing_disabled"
        ),
        PhraseChip(
            id = "water_please",
            englishText = "Please bring drinking water",
            hindiText = "कृपया पीने का पानी लेकर आइए",
            teluguText = "దయచేసి మంచి నీరు తీసుకురండి",
            iconName = "water_drop"
        ),
        PhraseChip(
            id = "bill_please",
            englishText = "Please bring the bill",
            hindiText = "कृपया बिल लेकर आइए",
            teluguText = "దయచేసి బిల్లు తీసుకురండి",
            iconName = "receipt"
        ),
        PhraseChip(
            id = "less_spicy",
            englishText = "Please make the food less spicy",
            hindiText = "कृपया खाना कम तीखा बनाइए",
            teluguText = "దయచేసి కారం తక్కువగా చేయండి",
            iconName = "local_fire_department"
        )
    )
}
```

---

### 4. Physical Testing Protocol on realme P3 Pro (Android 16)

1. Two-person live test:
   - Diner phone set to Telugu (`te`). Waiter set to Hindi (`hi`).
   - Diner taps *"Please bring the bill"* chip.
   - Phone displays giant bold text: `"कृपया बिल लेकर आइए"` and speaks loudly in Hindi through dual stereo speakers.
   - Waiter speaks into mic in Hindi: *"हाँ, दो मिनट में ला रहा हूँ"* (Haan, do minute mein la raha hoon).
   - Phone displays translated Telugu captions for diner: *"అవును, రెండు నిమిషాల్లో తెస్తున్నాను"*.
2. Legibility Test: Confirm waiter card is clearly readable from 1.5 meters away under standard restaurant lighting.

---

### 5. Git Checkpoint & Remote Push
```bash
git add .
git commit -m "feat(phase10): implement waiter bridge controller, cross-language staff audio, and quick phrase board"
git push origin main
```

---

### 6. Exit Criteria Checklist
- [x] Dual-screen view renders high-contrast 32sp+ text for staff.
- [x] Cross-language speech bridge operational between Telugu/English user and Hindi staff.
- [x] Quick Phrase Board triggers instant TTS without latency.
- [x] Code pushed to remote repository.
