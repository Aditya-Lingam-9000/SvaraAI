# SARVAAI: ADVANCED TECHNICAL DEEP DIVES & ALGORITHMIC SPECIFICATIONS
## Comprehensive Engineering Foundations for Megathon 2026

---

## 1. Acoustic & Signal Processing Engineering

### 1.1 Microphone Ingestion & Sample Rate Conversion
Android devices possess varying native audio hardware sample rates (typically 48,000 Hz or 44,100 Hz). The Bhashini Conformer ASR acoustic models, however, are strictly trained on **16,000 Hz, 16-bit linear PCM mono audio**.

When opening `AudioRecord`:
```kotlin
val sampleRate = 16000
val channelConfig = AudioFormat.CHANNEL_IN_MONO
val audioFormat = AudioFormat.ENCODING_PCM_16BIT
val minBufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)
val bufferSize = (minBufferSize * 2).coerceAtLeast(4096)
```

If the hardware HAL fails to initialize at 16,000 Hz directly (rare on realme P3 Pro, but documented on older chipsets), SarvaAI uses a polyphase FIR downsampling filter or linear interpolation:
$$y[n] = x[\lfloor n \cdot R \rfloor] + (n \cdot R - \lfloor n \cdot R \rfloor)(x[\lfloor n \cdot R \rfloor + 1] - x[\lfloor n \cdot R \rfloor])$$
where $R = \frac{f_{\text{source}}}{16000}$.

### 1.2 Energy-Based Voice Activity Detection (VAD) Formulation
To prevent transmitting silence or ambient restaurant chatter, the audio stream is partitioned into 20ms frames ($N = 320$ samples at 16 kHz):
1. **Frame Energy ($E$)**:
   $$E_m = \frac{1}{N} \sum_{k=0}^{N-1} s_m^2[k]$$
2. **Root Mean Square (RMS)**:
   $$\text{RMS}_m = \sqrt{E_m}$$
3. **Adaptive Noise Floor Tracking**:
   During the initial 300ms (15 frames) of silence calibration:
   $$\text{NoiseFloor}_{m} = (1 - \alpha) \cdot \text{NoiseFloor}_{m-1} + \alpha \cdot \text{RMS}_m, \quad \alpha = 0.05$$
4. **Dynamic Speech Threshold**:
   $$\text{Threshold}_{\text{speech}} = \max(2.5 \cdot \text{NoiseFloor}, 350.0)$$
5. **Speech Onset & Endpointing State Machine**:
   - *Onset Trigger*: $\text{RMS}_m > \text{Threshold}_{\text{speech}}$ for 3 consecutive frames (60ms).
   - *Hangover / Endpointing Duration*: Silence timer initialized to 1,200ms (standard) or 2,000ms (speech-impaired / elderly mode). If $\text{RMS}$ remains below threshold for the full hangover duration, utterance capture terminates and buffer is submitted to Bhashini.

---

## 2. Computer Vision & Menu Quality Assurance Mathematics

### 2.1 Luminance Metric ($L$)
Extracted directly from the Y-plane buffer of the `YUV_420_888` image proxy:
$$L = \frac{1}{W \cdot H} \sum_{x=0}^{W-1} \sum_{y=0}^{H-1} Y(x, y)$$
- $L < 45 \implies$ Under-exposed / Too Dark.
- $L > 225 \implies$ Over-exposed / Specular Glare.

### 2.2 Blur Detection via Discrete 2D Laplacian Operator
Given a downsampled grayscale image $I(x, y)$ of dimensions $320 \times 240$:
1. The discrete 2D Laplacian kernel is convolved over the image:
   $$K = \begin{bmatrix} 0 & 1 & 0 \\ 1 & -4 & 1 \\ 0 & 1 & 0 \end{bmatrix}$$
   $$\Delta I(x, y) = I(x+1, y) + I(x-1, y) + I(x, y+1) + I(x, y-1) - 4 I(x, y)$$
2. Compute the mean of the response:
   $$\mu_{\Delta} = \frac{1}{M \cdot N} \sum_{x} \sum_{y} \Delta I(x, y)$$
3. Compute the variance of the Laplacian:
   $$\sigma^2_{\Delta} = \frac{1}{M \cdot N} \sum_{x} \sum_{y} (\Delta I(x, y) - \mu_{\Delta})^2$$
4. **Focus Metric**:
   - $\sigma^2_{\Delta} < 110 \implies$ Defocused or motion blurred.
   - $\sigma^2_{\Delta} \ge 110 \implies$ Sharp text edges present.

### 2.3 Text Bounding Box Clipping Metric
Let $B_i = (x_{\min}, y_{\min}, x_{\max}, y_{\max})$ be the bounding rectangle of text block $i$ from ML Kit:
- Top Margin Violation: $\min_i (y_{\min}^{(i)}) < 0.05 \cdot H$
- Bottom Margin Violation: $\max_i (y_{\max}^{(i)}) > 0.95 \cdot H$
- Left Margin Violation: $\min_i (x_{\min}^{(i)}) < 0.05 \cdot W$
- Right Margin Violation: $\max_i (x_{\max}^{(i)}) > 0.95 \cdot W$

---

## 3. Indic Linguistic & Phonetic Processing

### 3.1 Indic Soundex & Phonetic Distance Matrix
Standard Soundex algorithms fail completely on Indian phonetics because they are tuned for Anglo-Saxon naming conventions. SarvaAI's Indic Normalizer implements custom rules reflecting Sanskrit/Dravidian acoustic realities:

