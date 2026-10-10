# Accessible Restaurant Ordering Assistant: Product Requirements and Build Plan

## 0. Read this first: what I could and couldn't verify

You asked for live web research on Bhashini, but web search is off in this chat. Everything below is from my knowledge up to mid-2026, and turning web search on would let me check current docs. Bhashini's onboarding and endpoints have changed several times, so anything marked **(verify)** must be checked in your first 30 minutes. Section 3 is built so that a wrong endpoint or ID costs you minutes, not hours.

Assumptions I'm making (tell me if any are wrong):
- Languages are **English + Hindi + Telugu**. Hindi has the widest Bhashini coverage and best-tested models, and Telugu fits since the event is at IIIT Hyderabad. Everything is config-driven so you can add Tamil or Kannada as bonus languages.
- Conditions are **Visual (mandatory), Hearing, Speech, Ageing**. That's four, and the rubric gives bonus points for more than three done well.
- The stack is native Android: Kotlin and Jetpack Compose.

---

## 1. Product definition

**Name (working):** *SarvaAI*, a voice-first restaurant companion.

**One-line pitch:** Point your phone at a physical menu and Thali reads it, explains it, answers dietary questions, takes your order, talks to the waiter for you, and splits the bill. It does this in English, Hindi or Telugu, adapts to how *you* need to use it, and still works when the network or speech recognition fails.

### 1.1 Design principles (they map directly to the rubric)

1. **Outputs, not just interfaces (25%).** The app never shows or speaks raw LLM text. Every AI response is structured data that an *Output Adapter* renders per user needs: spoken, captioned, headed, listed, simplified.
2. **Features, not modes.** The slides say: *"Each condition needs a specific, demonstrable feature, not a generic accessibility mode"* and *"System should be able to infer the condition and act accordingly."* So we build about 12 discrete adaptive features and an **inference engine** that switches them on from signals, always reversibly and transparently.
3. **Bhashini is the spine.** ASR, translation, TTS, plus transliteration and language detection where available. Fallbacks exist only for failure and are clearly labeled.
4. **Deterministic actions, probabilistic language.** The LLM interprets and explains. App code owns the order, totals, bill and split, so it can't hallucinate an order.
5. **Fail loudly in every modality.** Every error is spoken, shown and vibrated, with a recovery path.
6. **Spine first.** A working speech-to-speech loop exists by hour 3. Everything else layers on top.

---

## 2. Scope: end-to-end user journey

Journey: **Launch → Language → Capture menu → Understand → Ask → Compare → Order → Confirm → Service (waiter / bill / split / feedback)**.

| Stage | What the user does | What the system does |
|---|---|---|
| Launch | Opens app (maybe with TalkBack) | Speaks a short welcome, plays an earcon, starts listening for a language |
| Language | Says "Telugu" or just speaks | Detects language, confirms by voice and big buttons, remembers it |
| Capture | Points phone at the menu | Spoken camera coaching, auto-capture when stable, multi-page support |
| Understand | Listens | Layout description ("4 sections: Starters, Mains, Breads, Desserts, about 38 items"), then reads section by section with prices |
| Ask | "Is this Jain?", "Which is mildest?", "What is Gongura mutton?" | Dietary and ingredient answers with confidence, plus a confirm-with-staff prompt |
| Compare | "Compare paneer butter masala and kadai paneer" | Spoken narrative, with a visual comparison for sighted users |
| Order | "Add two butter naan" | Cart updates, read-back, explicit confirmation |
| Waiter | "Call the waiter" | Plays a TTS line in the staff's language, shows a large card, vibrates |
| Bill | "Bill please", "split among 3" | Totals with tax lines, equal or by-item split, spoken breakdown |
| Feedback | Speaks a complaint | ASR, structured summary, translated to the staff language, shown and spoken to staff |

**Out of scope (matches the slide):** training LLMs, medical/legal/financial advice, single-language solutions, skipping Bhashini, special hardware, and real payment gateways. Split payment means *calculation plus optional UPI deep link*.

---

## 3. Bhashini: how it works and how to integrate it