| Phonetic Category | Raw Transcribed Graphemes | Normalized Representation |
|---|---|:---:|
| Long Vowels | `ee`, `ii`, `iy` | `i` |
| Long Vowels | `oo`, `uu` | `u` |
| Long Vowels | `aa` | `a` |
| Diphthongs | `ai`, `ay` | `e` |
| Diphthongs | `au`, `ow` | `o` |
| Velar Aspirates | `k`, `kh` | `k` |
| Velar Aspirates | `g`, `gh` | `g` |
| Palatal Aspirates | `ch`, `chh` | `c` |
| Palatal Aspirates | `j`, `jh` | `j` |
| Dental Aspirates | `t`, `th` | `t` |
| Dental Aspirates | `d`, `dh` | `d` |
| Labial Aspirates | `p`, `ph`, `f` | `p` |
| Labial Aspirates | `b`, `bh` | `b` |
| Sibilants | `s`, `sh`, `shh`, `z` | `s` |
| Labiodental | `v`, `w` | `v` |

---

## 4. Groq Cloud System Prompts & Strict JSON Validation

### 4.1 Master System Prompt Specification
```
You are SarvaAI, an expert multilingual restaurant assistant.
You strictly adhere to deterministic restaurant dining operations.
Current Menu Context: {{COMPACT_MENU_JSON}}
Active Language: {{USER_LANGUAGE}} (en, hi, te)
Active Reading Level: {{READING_LEVEL}} (1 = Normal, 2 = Simple, 3 = Very Simple)

OUTPUT RULES:
1. Output strictly valid JSON conforming to the StructuredAiResponse schema.
2. In the "spoken" field:
   - Keep sentences under 20 words each.
   - Spell out all numbers and currencies in words (e.g. "two hundred and fifty rupees").
   - Strip all markdown symbols (*, _, #, `, [ ]) and emojis.
   - Write in clear, translation-friendly phrasing.
3. In the "caption" field:
   - Provide concise text for real-time captioned display.
4. For dish comparisons:
   - Provide a visual "table" object.
   - Provide a complete spoken prose equivalent in "table_narration" so blind users receive 100% of the information.
5. Dietary safety:
   - If a dish may contain allergens or violate Jain rules, mark "needs_staff_confirmation": true and append an alert.
   - Never provide medical advice.
```

---

## 5. Android 16 System Nuances & realme UI Optimizations

### 5.1 ColorOS / realme UI 6.0 Background Task Policies
realme UI includes aggressive background process termination rules:
1. **Disable Permission Monitoring**: Must be enabled in Developer Options to allow background coroutines during testing.
2. **Foreground Service Scoping**: All audio recording and ExoPlayer playback are bound to the active Activity lifecycle and foreground coroutine scopes (`lifecycleScope` / `ProcessLifecycleOwner`).
3. **Edge-to-Edge Mandatory Handling**: Android 16 strictly enforces edge-to-edge layouts. All Compose screens utilize `Modifier.systemBarsPadding()` to guarantee that system status bar icons and navigation gesture pills never overlap interactive controls.

### 5.2 O-Haptics Linear Motor Tuning
The realme P3 Pro features a high-end X-axis linear motor capable of haptic primitives via `VibrationEffect.Composition`:
- `PRIMITIVE_CLICK`: Used for tactile touch confirmation on 64dp buttons.
- `PRIMITIVE_TICK`: Used for camera frame stability and slider ticks.
- `PRIMITIVE_THUD`: Used for speech turn transitions and incoming waiter messages.

---

## 6. Comprehensive Verification Matrix

```
┌─────────────────────────┬──────────────────────┬──────────────────────┬──────────────────────┐
│ Verification Area       │ Test Scenario        │ Expected Outcome     │ Fallback Pathway     │
├─────────────────────────┼──────────────────────┼──────────────────────┼──────────────────────┤
│ Audio Recording         │ 16kHz Mono PCM Mic   │ Valid WAV buffer     │ 44.1kHz Resampling   │
│ VAD Endpointing         │ 1.2s Silence         │ Auto-submission      │ 12s Hard Cap         │
│ Bhashini ASR (Hindi)    │ Native Hindi Speech  │ Accurate Devanagari  │ Android SpeechRecog  │
│ Bhashini ASR (Telugu)   │ Native Telugu Speech │ Accurate Telugu Text │ Android SpeechRecog  │
│ Bhashini NMT            │ hi/te -> en          │ Clean English prose  │ Direct English mode  │
│ Groq LLM                │ Sub-second reasoning │ StructuredAiResponse │ Local Rule Fallback  │
│ Output Adapter          │ Price Verbalization  │ "480 rupees" spelled │ Numeric fallback     │
│ Camera Coaching         │ Edge Clipping        │ "Move phone up"      │ Manual Shutter       │
│ Camera Auto-Capture     │ 1.5s Stability       │ Automatic snapshot   │ Screen tap button    │
│ Dietary Rules           │ Jain / Root Veg      │ Violation flagged    │ Ask Waiter chip      │
│ Cart Persistence        │ Process Kill         │ 100% State restored  │ Room Database SQLite │
│ Waiter Bridge           │ Cross-Language Talk  │ Dual-View + TTS      │ Large Text Card      │
│ Bill Calculation        │ Indian GST (5%)      │ Exact paise total    │ Manual split mode    │
│ Condition Inference     │ TalkBack enabled     │ Voice-first flags    │ Manual toggle panel  │
│ Offline Resilience      │ Airplane Mode        │ Pre-bundled audio    │ System TTS           │
└─────────────────────────┴──────────────────────┴──────────────────────┴──────────────────────┘
```