### 3.1 What Bhashini is
Bhashini is India's National Language Translation Mission platform (MeitY / Digital India Bhashini Division). It exposes government-hosted models for:
- **ASR**: speech to text for the 22 scheduled languages plus English, with some language-specific and multilingual models
- **NMT**: IndicTrans2-based translation across the 22 scheduled languages and English
- **TTS**: speech synthesis for a smaller set. Hindi, Tamil, Telugu, Kannada, Malayalam, Marathi, Bengali, Gujarati, Odia, Punjabi, Assamese and English are commonly available, but check each one **(verify)**.
- **Others**: transliteration, text and audio language detection, ITN, punctuation, VAD, and some OCR models **(verify which are exposed in your account's pipeline)**

Model hosting is via the **Dhruva** inference platform. Models are catalogued in **ULCA** (Universal Language Contribution API).

### 3.2 Getting credentials (do this immediately, ideally the night before)
1. Register and log in at **bhashini.gov.in**. Find the developer/API section, or the profile page where you generate a **User ID** and **ULCA API Key**.
2. There's an alternate/newer path via the Bhashini dashboard for direct Dhruva keys **(verify which your account uses)**.
3. **Ask the hackathon organizers for event keys or raised quotas.** They're IIIT Hyderabad and Bhashini-aligned, and a hackathon on Bhashini usually has contacts who can lift rate limits.
4. Keep two sets of keys if possible, in case one hits a limit mid-testing.

### 3.3 The two-step pipeline API (the commonly documented flow)

**Step 1: get pipeline config.** This returns the model/service IDs, the inference URL and a short-lived inference key.

```
POST https://meity-auth.ulcacontrib.org/ulca/apis/v0/model/getModelsPipeline
Headers: userID: <your user id>
         ulcaApiKey: <your ulca api key>
         Content-Type: application/json

{
  "pipelineTasks": [
    { "taskType": "asr",
      "config": { "language": { "sourceLanguage": "te" } } },
    { "taskType": "translation",
      "config": { "language": { "sourceLanguage": "te", "targetLanguage": "en" } } },
    { "taskType": "tts",
      "config": { "language": { "sourceLanguage": "te" } } }
  ],
  "pipelineRequestConfig": { "pipelineId": "64392f96daac500b55c543cd" }
}
```
(The pipeline ID above is the widely documented ASR+NMT+TTS one **(verify)**.)

The response includes:
- `pipelineResponseConfig[].config[].serviceId` for each task
- `pipelineInferenceAPIEndPoint.callbackUrl` (typically `https://dhruva-api.bhashini.gov.in/services/inference/pipeline`)
- `pipelineInferenceAPIEndPoint.inferenceApiKey.{name,value}`, which is the header name and value for step 2

**Step 2: compute (ASR + translation in one call).**

```
POST <callbackUrl>
Headers: <inferenceApiKey.name>: <inferenceApiKey.value>   (usually "Authorization")
         Content-Type: application/json

{
  "pipelineTasks": [
    { "taskType": "asr",
      "config": { "language": { "sourceLanguage": "te" },
                  "serviceId": "<from step 1>",
                  "audioFormat": "wav",
                  "samplingRate": 16000 } },
    { "taskType": "translation",
      "config": { "language": { "sourceLanguage": "te", "targetLanguage": "en" },
                  "serviceId": "<from step 1>" } }
  ],
  "inputData": { "audio": [ { "audioContent": "<base64 audio>" } ] }
}
```

**Text-only calls (translation + TTS):**

```
{
  "pipelineTasks": [
    { "taskType": "translation",
      "config": { "language": { "sourceLanguage": "en", "targetLanguage": "hi" },
                  "serviceId": "<from step 1>" } },
    { "taskType": "tts",
      "config": { "language": { "sourceLanguage": "hi" },
                  "serviceId": "<from step 1>",
                  "gender": "female" } }
  ],
  "inputData": { "input": [ { "source": "Your order has two items." } ] }
}
```

**Response parsing** (extract by `taskType`, not by position):
- ASR gives `output[0].source` (the transcript)
- Translation gives `output[0].target`
- TTS gives `audio[0].audioContent` (base64 WAV)

### 3.4 Integration rules that will save you hours
1. **Never hardcode serviceIds.** Fetch config at startup per (task, language pair), cache it in memory and on disk, and refresh on 401/403/5xx. Model IDs and inference keys change.
2. **Probe a capability matrix on first run.** For each of the 3 languages × {asr, translation, tts, transliteration, lang-detect}, call getModelsPipeline and record what exists. The UI and fallbacks key off this matrix.
3. **Audio format:** 16 kHz mono 16-bit PCM WAV is the safe choice. FLAC is smaller if the pipeline accepts it, which helps low-bandwidth, so test both **(verify)**. Keep clips short, about 5–15 s, because long clips are slow or rejected. The exact max varies **(verify)**.
4. **Combine calls:** one call for ASR→NMT, one for NMT→TTS. That's two Bhashini round-trips per turn, not four.
5. **TTS chunking:** split text into sentences under about 200 characters and synthesize them in order. Start playback after sentence 1.
6. **TTS speed:** don't rely on a server speed parameter. Control speed client-side with `PlaybackParams.setSpeed()` (ExoPlayer or MediaPlayer), which preserves pitch.
7. **Numbers and currency:** Bhashini TTS often mishandles "₹250" or "2x". Verbalize numbers into words in the target language in the app *before* TTS (a small per-language number-to-words utility, or have the LLM emit a `spoken` field with numbers spelled out). This is an easy quality win that judges will notice.
8. **Dish names:** don't translate them. Protect each name with a placeholder before translation, then reinsert it **transliterated into the target script** (Bhashini transliteration if available, otherwise LLM transliteration). "Paneer Butter Masala" spoken by Telugu TTS from English letters sounds broken. In Telugu script it sounds right.
9. **Streaming ASR (stretch):** Dhruva offers WebSocket/Socket.IO streaming ASR **(verify)**. For waiter live-captions, you can fake near-real-time with 3–5 s VAD-chunked uploads, which is far less risky.
10. **Timeouts and retries:** 15 s timeout, 2 retries with backoff, and a circuit breaker after N consecutive failures.
11. **Error taxonomy:** `NoNetwork`, `Timeout`, `AuthExpired`, `ServiceUnavailable`, `EmptyTranscript`, `UnsupportedLanguage`, `RateLimited`. Each maps to a recovery behavior (Section 8).
12. **Declare dependencies:** the rules say to declare all pre-existing libraries and APIs. Keep a running list as you add them.

### 3.5 Your 30-minute verification checklist
- [ ] Credentials work: getModelsPipeline returns 200 via curl or Postman
- [ ] ASR Hindi, Telugu and English each return a sensible transcript from a recorded WAV
- [ ] NMT works en↔hi, en↔te, and hi↔te
- [ ] TTS returns playable audio for hi, te and en (save base64 to a .wav and play it)
- [ ] Check whether transliteration, language-detection and OCR tasks exist in the pipeline
- [ ] Measure the latency of each call from your hackathon Wi-Fi
- [ ] Note any rate-limit errors, and ask organizers if you hit them

**If Bhashini is down or slow during the event**, you still need the demo to work. Cache pre-recorded Bhashini TTS for all static phrases (Section 8.4) and keep a recorded-response mode behind a hidden debug flag.

---

## 4. System architecture

### 4.1 Voice turn pipeline (this is the "Bhashini speech-to-speech" requirement)

```
Mic → VAD/endpointing → WAV 16k mono
   → [Bhashini] ASR (user lang) → NMT (user lang → en)        ← one call
   → Dialogue manager + LLM (en, structured JSON, tool calls)
   → Output Adapter (profile-aware, simplifies, verbalizes numbers, protects names)
   → [Bhashini] NMT (en → user/target lang) → TTS             ← one call
   → Audio player (speed-controlled) + synced captions + haptics
```

Rules:
- If the user language is English, skip both NMT steps.
- Cross-language mode: "speak Telugu, hear Hindi" is just a different target language on the output half.
- The menu may be in English while the user speaks Telugu. The menu data stays canonical in English plus original text, and gets translated on output.

### 4.2 Menu pipeline

```
CameraX preview → quality analysis (blur, brightness, text coverage via ML Kit)
→ spoken coaching ("move phone up", "too dark, move near light")
→ auto-capture when stable → (repeat for multi-page)
→ Multimodal LLM: image → structured Menu JSON (sections, items, prices, veg/non-veg marks, descriptions, layout notes)
→ validation (price sanity, duplicates, low-confidence flags)
→ Menu store (Room) → layout description + read-aloud
```

**OCR strategy:**
- Primary: a multimodal vision LLM that outputs structured JSON. It handles columns, the green/red veg dots, and mixed scripts far better than raw OCR.
- On-device fallback: ML Kit text recognition (Latin and Devanagari scripts). It has no Telugu, so state this honestly as a limitation.
- Optional: try Bhashini's OCR if it's exposed **(verify)** and show it as a second opinion.
- If the menu text is unclear, say so and ask for a retake, as the slide requires: *"a prompt to retake the photo when text is unclear."*

### 4.3 The "AI" step
Rules say you can't train an LLM but can use existing ones, so declare it.
- Use an `LlmClient` interface with one implementation (Gemini Flash-class is a natural fit given Antigravity, free tiers and vision). Keep a second implementation behind the interface in case of rate limits.
- Requirements: vision input, JSON-schema constrained output, and tool/function calling. Use the current model names from the provider docs **(verify)**.
- Keep keys in `local.properties` → `BuildConfig` for the hackathon. Don't commit them. A backend proxy is better engineering but adds a failure point in 12 hours. Mention it as the production path.

### 4.4 Tech stack

| Concern | Choice |
|---|---|
| Language/UI | Kotlin, Jetpack Compose, Material 3 |
| Architecture | MVVM + unidirectional state (StateFlow), single-activity, Navigation Compose |
| DI | Manual DI or Koin. Skip Hilt/kapt to reduce AI-generated build breakage. |
| Network | OkHttp (HTTP/2, connection pooling) + kotlinx.serialization |
| Async | Coroutines + Flow |
| Camera | CameraX (preview + ImageAnalysis + ImageCapture) |
| Local OCR/quality | ML Kit Text Recognition |
| Audio in | `AudioRecord` + simple energy-based VAD |
| Audio out | Media3 ExoPlayer (speed control, queueing) |
| Persistence | Room (menu, order, session, translation and TTS cache) + DataStore (settings) |
| Min/target SDK | minSdk 26, targetSdk 34 or 35 |
| Versions | Pin everything in `libs.versions.toml`. Never let the AI agent bump AGP/Kotlin/Compose versions casually. |

### 4.5 Package layout

```
app/src/main/java/.../thali/
  core/            Result types, dispatchers, logging, Latency tracer
  bhashini/        BhashiniConfigManager, BhashiniApi, AsrNmtClient, NmtTtsClient,
                   TransliterationClient, CapabilityMatrix, BhashiniError
  audio/           Recorder, Vad, WavEncoder, SpeechPlayer(queue+speed), Earcons, Haptics
  llm/             LlmClient, Prompts, Schemas, ToolDefinitions
  menu/            CameraAnalyzer, MenuExtractor, MenuModels, MenuRepository, LayoutNarrator
  dialogue/        DialogueManager (state machine), IntentRouter, ToolExecutor,
                   OrderManager, BillCalculator, FuzzyMatcher
  adapt/           SignalCollector, ProfileInferenceEngine, FeatureFlags,
                   OutputAdapter, SimplifyController, NumberVerbalizer, TableNarrator
  failure/         NetworkMonitor, RetryPolicy, ConfirmationManager, OfflineFallbacks
  ui/              screens, components, theme (typography scale, contrast palettes)
  data/            Room DB, DAOs, DataStore
```

---

## 5. Condition handling

The slides ask for specific, demonstrable features per condition and for inference. For each condition: the feature, how it's built, and how it's inferred.

### 5.1 Visual impairment (mandatory, deepest implementation)

**Features**
1. **Voice-first launch.** No visual dependency from first second. Welcome speech plus earcon, and the whole screen is one giant "tap anywhere to speak" target.
2. **Audio camera coaching.** Spoken guidance ("menu is cut off on the right, move phone left", "too dark", "hold steady") plus a distinct haptic when captured. Blur is measured by Laplacian variance, brightness from luma, and edge-clipping from ML Kit text bounding boxes touching the frame border.
3. **Layout description.** "This menu has 4 sections and 38 items. Starters has 9 items, starting from 120 rupees." The LLM supplies layout notes (columns, highlighted items, chef specials, veg symbols).
4. **Sectioned read-aloud with navigation.** Voice commands "next section", "go back", "skip", "read prices only", "read only vegetarian."
5. **Price handling.** Always spoken with the item, in words, in the user's language.
6. **Order read-back and confirmation.** "You've ordered: two butter naan, one paneer butter masala. Total 560 rupees. Say confirm to place the order."
7. **Full TalkBack compatibility.** Real heading semantics, content descriptions, logical focus order, 48dp+ touch targets, live regions for status, no gesture-only controls.
8. **Double-speech prevention.** When TalkBack is on and Bhashini audio is playing, suppress redundant live-region announcements for content already being spoken.

**Inference signals:** TalkBack/touch exploration enabled (`AccessibilityManager.isTouchExplorationEnabled`), the user never touching the screen during onboarding, camera held without a visible-menu framing, and the user relying on "what's on the screen?"-style questions.

### 5.2 Hearing impairment

**Features**
1. **Live captions** for every spoken output, with word-level highlight synced to the audio, so it's readable at a glance.
2. **Persistent transcript** of the whole session, scrollable and searchable.
3. **Visual and haptic alerts** instead of audio cues: color flash, large icon, and distinct vibration patterns for "order placed," "waiter acknowledged," "error," and "your turn."
4. **Waiter conversation mode (the killer feature).** The phone becomes a two-way bridge:
   - The user types or taps a phrase. The app shows it in large text for the waiter and can also speak it with Bhashini TTS in the staff's language.
   - The waiter speaks. Chunked Bhashini ASR (waiter's language) feeds NMT into the user's language, shown as big live captions.
   - Quick chips include "I am deaf, please write or show me", "Bring the bill", and "Is this vegetarian?"
5. **Captions on by default** if the system captioning setting is on.

**Inference signals:** `CaptioningManager.isEnabled`, media volume at zero, the user repeatedly responding by text to spoken prompts, the user tapping "repeat" or "show text" often, and never granting or using the mic.

### 5.3 Speech impairment

**Features**
1. **Phrase board.** Context-aware tap-to-speak phrases ("I'd like to order", "Less spicy please", "Bill please", "Is this vegetarian?"), spoken aloud by Bhashini TTS in the waiter's language.
2. **Menu-aware word prediction.** After an item or section is partially entered (typed, or partially recognized), suggest dish names from the *captured menu*, which is a tiny closed vocabulary and very high accuracy.
3. **Tolerant ASR handling:**
   - Long endpointing, so the app waits for slow speech and doesn't cut users off mid-word
   - Accept single-word or fragment utterances ("naan... two")
   - Fuzzy matching of transcripts to menu items using edit distance plus an Indic phonetic normalization (collapsing vowel length, aspirates, and similar sounds)
   - Candidate chips: "Did you mean: Butter Naan / Garlic Naan?" instead of failing
4. **Speak-for-me.** Typed or tapped text is voiced to the waiter by TTS.
5. **Auto-escalation.** After 2 failed recognitions, the UI offers the phrase board and typing without the user having to ask.

**Inference signals:** repeated low-quality or empty ASR results, very short fragmented utterances, long mid-utterance pauses, rising retry counts, and the user switching to tapping after speaking attempts.

### 5.4 Ageing

**Features**
1. **Large text and high contrast**, up to 200%, with big targets (64–96dp), one primary action per screen.
2. **Slow voice.** Default 0.8× playback with clear pauses between sentences. A prominent slower/faster control.
3. **Step-by-step flow.** One question at a time ("First, which language?", "Now point at the menu"), a persistent big **Repeat** button, and no timeouts that punish slowness.
4. **Fewer choices.** Show or read at most 3 options at once, with "more" on request.
5. **Simpler explanations.** Default to simple language, with familiar analogies for dishes ("Dahi vada: soft lentil dumplings in curd, cool and mild").
6. **Gentle error tone.** No blame ("I didn't catch that, let's try again"), and generous retries.

**Inference signals:** system font scale ≥ 1.3, display size increased, slow speech rate, many "repeat" taps, long gaps before acting, and hesitation patterns.

### 5.5 Condition inference engine (the "infer and act" requirement)

**Inputs (`SignalCollector`):**

| Signal | Source | Suggests |
|---|---|---|
| TalkBack / touch exploration on | AccessibilityManager | Visual |
| Captions enabled | CaptioningManager | Hearing |
| Media volume muted, headphones absent | AudioManager | Hearing (weak) |
| Font scale ≥ 1.3, display size up | Configuration | Ageing / low vision |
| Repeated empty/low-confidence ASR | Pipeline | Speech |
| Fragmented utterances, long pauses | VAD timing | Speech / Ageing |
| Many Repeat/Slower taps | UI events | Ageing / Hearing |
| Replies by typing to voice prompts | UI events | Hearing / Speech |
| Zero touch in first 15 s plus speaking | UI events | Visual |

**Engine:** each signal contributes a weighted score to each condition. Hysteresis prevents flapping, and a feature turns on when its condition passes a threshold. The output is a set of **feature flags** (`captionsOn`, `slowTts`, `phraseBoard`, `largeText`, `stepByStep`, `voiceFirst`, `tolerantAsr`, and so on), not modes.

**Transparency and control:**
- The app announces and shows every adaptation: "I've turned on larger text and slower speech. Say 'undo' to switch back."
- A settings screen shows what was inferred and why, and every flag can be overridden.
- Inference is local-only and never stored beyond the session unless the user opts in. It adapts, it doesn't diagnose.
- A **debug signal panel** lets you simulate signals so you can test and show each inference path without needing real users.

---

## 6. Accessible outputs (25% of the score)

### 6.1 Structured AI response
The LLM never returns free-form markdown. It returns this:

```json
{
  "intent": "compare_items",
  "headline": "Paneer Butter Masala vs Kadai Paneer",
  "spoken": "Paneer butter masala is creamy and mild, 280 rupees. Kadai paneer is spicier with peppers, 260 rupees.",
  "caption": "Paneer Butter Masala: creamy, mild, ₹280. Kadai Paneer: spicy, with peppers, ₹260.",
  "bullets": ["..."],
  "table": { "columns": ["Dish","Spice","Price"], "rows": [["...","..."]] },
  "table_narration": "Two dishes. The first is mild at 280...",
  "simple_level": 1,
  "confidence": "high",
  "needs_staff_confirmation": false,
  "actions": [{"id":"add_pbm","label":"Add Paneer Butter Masala"}],
  "followup_prompt": "Would you like to add one of these?"
}
```

### 6.2 Output Adapter rules
- **Real headings and lists** via Compose semantics (`heading()`), never fake-styled text.
- **Tables are described in words** (`table_narration`) for spoken and TalkBack output, with the visual table only for sighted users. The same goes for any code or chart-like content the LLM produces.
- **Strip symbols** (markdown, emoji, bullets) before TTS.
- **Sentence caps:** spoken sentences stay under about 20 words and are chunked for TTS.
- **Numbers and prices** are verbalized per language.
- **Explain simpler:** a persistent control with three levels (normal → simple → very simple). It regenerates the response with a reading-level instruction, shorter sentences, everyday analogies and no jargon. There's also "tell me more."
- **Captions** are always generated and displayed when the flag is on, with word highlighting.
- **Adjustable text size and speech speed**, as in-app controls with persisted values, independent of the system settings.
- **Raw vs accessible toggle (debug):** shows what the raw LLM output would have looked like next to the adapted version. It's cheap to build and makes the accessibility work visible.

### 6.3 Dietary answers (careful framing)
"Vegetarian / Jain / nut-free" answers combine three sources:
1. The menu's own veg/non-veg marks (green/red dot, detected by vision)
2. Inferred ingredients from the dish name and description
3. Rules (Jain = no onion, garlic, root vegetables, and so on)

Each answer includes a confidence level, and for Jain and allergen questions it always says "typically contains... please confirm with the restaurant," and offers a one-tap *"Ask the waiter about this"* that speaks the question to staff. This keeps us within the "no medical advice" boundary while being useful. Nut-free is treated conservatively: unknown means "can't confirm."

---

## 7. Dialogue and tool design

### 7.1 State machine
`Welcome → LanguageSelect → MenuCapture → MenuReview → Browse/Ask → Cart → ConfirmOrder → Ordered → Service (waiter/bill/split/feedback) → Done`. Any state can interrupt to "repeat", "go back", "change language", "slower", "simpler", "help."

### 7.2 LLM tools (function calling)
`filter_menu(dietary, spice_max, price_max, section)`, `explain_item(item_id, level)`, `compare_items(ids)`, `add_to_order(item_id|fuzzy_name, qty, notes)`, `remove_from_order`, `read_order`, `confirm_order`, `request_waiter(reason)`, `request_bill`, `split_bill(mode, n|assignments)`, `submit_feedback(text, sentiment)`, `set_preference(speed|text_size|language|captions)`, `ask_staff_question(text)`.

The LLM picks the tool and arguments, and `ToolExecutor` mutates state. `FuzzyMatcher` resolves names against the real menu and returns ambiguity back to the dialogue as candidates.

### 7.3 Prompt skeleton
- **System:** role, the output JSON schema, the active feature flags (reading level, brevity), the language policy (reply in English, translation-friendly: short sentences, no idioms, no slang), "never invent menu items or prices," and "use tools for all state changes."
- **Context injected each turn:** compact menu JSON, current order, last 6 turns, and dietary preferences.
- **Placeholder rule:** refer to dishes as `⟦id⟧`-style tokens that the app swaps to transliterated names after translation.

### 7.4 Bill and split
`BillCalculator` handles items, GST/service-charge lines (user-configurable, since menus often say "taxes extra"), and the total. Split modes are equal across N, by-item assignment ("I had the biryani"), and custom. Output is a spoken breakdown per person and an optional UPI deep link. No real payment.

### 7.5 Feedback and complaints
User speaks → ASR → LLM structures it (topic, severity, summary) → translated into the staff language → shown on a large card and optionally spoken to the staff. Offer "send now" and "read it back to me first."

---

## 8. Graceful failure (10%, and it also feeds the other criteria)

### 8.1 Misrecognition
- **Confirm before consequence:** orders, quantities, bill splits and complaints always get an explicit read-back. Read-back and confirmation happen in the user's language.
- **Heuristic checks** (Bhashini doesn't reliably return confidence): empty or very short transcript, repeated tokens, script/language mismatch (use Bhashini language detection if available), and no fuzzy match to a known menu item.
- **Back-translation check** for high-stakes utterances: translate the English interpretation back and confirm it.
- **Candidate chips** instead of blind failure. "I heard: *butter naan*. Right?" with Yes / No / Repeat.
- **Escalation ladder:** retry → candidates → phrase board/typing.

### 8.2 Network and service failures
- `NetworkMonitor` (ConnectivityManager callbacks), plus actual call outcomes, since "connected" doesn't mean "working."
- Retry with backoff, then a circuit breaker.
- **Never lose state.** Session, menu and order persist in Room and survive drops and process death.
- **Queue and resume:** a recording made during an outage is kept and retried, and the user is told so.
- Every failure is announced three ways: speech, big visual banner, haptic.

### 8.3 Degraded-mode fallbacks (labeled, not a replacement for Bhashini)
- On-device `SpeechRecognizer` (offline packs, where available) for ASR
- Android system TTS for speech output
- ML Kit OCR for menu text
- Rule-based dietary hints from keywords

The UI says: "Offline mode, quality is reduced."

### 8.4 Offline and low-bandwidth bonus
- **Pre-generated Bhashini TTS audio** for all static phrases (welcome, prompts, errors, phrase-board lines) in all 3 languages, bundled in `assets/`, so core flows work with zero network.
- Compressed audio uploads, response caching (translation and TTS cache keyed by text+lang), and a light-data mode that skips non-essential calls.

---

## 9. Rubric-to-feature map

| Criterion (weight) | Where we win it |
|---|---|
| Accessibility of outputs (25%) | Structured response + Output Adapter, table/code narration, explain-simpler, captions, speed and text controls, raw-vs-adapted toggle |
| Depth of 3 conditions (20%) | Four conditions, each with 4–5 concrete features and inference signals |
| Bhashini + language quality (20%) | Real S2S with all 3 languages, cross-language mode, number verbalization, transliterated dish names, capability probing |
| User understanding and impact (15%) | Real-world journey (menu → bill), waiter-bridge feature, India-specific details (Jain, veg dots, GST, UPI, Hinglish menus), quick validation with real users |
| Technical quality and graceful failure (10%) | Layered architecture, tool-based deterministic state, retry/circuit breaker, offline assets, state persistence |
| Presentation/docs/demo (10%) | You're skipping this, but a 20-minute README (setup, keys, dependency declaration) is cheap insurance |
| Bonus (up to 5) | Validation with real users, offline/low-bandwidth, extra languages via config, >3 conditions |

For real-user validation, even 2–3 sessions with visually impaired or older users, with notes on what broke, is worth far more than polish. Ask the organizers or a local accessibility cell for contacts.

---

## 10. Testing plan

**Test assets (collect in the first hour):** 6–8 menu photos covering a clean English menu, a Hinglish menu, a Telugu-script menu, a glossy low-light photo, a multi-column layout, a handwritten specials board, and a menu with veg/non-veg dots.

**Checks**
- **Per language (en/hi/te):** full S2S loop, the ordering flow, number and price speech, and dish-name pronunciation.
- **Cross-language:** speak Telugu, hear Hindi, and the reverse.
- **TalkBack pass:** complete the whole journey eyes-closed, and check the double-speech handling.
- **Accessibility Scanner:** run it on every screen.
- **Failure drills:** airplane mode mid-turn, throttled network, a mumbled or noisy utterance, a wrong-language utterance, an expired Bhashini key (force 401), and the app being killed mid-order.
- **Condition drills:** use the debug panel to simulate each signal set and confirm the right flags flip, announce, and undo.
- **Latency log:** record per-stage timings. Target under 6–8 s per full turn, with first audio under about 4 s thanks to sentence-chunked TTS and filler speech ("one moment").

---

## 11. 12-hour execution plan

| Hours | Goal | Exit criteria |
|---|---|---|
| 0–1 | Setup and Bhashini verification | Project builds and runs on phone, Bhashini checklist (3.5) done, keys in `local.properties`, git initialized |
| 1–3 | **The spine: S2S loop** | Speak Hindi → hear a response in Telugu/English via real Bhashini, with captions and an error-free retry |
| 3–5 | Menu pipeline | Photo → structured menu → layout description → sectioned read-aloud in 3 languages |
| 5–7 | Dialogue and service | Q&A (dietary, explain, compare), order with read-back, waiter call, bill, split, feedback |
| 7–9 | Accessibility layer | Output Adapter, explain-simpler, caption sync, text/speed controls, phrase board, tolerant matching, waiter conversation mode |
| 9–10.5 | Inference engine and failure handling | Signals → flags with announcements and undo, retry/breaker, offline assets, state persistence |
| 10.5–12 | Test, fix, freeze | Failure drills, TalkBack pass, bug bash, release APK installed on the phone |

**Feature freeze at hour 10.** If you fall behind, cut in this order:
1. Streaming/live waiter captions → chunked captions only
2. UPI deep link
3. Extra languages
4. By-item bill split → equal split only
5. Raw-vs-adapted toggle
6. Bhashini OCR second opinion

**Never cut:** the S2S spine, the visual-impairment flow, confirm-before-order, graceful failure, and captions.

---

## 12. Working with Antigravity, Android Studio and your M1 Pro

**Setup**
- One project folder opened in both Antigravity and Android Studio. Android Studio picks up external file changes, and **File → Sync Project with Gradle Files** after build-file edits.
- Use Android Studio's bundled JDK 17 (JBR). Set `JAVA_HOME` so terminal builds in Antigravity use the same one.
- Enable USB debugging on the phone and run `adb devices` to confirm it's listed.
- Android's arm64 tooling runs natively on Apple Silicon, so builds are fast.

**Faster loop:** let the agent run builds itself. From the project root, `./gradlew assembleDebug` checks it compiles, `./gradlew installDebug` installs on the connected phone, and `adb logcat | grep Thali` streams logs. Use Android Studio mainly for Run, the Layout Inspector and the profiler.

**Agent discipline (this is how you avoid a vibe-coded mess)**
1. Put this document in the repo as context, plus a short rules file: pinned versions, package layout, "no new dependency without telling me," "compile before you finish," and "no hardcoded keys."
2. **Vertical slices, one prompt each**, in the order of the plan above. For example: "Implement `BhashiniConfigManager` and `AsrNmtClient` per Section 3, with unit tests against recorded JSON fixtures. Don't touch UI."
3. Define interfaces first (`LlmClient`, `BhashiniClient`, `SpeechPlayer`), then implementations, so the agent can stub and test independently.
4. **Commit after every working slice.** Agents sometimes break working code, and a one-command rollback is your safety net.
5. Have the agent write tests for the pure logic: number verbalizer, fuzzy matcher, bill calculator, inference engine, and response parsers. These are the pieces most likely to silently break.
6. Review agent output for the known traps: hardcoded serviceIds, blocking calls on the main thread, missing `contentDescription`s, text sized in dp instead of sp, and state held only in a Composable.

---

## 13. Risks and mitigations

| Risk | Mitigation |
|---|---|
| Bhashini quota/latency/downtime during demo | Organizer-provided keys, caching, pre-bundled TTS, sentence-chunked playback, hidden recorded-response mode |
| Double translation degrades meaning | Short translation-friendly English from the LLM, protected dish names, back-translation on high-stakes turns |
| Indic TTS mispronounces numbers and names | Own number verbalizer, transliterated dish names |
| Menu OCR failure on stylized menus | Vision LLM primary, quality coaching, retake prompts, ML Kit fallback |
| Wrong dietary or allergen info | Confidence labels, conservative nut-free handling, always offer to ask the waiter |
| Misinference of a condition | Reversible, announced, overridable, local-only |
| Build breakage from agent-driven changes | Pinned versions, commit-per-slice, compile-before-done rule |
| Scope creep | Hour-10 freeze and the cut list |

---

## 14. Decisions I need from you to tighten the spec
1. Confirm Hindi + Telugu, or choose different languages.
2. Do you have Bhashini credentials yet, and are organizers providing event keys?
3. Which LLM provider do you want behind `LlmClient`: Gemini, Claude, or both?
4. Which phone model and Android version are you testing on?

Once you have Bhashini credentials and the 30-minute checklist results, send me any surprises (a missing TTS language, an auth error, odd response shapes). I can then give you exact Kotlin request/response models and ready-to-paste Antigravity prompts for each slice.

Your Bhashini API details above come from my training knowledge, not live research. If you tap the "Turn on web search" button, I can check the current docs and redo the Bhashini section. In particular I would confirm the endpoints, the pipeline ID, the credential flow and which languages and tasks are supported.