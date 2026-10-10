# SARVAAI: ACCESSIBLE RESTAURANT ORDERING ASSISTANT
## Master Engineering Specification & End-to-End Phased Implementation Roadmap
### Megathon 2026 — Accessible AI for All (IIIT Hyderabad)

---

## 1. Executive Summary & Architectural Mandate

### 1.1 Project Identity & Core Mission
**SarvaAI** (formerly referred to as Thali / SvaraAI) is a production-grade, voice-first multimodal restaurant ordering assistant engineered specifically for Indian dining environments. Built natively in Kotlin and Jetpack Compose for Android 16 (API Level 35/36), SarvaAI eliminates the accessibility barriers inherent in standard AI assistants and digital ordering platforms for users with **Visual**, **Hearing**, **Speech**, and **Ageing** impairments across **English**, **Hindi (हिन्दी)**, and **Telugu (తెలుగు)**.

Unlike typical hackathon prototypes that act as thin wrappers around raw Large Language Model (LLM) chat completions, SarvaAI is built on five strict architectural invariants:
1. **Outputs, Not Just Interfaces (Rubric Weight: 25%)**: Raw LLM markdown or streaming tokens are strictly forbidden from reaching the UI or audio layer. All responses are emitted as strongly-typed domain objects passed through an **Output Adapter** that synthesizes spoken prose, synchronized word-highlighted captions, accessible tables, and screen-reader headings tailored to user profiles.
2. **Features, Not Modes (Rubric Weight: 20%)**: Rather than forcing users into monolithic, rigid "accessibility modes", SarvaAI implements granular, reversible adaptive features governed by an on-device **Condition Inference Engine** that continuously evaluates behavioral and system signals.
3. **Bhashini as the Multilingual Spine (Rubric Weight: 20%)**: Live Speech-to-Speech (S2S) processing is driven by Bhashini / AnuvaadHub endpoints for Automatic Speech Recognition (ASR), Neural Machine Translation (NMT), and Text-to-Speech (TTS), augmented with client-side audio caching, pitch-preserving speed regulation, and transliteration for Indic culinary nomenclature.
4. **Deterministic Actions, Probabilistic Language (Rubric Weight: 10%)**: The LLM serves solely as an interpreter, explainer, and intent router. Financial computations (GST, service charge, bill splits), cart mutations, dietary conflict checks, and order confirmations are strictly executed by deterministic Kotlin domain code.
5. **Zero-Visual-Dependency Guarantee**: The entire customer journey—from opening the app, framing a printed physical menu, exploring culinary offerings, inquiring about Jain/vegetarian restrictions, placing an order, summoning waitstaff, and splitting the final bill—can be completed completely eyes-closed using audio coaching, earcons, haptic cues, and TalkBack semantics.

---

## 2. Hardware Environment, Toolchain & Device Profile

### 2.1 Host Development Machine (Apple M1 Pro)
- **Architecture**: Apple Silicon ARM64 (10-core CPU, 16-core GPU, 16-core Neural Engine).
- **Operating System**: macOS Sequoia / Sonoma with native ZSH shell.
- **Java Runtime**: JDK 17 (bundled JetBrains Runtime `jbr-17` inside Android Studio) pinned to ensure reproducible Gradle daemon performance without architecture translation penalties.
- **Android Studio**: Android Studio Ladybug (2024.2.1+) / Koala Feature Drop, optimized for native ARM64 Gradle compilation.
- **Build System**: Gradle 8.7+ with Kotlin 2.0.20+ and Jetpack Compose Compiler plugin.

### 2.2 Physical Testbed Device (realme P3 Pro 5G)
- **Hardware Platform**: Qualcomm Snapdragon 7s Gen 2 / MediaTek Dimensity platform with 8 GB Physical LPDDR4X/LPDDR5 RAM + UFS 3.1 storage.
- **Display**: 6.7-inch 120Hz Curved AMOLED with dynamic refresh rate and HDR10+ support.
- **Operating System**: realme UI 6.0 / Android 16 (API 35/36 Preview / Stable).
- **Audio & Haptic Hardware**: Stereo dual speakers, Dual-mic noise cancellation array, O-Haptics linear X-axis vibration motor.
- **Testing Constraints & Real-World Nuances**:
  - *Android 16 Background Process Limitations*: Strict Phantom Process Killer and aggressive ColorOS/Realme UI battery management require foreground service audio playback and proper wake-lock / coroutine lifecycle scoping.
  - *CameraX Level 3 / Full HAL*: CameraX vendor extensions and ultra-fast autofocus require explicit exposure compensation and tap-to-focus stabilization during low-light restaurant menu scanning.
  - *Accessibility Services*: TalkBack 14.1+, High Contrast Text toggle, System Font Scale (1.0x to 2.0x), and Live Caption support must be actively validated on the physical hardware.

### 2.3 Remote Version Control & Git Strategy
- **Repository URL**: `https://github.com/Aditya-Lingam-9000/SarvaAI`
- **Primary Branch**: `main`
- **Commit Cadence**: Every single completed slice, architecture refinement, and tested phase checkpoint is committed with semantic messages and immediately pushed to origin.
- **Credential Sanitization**: Strictly zero API keys, tokens, or personal identifiers in version control. All credentials reside in `local.properties` or environment variables, surfaced exclusively via generated `BuildConfig`.

---

## 3. High-Level System Architecture & Package Layout

```
                  ┌────────────────────────────────────────────────────────┐
                  │                 Physical Realme P3 Pro                 │
                  │             (Android 16, TalkBack, O-Haptics)          │
                  └──────────────────────────┬─────────────────────────────┘
                                             │ User Touch / Mic / Camera
                                             ▼
┌───────────────────────────────────────────────────────────────────────────────────────────────┐
│                                  PRESENTATION & UI LAYER                                     │
│  - Compose Screens: Splash, LanguageSelect, CameraScan, MenuBrowser, Cart, WaiterBridge, Bill │
│  - Accessible Primitives: AccessibleCard, LiveCaptionBar, LargeTouchTarget (48-96dp)          │
│  - Semantic Hierarchy: Heading, LiveRegion, ContentDescription, DoubleSpeechSuppressor         │
└───────────────┬───────────────────────────────────────────────────────────────┬───────────────┘
                │ StateFlow / Events                                            │ UI State
                ▼                                                               ▼
┌───────────────────────────────────────────────┐   ┌───────────────────────────────────────────┐
│            DIALOGUE MANAGER (MVI)             │   │        OUTPUT ADAPTER & ACCESSIBILITY     │
│  - Unidirectional State Flow                  │   │  - Spoken sentence chunking (<20 words)   │
│  - Intent Router (Order, Q&A, Staff, Bill)    │   │  - Number / Currency verbalizer (hi/te/en)│
│  - Tool Calling & State Mutator               │   │  - Dish name transliteration protection   │
│  - Multi-Turn Conversation Memory             │   │  - Explain-Simpler (3 tiers) generator    │
└───────────────┬───────────────────────────────┘   └───────────────────▲───────────────────────┘
                │                                                       │ Formatted Output
                ▼                                                       │
┌───────────────────────────────────────────────┐   ┌───────────────────┴───────────────────────┐
│              GROQ LLM CLIENT                  │   │        CONDITION INFERENCE ENGINE         │
│  - Llama 3.3 70B Versatile (Sub-second Q&A)   │   │  - Signal Collector: TalkBack, FontScale, │
│  - Llama 3.2 Vision (Menu OCR & Extraction)   │   │    Volume, ASR confidence, Hesitations    │
│  - Strict JSON Schema Validation              │   │  - Weighted hysteresis scoring model      │
│  - Tool Definition & Schema Enforcement       │   │  - Dynamic, reversible feature flags      │
└───────────────┬───────────────────────────────┘   └───────────────────────────────────────────┘
                │
                ▼
┌───────────────────────────────────────────────────────────────────────────────────────────────┐
│                                  BHASHINI MULTILINGUAL SPINE                                  │
│  - Pipeline Config Manager: Service ID caching, auth resolution, capability matrix probing     │
│  - ASR Engine: 16kHz mono WAV recorder, energy VAD, Base64 chunked streaming                   │
│  - NMT Engine: IndicTrans2 (te->en, hi->en, en->te, en->hi, cross-language hi<->te)           │
│  - TTS Engine: Indic speech synthesis, ExoPlayer speed control (0.8x-1.5x), Pitch preservation│
└───────────────┬───────────────────────────────────────────────────────────────┬───────────────┘
                │ Room Entities / DAOs                                          │ Preferences
                ▼                                                               ▼
┌───────────────────────────────────────────────┐   ┌───────────────────────────────────────────┐
│                 ROOM DATABASE                 │   │            DATASTORE / ASSETS             │
│  - MenuEntity, MenuItemEntity, SectionEntity  │   │  - Pre-bundled Bhashini static audio clips│
│  - CartItemEntity, OrderAuditEntity           │   │  - User language & accessibility prefs    │
│  - ResponseCacheEntity (Offline NMT / TTS)    │   │  - Earcon sound cues (WAV / OGG)          │
└───────────────────────────────────────────────┘   └───────────────────────────────────────────┘
```

### 3.1 Project Package Structure (`com.sarvaai.app`)
```
com.sarvaai.app/
├── SarvaApplication.kt               # Application entry point, Koin/Manual DI initializers
├── core/
│   ├── dispatchers/                  # Coroutine dispatchers provider (IO, Default, Main)
│   ├── network/                      # OkHttpClient factory, HTTP/2 pooling, Logging
│   ├── result/                       # Result<T>, AppError taxonomy, Domain exceptions
│   └── telemetry/                    # Latency tracer, Step-by-step latency profiler
├── bhashini/
│   ├── api/                          # Retrofit / OkHttp service declarations
│   ├── auth/                         # BhashiniConfigManager, Token caching, Refresh logic
│   ├── matrix/                       # CapabilityMatrix, Language pairing probe
│   ├── models/                       # Pipeline request/response DTOs, Audio payload models
│   ├── asr/                          # BhashiniAsrClient, Audio WAV base64 encoder
│   ├── nmt/                          # BhashiniNmtClient, Transliteration client
│   └── tts/                          # BhashiniTtsClient, Audio stream parser
├── llm/
│   ├── api/                          # GroqApiClient, ChatCompletion API interfaces
│   ├── client/                       # LlmClient contract, GroqLlmClient implementation
│   ├── models/                       # StructuredAiResponse, ToolDefinitions, ChatMessage
│   ├── prompts/                      # SystemPromptProvider, MenuExtractionPrompts
│   └── parser/                       # JsonSchemaValidator, OutputSanitizer
├── audio/
│   ├── recorder/                     # AudioRecorder, AudioRecord 16kHz mono PCM stream
│   ├── vad/                          # EnergyVad, Speech endpointing, Silence detector
│   ├── player/                       # SpeechPlayer, Media3 ExoPlayer, PlaybackParams
│   ├── cues/                         # EarconManager, Sound cues for blind accessibility
│   └── haptics/                      # HapticFeedbackEngine, O-Haptics pattern vibration
├── menu/
│   ├── camera/                       # CameraXManager, ImageAnalysis, Tap-to-focus
│   ├── analyzer/                     # MenuQualityAnalyzer (Luma, Blur, Text Bounding Box)
│   ├── coaching/                     # AuditoryCoachingEngine ("move up", "too dark")
│   ├── extractor/                    # MenuExtractor, Vision LLM orchestrator
│   ├── models/                       # Menu, MenuSection, MenuItem, DietaryTag, Price
│   └── repository/                   # MenuRepository, MenuRoomDatabase integration
├── dialogue/
│   ├── state/                        # DialogueState (MVI), DialogueEvent, DialogueEffect
│   ├── manager/                      # DialogueManager, Finite State Machine orchestrator
│   ├── tools/                        # ToolExecutor (FilterMenu, ExplainItem, OrderTool)
│   ├── matching/                     # FuzzyMatcher, Indic phonetic normalizer
│   └── order/                        # OrderManager, CartRepository, OrderConfirmation
├── adapt/
│   ├── signals/                      # SignalCollector (TalkBack, Captions, Volume, Font)
│   ├── inference/                    # ProfileInferenceEngine, Hysteresis weighting model
│   ├── flags/                        # FeatureFlags, AdaptiveState, Reversible overrides
│   ├── adapter/                      # OutputAdapter, SpokenTextChunker, TableNarrator
│   ├── verbalizer/                   # NumberVerbalizer (hi, te, en currency/quantities)
│   └── transliteration/              # IndicDishTransliterationProtector (`⟦id⟧` tokens)
├── service/
│   ├── waiter/                       # WaiterBridgeController, DualViewSync, StaffTTS
│   ├── bill/                         # BillCalculator, GstTaxEngine, SplitBillEngine
│   └── upi/                          # UpiIntentGenerator, QRBuilder, PaymentDeepLink
├── failure/
│   ├── network/                      # NetworkMonitor, ConnectivityManager callbacks
│   ├── breaker/                      # CircuitBreaker, Exponential backoff retry policy
│   ├── offline/                      # OfflineFallbackManager, Android System TTS fallback
│   └── persistence/                  # OfflineQueueRepository, PendingTurnStore
├── data/
│   ├── db/                           # AppDatabase, Room migrations
│   ├── dao/                          # MenuDao, OrderDao, CacheDao
│   ├── entities/                     # MenuEntity, OrderEntity, AudioCacheEntity
│   └── datastore/                    # UserPreferencesDataStore, FlagOverridesDataStore
└── ui/
    ├── theme/                        # Color, Typography (Dynamic 200%), Shape, Contrast
    ├── components/                   # AccessibleButton, LiveCaptions, LargeCard, Dialogs
    ├── navigation/                   # Screen destinations, NavHost, BackHandler
    └── screens/
        ├── splash/                   # SplashScreen, Hardware verification
        ├── language/                 # LanguageSelectionScreen (Audio-first)
        ├── camera/                   # MenuCameraScreen, Live audio coaching overlay
        ├── browser/                  # MenuBrowserScreen, TalkBack layout narrator
        ├── detail/                   # DishDetailScreen, Explain-simpler toggle
        ├── cart/                     # CartScreen, Read-back verification modal
        ├── waiter/                   # WaiterBridgeScreen, High-contrast staff cards
        ├── bill/                     # BillSplitScreen, Spoken share summary
        └── debug/                    # DebugSignalPanel, Raw vs Accessible comparative view
```

---

## 4. Master 15-Phase Execution Plan

The end-to-end development is partitioned into **15 structured, sequential, uncompromising phases**. Execution proceeds strictly phase-by-phase. No phase may commence until the preceding phase has been built, tested, and verified on the physical **realme P3 Pro (Android 16)** with code committed and pushed to `https://github.com/Aditya-Lingam-9000/SarvaAI`.

```
Phase 0: Environment, Toolchain, Hardware & Baseline Verification
   │
Phase 1: Architecture Core, Design System & Accessible Domain Foundations
   │
Phase 2: Bhashini & AnuvaadHub Audio Ingestion Pipeline (Spine - Part 1)
   │
Phase 3: Real-Time Audio Playback & Synchronized Captions (Spine - Part 2)
   │
Phase 4: Groq LLM Intelligence Engine & Structured Output Adapter
   │
Phase 5: CameraX Menu Capture with Auditory Guidance
   │
Phase 6: Menu Modeling, Section Narration & Voice-First Menu Navigation
   │
Phase 7: Multi-Turn Conversational Dialogue Manager & Intent Routing
   │
Phase 8: Dietary Reasoning, Allergen Intelligence & Food Explanation
   │
Phase 9: Deterministic Cart, Read-Back & Order Confirmation Engine
   │
Phase 10: Waiter Interaction Bridge & Dual-Screen Staff Mode
   │
Phase 11: Bill Calculation, Splitting Engine & UPI Payment Intent
   │
Phase 12: Condition Inference Engine & Dynamic Adaptation
   │
Phase 13: Resilient Offline Fallbacks, Degraded Mode & Pre-Bundled Assets
   │
Phase 14: End-to-End Stress Testing, Accessibility Audits & Physical Device Polish
```

---

# Detailed Phase Specifications

```
================================================================================
PHASE 0: ENVIRONMENT, TOOLCHAIN, HARDWARE & BASELINE VERIFICATION
================================================================================
```

### 1. Objective & Strategic Goal
Establish a bulletproof, reproducible build and execution environment on the M1 Pro host machine, configure local repository tracking with remote origin `https://github.com/Aditya-Lingam-9000/SarvaAI`, establish USB debugging and physical device connectivity with the **realme P3 Pro (Android 16)**, generate the root project structure with pinned dependency catalogs, configure `local.properties` secrets segregation, and execute a verified baseline build installed on the physical handset with real-time logcat confirmation.

### 2. Prerequisites & Entry Criteria
- Apple M1 Pro Mac running macOS with Terminal / ZSH.
- Android Studio Ladybug / Koala installed.
- Android SDK Platforms 34, 35, and 36 (Android 16 Preview/Vanilla Ice Cream / Baklava) installed in Android SDK Manager.
- Realme P3 Pro with USB Debugging enabled in Developer Options.
- USB-C to USB-C / USB-A cable connected with "Always allow USB debugging from this computer" authorized.

### 3. Detailed Technical Architecture & Component Design
1. **Repository & Secret Management**:
   - Establish `.gitignore` comprehensive for Android, macOS, Gradle, and IDE metadata (`.DS_Store`, `local.properties`, `.gradle/`, `build/`, `*.apk`, `*.hprof`).
   - Create a clean `local.properties.template` documenting required keys:
     - `GROQ_API_KEY`: Groq Cloud inference authorization token.
     - `BHASHINI_USER_ID`: AnuvaadHub / Bhashini User ID.
     - `BHASHINI_API_KEY`: AnuvaadHub / Bhashini ULCA API Key.
     - `BHASHINI_PIPELINE_ID`: Megathon-provided or default pipeline ID (`64392f96daac500b55c543cd`).
     - `BHASHINI_INFERENCE_KEY`: Short-lived or event-provided Dhruva inference API key.
2. **Gradle Version Catalog (`gradle/libs.versions.toml`)**:
   - Pinned Gradle plugin versions: AGP 8.7.0, Kotlin 2.0.20, Compose Compiler 2.0.20.
   - Core libraries: AndroidX Core KTX (1.13.1), Lifecycle Runtime KTX (2.8.6), Activity Compose (1.9.2).
   - Jetpack Compose BOM (2024.09.03 or latest stable) for Material 3, Navigation Compose (2.8.2).
   - CameraX (1.3.4), ML Kit Text Recognition (16.0.1).
   - Media3 ExoPlayer (1.4.1), OkHttp (4.12.0), Kotlinx Serialization JSON (1.7.3).
   - Room (2.6.1), DataStore Preferences (1.1.1).
3. **Android 16 Manifest & Target Configuration**:
   - `compileSdk = 35` (or 36 for Android 16 SDK).
   - `minSdk = 26` (Android 8.0 Oreo, covering 99.4% of active devices while providing full AudioRecord / O-Haptics API support).
   - `targetSdk = 35` (compatible with Android 16 runtime security models).
   - Permissions declared: `RECORD_AUDIO`, `CAMERA`, `INTERNET`, `ACCESS_NETWORK_STATE`, `VIBRATE`.

### 4. Step-by-Step Implementation Instructions
1. Initialize local Git repository tracking and branch configuration:
   ```bash
   git branch -M main
   git remote set-url origin https://github.com/Aditya-Lingam-9000/SarvaAI.git
   ```
2. Verify Java 17 and Android SDK environment in host shell:
   ```bash
   java -version
   echo $JAVA_HOME
   adb devices -l
   ```
3. Initialize the native Android Gradle project structure:
   - Root `settings.gradle.kts`
   - Root `build.gradle.kts`
   - `gradle/libs.versions.toml`
   - `app/build.gradle.kts`
   - `app/src/main/AndroidManifest.xml`
   - `app/src/main/java/com/sarvaai/app/MainActivity.kt`
   - `app/src/main/java/com/sarvaai/app/SarvaApplication.kt`
4. Configure BuildConfig fields in `app/build.gradle.kts` reading securely from `local.properties`.
5. Connect Realme P3 Pro via USB, verify ADB authorization:
   ```bash
   adb devices
   # Expected output:
   # List of devices attached
   # <device_id>    device
   ```

### 5. Physical Device Testing & Validation Protocol on realme P3 Pro (Android 16)
- Execute Gradle build and installation:
  ```bash
  ./gradlew assembleDebug
  ./gradlew installDebug
  ```
- Launch application on the realme P3 Pro:
  ```bash
  adb shell am start -n com.sarvaai.app/.MainActivity
  ```
- Confirm baseline UI displays with theme colors and verified Android 16 runtime environment.
- Stream logcat filtered by package tag:
  ```bash
  adb logcat | grep SarvaAI
  ```

### 6. Failure Modes, Edge Cases & Recovery Protocols
- *Issue*: `adb devices` shows `unauthorized`.
  *Recovery*: Toggle USB Debugging on realme P3 Pro, revoke USB debugging authorizations, reconnect cable, and accept authorization prompt with RSA fingerprint.
- *Issue*: Gradle daemon fails due to JDK version mismatch on M1 Pro.
  *Recovery*: Point `org.gradle.java.home` in `gradle.properties` to `/Applications/Android Studio.app/Contents/jbr/Contents/Home`.
- *Issue*: TargetSdk 35/36 enforces 16KB page size compatibility or strict edge-to-edge layout enforcement.
  *Recovery*: Enable `enableEdgeToEdge()` in `MainActivity.kt` and use system window insets.

### 7. Git Commit & Push Checkpoint
```bash
git add .
git commit -m "chore(phase0): initialize android baseline project, pinned dependencies, local.properties template and verify physical realme p3 pro deployment"
git push -u origin main
```

### 8. Exit Criteria & Sign-Off Checklist
- [ ] Git remote `https://github.com/Aditya-Lingam-9000/SarvaAI` synchronized on `main`.
- [ ] Project compiles with zero warnings or errors via `./gradlew assembleDebug`.
- [ ] Baseline APK successfully installed on physical realme P3 Pro (Android 16) via `./gradlew installDebug`.
- [ ] App launches smoothly on physical device and emits `SarvaAI: Baseline initialized on Android 16` in logcat.
- [ ] Zero secrets in Git tracking; `local.properties` ignored.

---

```
================================================================================
PHASE 1: ARCHITECTURE CORE, DESIGN SYSTEM & ACCESSIBLE DOMAIN FOUNDATIONS
================================================================================
```

### 1. Objective & Strategic Goal
Architect the core foundation of SarvaAI: unidirectional MVI state infrastructure, a high-contrast Material 3 design system with dynamic text scaling up to 200%, semantic accessible components with 48dp–96dp touch targets, Room Database schema for menus and orders, encrypted DataStore for user preferences, and a fine-tuned X-axis O-Haptics vibration engine.

### 2. Prerequisites & Entry Criteria
- Phase 0 completed and verified on realme P3 Pro.
- Device display settings configured to test font scaling (1.0x to 2.0x).

### 3. Detailed Technical Architecture & Component Design
1. **Unidirectional MVI Architecture Pattern**:
   ```kotlin
   interface UiState
   interface UiEvent
   interface UiEffect
   abstract class BaseViewModel<S : UiState, E : UiEvent, F : UiEffect>(initialState: S) : ViewModel() {
       private val _uiState = MutableStateFlow(initialState)
       val uiState: StateFlow<S> = _uiState.asStateFlow()
       
       private val _effect = Channel<F>(Channel.BUFFERED)
       val effect: Flow<F> = _effect.receiveAsFlow()
       
       abstract fun onEvent(event: E)
       protected fun setState(reducer: S.() -> S) { _uiState.update(reducer) }
       protected fun sendEffect(builder: () -> F) { viewModelScope.launch { _effect.send(builder()) } }
   }
   ```
2. **Accessible Material 3 Design System**:
   - **Contrast Compliance**: WCAG AAA (7:1 contrast ratio) for critical text and interactive elements.
   - **Dynamic Typography**: Custom `SarvaTypography` utilizing system font scaling with explicit `sp` units and maximum bounds to prevent truncation or overlap at 200% font scale.
   - **Color Palettes**:
     - Standard Dark Palette: Deep charcoal (`#121212`), high-contrast surface (`#1E1E1E`), saffron accent (`#FF9933`), emerald veg green (`#00875A`), crimson non-veg (`#DE350B`).
     - High Contrast Mode: Pure black (`#000000`), pure white text (`#FFFFFF`), bright yellow highlights (`#FFD700`).
3. **Room Database 2.6+ Persistence**:
   - Database: `AppDatabase` with Room KSP.
   - Entities:
     - `MenuEntity`: Menu ID, restaurant name, capture timestamp, page count.
     - `MenuItemEntity`: Item ID, section ID, canonical English name, Indic script name, transliteration, description, price (in paise / int to prevent floating-point errors), dietary flags (veg, non-veg, Jain, nut-free, vegan), spicy level (0–3), chef special boolean.
     - `CartItemEntity`: Unique cart entry ID, item ID, quantity, special instructions, added timestamp.
     - `OrderAuditEntity`: Order ID, items JSON, subtotal, tax breakdown, final total, status, timestamp.
4. **O-Haptics Vibration Engine (`HapticFeedbackEngine`)**:
   - Integration with Android `Vibrator` / `VibratorManager` using `VibrationEffect.createWaveform` and `VibrationEffect.Composition` (primitive clicks, ticks, and thuds) specifically tuned for realme P3 Pro's X-axis linear motor.

### 4. Step-by-Step Implementation Instructions
1. Create `core/result/Result.kt` and `core/result/AppError.kt` with explicit failure types.
2. Implement `ui/theme/Color.kt`, `ui/theme/Type.kt`, and `ui/theme/Theme.kt` supporting standard and AAA high-contrast modes.
3. Build accessible UI primitives in `ui/components/`:
   - `AccessibleButton`: Large touch targets (minimum 64dp height for elderly/motor-impaired users), clear focus indicators, TalkBack action labels.
   - `LiveCaptionBar`: Fixed floating caption bar with high-contrast text and real-time word highlight.
   - `AccessibleCard`: High-contrast card with semantic role, focus states, and haptic feedback on touch.
4. Set up Room Database in `data/db/` with Room DAOs (`MenuDao`, `CartDao`, `OrderDao`).
5. Build `audio/haptics/HapticFeedbackEngine.kt` supporting distinct patterns:
   - `CONFIRMATION`: Sharp double tick (100ms on, 50ms off, 100ms on).
   - `ERROR_ALERT`: Heavy buzz (250ms strong vibration).
   - `CAMERA_STABLE`: Crisp single click (30ms sharp tick).
   - `HEARING_CUE`: Pulsed tactile thud for speech turn transitions.

### 5. Physical Device Testing & Validation Protocol on realme P3 Pro (Android 16)
- Deploy build to realme P3 Pro.
- Open Settings on phone -> Accessibility -> Display size and text -> Increase Font Size to Maximum (200%).
- Launch app: Verify that UI layouts adapt gracefully without text clipping, overlapping, or broken borders.
- Enable TalkBack (Volume Up + Volume Down hold): Navigate all buttons and verify proper focus rectangles, spoken accessibility descriptions, and minimum 48dp+ hit targets.
- Test O-Haptics feedback: Tap interactive components and verify physical haptic feedback responses.

### 6. Failure Modes, Edge Cases & Recovery Protocols
- *Issue*: Text overlaps when Android 16 font scale is set to maximum 2.0x.
  *Recovery*: Wrap vertically expanding text in scrollable columns or use `Modifier.heightIn(min = 64.dp)` instead of fixed `Modifier.height(64.dp)`.
- *Issue*: Room database schema migration crash on upgrade.
  *Recovery*: Use `fallbackToDestructiveMigration()` during development builds before production release.

### 7. Git Commit & Push Checkpoint
```bash
git add .
git commit -m "feat(phase1): implement accessible design system, dynamic typography, room database entities and o-haptics vibration engine"
git push origin main
```

### 8. Exit Criteria & Sign-Off Checklist
- [ ] Accessible Theme compiles and renders on realme P3 Pro.
- [ ] Font scale 200% stress test passes without layout clipping.
- [ ] TalkBack traverses all components with valid semantic labels.
- [ ] Room Database initial table creation verified via SQLite tests.
- [ ] Physical O-Haptics vibrations felt and confirmed on realme P3 Pro.

---

```
================================================================================
PHASE 2: BHASHINI & ANUVAADHUB AUDIO INGESTION PIPELINE (SPINE - PART 1)
================================================================================
```

### 1. Objective & Strategic Goal
Construct the first half of SarvaAI's speech spine: network integration with AnuvaadHub / Bhashini Dhruva services, configuration resolution, dynamic token caching, capability matrix probing across English, Hindi, and Telugu, high-fidelity 16kHz mono 16-bit PCM microphone recording with energy-based Voice Activity Detection (VAD), chunked Base64 encoding, and end-to-end ASR + NMT pipeline execution.

### 2. Prerequisites & Entry Criteria
- Phase 1 verified on physical device.
- Bhashini / AnuvaadHub credentials available (User ID, API Key, Pipeline ID) as documented in `ProblemStatement/AnuvaadHub API Access Flow.pdf`.
- `RECORD_AUDIO` runtime permission handling implemented.

### 3. Detailed Technical Architecture & Component Design
1. **Bhashini Dynamic Two-Step Pipeline Architecture**:
   - **Step 1: Pipeline Discovery (`getModelsPipeline`)**:
     - Endpoint: `POST https://meity-auth.ulcacontrib.org/ulca/apis/v0/model/getModelsPipeline`
     - Headers: `userID: <BHASHINI_USER_ID>`, `ulcaApiKey: <BHASHINI_API_KEY>`, `Content-Type: application/json`
     - Resolves: Dynamic `callbackUrl` (typically `https://dhruva-api.bhashini.gov.in/services/inference/pipeline`), short-lived inference API key name and value, and exact `serviceId` mappings per language task.
     - Caching Strategy: Persist resolved config in-memory and in DataStore for 12 hours. Invalidate and re-fetch immediately upon HTTP 401/403 or upstream 5xx errors.
   - **Step 2: Unified Inference Execution (`computePipeline`)**:
     - Dual-Task Ingestion: ASR (User Language) -> NMT (User Language -> English) in a single atomic HTTP request.
     - Avoids multiple network hops by executing speech-to-text and translation on the server cluster in one round-trip.
2. **Audio Recording & PCM Processing Engine (`AudioRecorder`)**:
   - Format: Strict 16,000 Hz sample rate, 16-bit linear PCM, mono channel, uncompressed WAV header.
   - Buffer size: Computed via `AudioRecord.getMinBufferSize(16000, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT) * 2`.
3. **Energy-Based Voice Activity Detection (VAD)**:
   - Real-time root-mean-square (RMS) energy calculation over 20ms audio frames.
   - Dynamic thresholding: Noise floor calibration in the first 300ms.
   - Speech detection: Speech begins when RMS exceeds threshold for 150ms.
   - Endpointing: Silence detected when RMS drops below threshold for 1,200ms (standard) or 2,000ms (speech-impaired / elderly mode). Maximum utterance cap: 12 seconds.

### 4. Step-by-Step Implementation Instructions
1. Implement OkHttp networking in `core/network/OkHttpFactory.kt` with HTTP/2 support, 20-second connect/read timeouts, and certificate validation.
2. Create Bhashini DTO data classes in `bhashini/models/`:
   - `PipelineConfigRequest`, `PipelineConfigResponse`, `PipelineInferenceRequest`, `PipelineInferenceResponse`.
3. Build `bhashini/auth/BhashiniConfigManager.kt` managing discovery, serviceId caching, and automatic token refresh.
4. Build `bhashini/matrix/CapabilityMatrix.kt` testing available tasks for `{en, hi, te}` across `{asr, translation, tts, transliteration}` on first launch.
5. Implement `audio/recorder/AudioRecorder.kt` reading from `AudioRecord` on `Dispatchers.IO`.
6. Implement `audio/vad/EnergyVad.kt` calculating frame amplitude and firing speech-start and speech-end callbacks.
7. Implement `bhashini/asr/AsrNmtClient.kt` converting recorded PCM to Base64 WAV and invoking Bhashini inference.

### 5. Physical Device Testing & Validation Protocol on realme P3 Pro (Android 16)
- Grant microphone permission on realme P3 Pro.
- Execute live voice test:
  - Speak in Hindi: *"मुझे दो बटर नान और पनीर टिक्का चाहिए"* (Mujhe do butter naan aur paneer tikka chahiye).
  - Speak in Telugu: *"నాకు రెండు బటర్ నాన్ మరియు పనీర్ టిక్కా కావాలి"* (Naaku rendu butter naan mariyu paneer tikka kaavali).
- Verify in Logcat:
  - ASR transcript extracted correctly.
  - NMT translation emitted in clear English: *"I want two butter naan and paneer tikka."*
- Verify round-trip latency (target: < 2.5 seconds on Wi-Fi / 5G).

### 6. Failure Modes, Edge Cases & Recovery Protocols
- *Issue*: Bhashini returns HTTP 401 Unauthorized mid-session due to expired inference token.
  *Recovery*: `BhashiniConfigManager` intercepts 401, clears cached credentials, executes `getModelsPipeline`, and replays the original request automatically.
- *Issue*: Silent audio or microphone blocked by Android 16 permission or mic mute switch.
  *Recovery*: Detect zero-amplitude RMS frames, emit user-facing audio & visual alert: *"Microphone is muted or silent, please check permissions."*

### 7. Git Commit & Push Checkpoint
```bash
git add .
git commit -m "feat(phase2): implement bhashini config manager, capability matrix, 16khz pcm audio recording with vad, and asr-nmt ingestion client"
git push origin main
```

### 8. Exit Criteria & Sign-Off Checklist
- [ ] Bhashini authentication and pipeline discovery verified with live credentials.
- [ ] Capability matrix resolves valid serviceIds for Hindi, Telugu, and English.
- [ ] 16kHz mono audio recording and VAD speech endpointing confirmed on realme P3 Pro hardware.
- [ ] Live Hindi and Telugu speech converted to accurate transcripts and English translations.
- [ ] Zero audio memory leaks; `AudioRecord` cleanly released on lifecycle events.

---

```
================================================================================
PHASE 3: REAL-TIME AUDIO PLAYBACK & SYNCHRONIZED CAPTIONS (SPINE - PART 2)
================================================================================
```

### 1. Objective & Strategic Goal
Complete the second half of the speech spine: Bhashini NMT (English -> Target Language) and TTS synthesis, client-side pitch-preserving speed regulation (0.5x to 2.0x) via Media3 ExoPlayer, sentence-chunked streaming playback for minimum time-to-first-audio, TalkBack double-speech conflict suppression, distinct earcon sound cues, and a word-synchronized high-contrast live caption system.

### 2. Prerequisites & Entry Criteria
- Phase 2 completed with verified ASR + NMT ingestion.
- Media3 ExoPlayer dependencies linked in Gradle.

### 3. Detailed Technical Architecture & Component Design
1. **Sentence-Chunked Streaming Playback Engine**:
   - Rather than waiting for the entire multi-sentence response to be synthesized, split output into sentences (<180 characters each).
   - Dispatch Sentence 1 to Bhashini TTS immediately -> Begin audio playback as soon as Chunk 1 arrives (< 1.2s).
   - Concurrently synthesize Sentence 2 and Sentence 3 in the background, queuing them seamlessly in ExoPlayer's playlist.
2. **Client-Side Speed & Pitch Regulation**:
   - Server-side TTS speed parameters in Bhashini frequently produce distorted pitch or robotic artifacts.
   - Use Android Media3 `PlaybackParams.setSpeed(speed).setPitch(1.0f)` on the client side to provide crystal-clear speed options:
     - 0.8x: Gentle / Elderly accessibility default.
     - 1.0x: Standard pace.
     - 1.25x / 1.5x: Fast navigation for experienced TalkBack users.
3. **TalkBack Double-Speech Prevention Engine**:
   - Problem: When TalkBack is enabled, system accessibility announcements and Bhashini TTS audio speak over each other simultaneously, creating an unintelligible acoustic cacophony.
   - Solution: Query `AccessibilityManager.isTouchExplorationEnabled`. When Bhashini audio begins playing, temporarily mute accessibility live region announcements (`AccessibilityEvent.TYPE_ANNOUNCEMENT` suppressed). When Bhashini audio completes or user taps to interrupt, restore standard accessibility announcements.
4. **Earcons & Audio Palette**:
   - Synthesized short acoustic cues (100–250ms) to guide blind users:
     - `EARCON_LISTEN_START`: Upbeat chime (mic is open).
     - `EARCON_LISTEN_END`: Soft descending chime (speech captured, processing).
     - `EARCON_SUCCESS`: Crisp bell (order confirmed, action completed).
     - `EARCON_ERROR`: Gentle double boop (network error, retry).
5. **Synchronized Word-Highlighted Live Captions**:
   - Maintain active character/word offset mapped to ExoPlayer playback position.
   - High-contrast visual bar rendered at bottom/top of screen displaying current spoken words highlighted in bright yellow (`#FFD700`) against black background (`#000000`).

### 4. Step-by-Step Implementation Instructions
1. Build `bhashini/tts/NmtTtsClient.kt` handling translation to target language and TTS synthesis into Base64 WAV.
2. Implement `audio/player/SpeechPlayer.kt` encapsulating Media3 `ExoPlayer`, sentence queueing, and speed controls.
3. Build `audio/cues/EarconManager.kt` loading short audio cues from Android `assets/earcons/`.
4. Build `audio/player/DoubleSpeechSuppressor.kt` coordinating with Android `AccessibilityManager`.
5. Implement `ui/components/LiveCaptionBar.kt` observing `SpeechPlayer.playbackProgress` to drive real-time word highlighting.
6. Assemble the full **Speech-to-Speech (S2S) Loop**:
   - Voice Input (Hindi/Telugu) -> ASR -> NMT (English) -> Echo/Logic -> NMT (Target) -> TTS -> Playback with live captions and earcons.

### 5. Physical Device Testing & Validation Protocol on realme P3 Pro (Android 16)
- Test on realme P3 Pro speakers:
  - Speak: *"नमस्ते, क्या मुझे पानी मिल सकता है?"*
  - Verify `EARCON_LISTEN_START` plays on tap.
  - Verify `EARCON_LISTEN_END` plays when user stops talking.
  - Verify TTS audio begins playing through realme P3 Pro dual speakers with crisp quality.
  - Verify Live Caption bar displays the Hindi text with active word highlighting synced to the voice.
- Test Speed Adjustment:
  - Toggle speed to 0.8x: Verify speech slows down cleanly without pitch drop or warble.
  - Toggle speed to 1.5x: Verify speech accelerates cleanly.
- Test TalkBack Interaction:
  - Turn on TalkBack on realme P3 Pro -> Run speech turn -> Confirm TalkBack does not speak over Bhashini audio.

### 6. Failure Modes, Edge Cases & Recovery Protocols
- *Issue*: Bhashini TTS returns invalid or truncated WAV header.
  *Recovery*: Wrap audio decoder in `WavHeaderValidator`. If corrupt, fall back immediately to on-device Android `TextToSpeech` with Indic locale.
- *Issue*: User interrupts speech playback by speaking or tapping.
  *Recovery*: Instantly stop ExoPlayer via `speechPlayer.stop()`, clear queue, play `EARCON_LISTEN_START`, and open mic.

### 7. Git Commit & Push Checkpoint
```bash
git add .
git commit -m "feat(phase3): implement exoplayer speech player, sentence chunking, earcons, live captions with word sync, and talkback double-speech suppression"
git push origin main
```

### 8. Exit Criteria & Sign-Off Checklist
- [ ] End-to-end Bhashini Speech-to-Speech loop verified on physical device.
- [ ] Latency from speech end to first audio playback measured under 3.5 seconds.
- [ ] Speed regulation (0.8x, 1.0x, 1.2x) verified without audio distortion.
- [ ] Word-synchronized live captions render smoothly at 60/120fps on realme P3 Pro display.
- [ ] Zero TalkBack audio collision when TalkBack is actively running.

---

```
================================================================================
PHASE 4: GROQ LLM INTELLIGENCE ENGINE & STRUCTURED OUTPUT ADAPTER
================================================================================
```

### 1. Objective & Strategic Goal
Integrate the ultra-fast Groq Cloud API using `Llama 3.3 70B Versatile` for sub-second conversational reasoning and `Llama 3.2 11B / 90B Vision` for menu document analysis. Implement strict JSON Schema enforcement guaranteeing that the LLM emits structured domain outputs (headline, spoken, caption, table narration, actions, confidence) rather than raw conversational markdown. Build the foundational Output Adapter with number verbalization in Hindi, Telugu, and English.

### 2. Prerequisites & Entry Criteria
- Phase 3 completed with working speech loop.
- Valid `GROQ_API_KEY` configured in `local.properties`.

### 3. Detailed Technical Architecture & Component Design
1. **Groq API Client Architecture**:
   - Model for Dialogue: `llama-3.3-70b-versatile` (chosen for ultra-low token latency ~200-300ms, essential for natural voice conversations).
   - Model for Vision: `llama-3.2-11b-vision-preview` / `llama-3.2-90b-vision-preview`.
   - Temperature: 0.1 (strict, deterministic reasoning without creative hallucinations).
   - Response Format: `{"type": "json_object"}` with strict JSON schema definition.
2. **Strict Structured Output Contract (`StructuredAiResponse`)**:
   ```kotlin
   @Serializable
   data class StructuredAiResponse(
       val intent: String,                      // e.g., "explain_dish", "compare_dishes", "add_to_cart", "dietary_query"
       val headline: String,                    // Concise visual title (e.g., "Paneer Tikka vs Kadai Paneer")
       val spoken: String,                      // Natural speech prose (< 25 words per sentence, numbers spelled out, no markdown)
       val caption: String,                     // Compact visual caption text for live caption bar
       val bullets: List<String> = emptyList(), // Optional key summary points
       val table: StructuredTable? = null,      // Visual comparison table for sighted users
       val tableNarration: String? = null,      // Complete spoken prose describing the table for blind / voice users
       val simpleLevel: Int = 1,                // 1 = Standard, 2 = Simplified, 3 = Very Simple (for ageing / cognitive)
       val confidence: String = "high",         // "high", "medium", "low"
       val needsStaffConfirmation: Boolean = false, // True for ambiguous dietary / allergen items
       val actions: List<UiAction> = emptyList(),   // Interactive action buttons (e.g., "Add to Order", "Ask Waiter")
       val followupPrompt: String? = null       // Spoken conversational next step prompt
   )
   ```
3. **Number & Currency Verbalizer (`NumberVerbalizer`)**:
   - Problem: Indic TTS systems pronounce "₹250" as *"symbol rupee two five zero"* or skip it entirely.
   - Solution: Pre-process all numbers, prices, and quantities into phonetically exact words before sending text to Bhashini TTS:
     - English: "₹250" -> *"two hundred and fifty rupees"*, "2x" -> *"two plates of"*.
     - Hindi: "₹250" -> *"दो सौ पचास रुपये"*, "2x" -> *"दो प्लेट"*.
     - Telugu: "₹250" -> *"రెండు వందల యాభై రూపాయలు"*, "2x" -> *"రెండు ప్లేట్లు"*.
4. **Dish Name Protection & Transliteration (`IndicDishTransliterationProtector`)**:
   - Problem: Translating "Butter Naan" to Telugu via NMT creates literal nonsense like "వెన్న రొట్టె" (Venna Rotte), which no restaurant waiter recognizes. Conversely, feeding English letters to Telugu TTS sounds completely garbled.
   - Solution: Tokenize dish names as `⟦ITEM_42⟧` before translation. After NMT translates the sentence framing, replace tokens with the dish name **transliterated into the native script**:
     - "Butter Naan" in Telugu script: *"బటర్ నాన్"*.
     - "Paneer Butter Masala" in Hindi script: *"पनीर बटर मसाला"*.

### 4. Step-by-Step Implementation Instructions
1. Implement `llm/api/GroqApiClient.kt` using OkHttp communicating with `https://api.groq.com/openai/v1/chat/completions`.
2. Define system prompts in `llm/prompts/SystemPromptProvider.kt` enforcing the JSON output contract, concise spoken outputs, and translation-safe phrasing.
3. Build `adapt/verbalizer/NumberVerbalizer.kt` with comprehensive unit tests for English, Hindi, and Telugu number-to-words conversion.
4. Build `adapt/transliteration/IndicDishTransliterationProtector.kt`.
5. Implement `adapt/adapter/OutputAdapter.kt` transforming raw `StructuredAiResponse` into spoken audio text, visual captions, and accessibility announcements.
6. Build a **Raw vs Accessible Comparative Debug View** in the UI to visually demonstrate the transformation to judges.

### 5. Physical Device Testing & Validation Protocol on realme P3 Pro (Android 16)
- Test Groq API integration directly on the phone:
  - Send sample menu query: *"Tell me about Paneer Butter Masala."*
  - Verify Groq round-trip latency (typically 400–700ms on Groq).
  - Verify JSON parses flawlessly into `StructuredAiResponse`.
- Verify Number Verbalization:
  - English: "₹450" is verbalized as *"four hundred and fifty rupees"*.
  - Hindi: "₹450" is verbalized as *"चार सौ पचास रुपये"*.
  - Telugu: "₹450" is verbalized as *"నాలుగు వందల యాభై రూపాయలు"*.
- Verify Dish Name Pronunciation:
  - Telugu TTS speaks *"బటర్ నాన్"* fluently without English spelling distortion.

### 6. Failure Modes, Edge Cases & Recovery Protocols
- *Issue*: Groq API rate limit (HTTP 429) or transient network timeout.
  *Recovery*: Circuit breaker triggers immediate retry with exponential backoff; if persistent, switch seamlessly to local rule-based fallback responses.
- *Issue*: LLM outputs malformed JSON or markdown backticks enclosing JSON.
  *Recovery*: `JsonSchemaValidator` strips markdown fences (````json ... ````) and cleans trailing commas before deserialization.

### 7. Git Commit & Push Checkpoint
```bash
git add .
git commit -m "feat(phase4): implement groq llm client with llama 3.3, structured ai response schema, number verbalizer and dish name transliteration"
git push origin main
```

### 8. Exit Criteria & Sign-Off Checklist
- [ ] Groq API client verified on device with active `GROQ_API_KEY`.
- [ ] Structured JSON responses parsed into strongly-typed `StructuredAiResponse` models.
- [ ] Number verbalizer passes 100% unit tests for en, hi, and te.
- [ ] Dish names transliterated into target Indic scripts and pronounced accurately by Bhashini TTS.
- [ ] Raw vs Accessible toggle view working in UI for demonstration.

---

```
================================================================================
PHASE 5: CAMERAX MENU CAPTURE WITH AUDITORY GUIDANCE
================================================================================
```

### 1. Objective & Strategic Goal
Build the eyes-closed camera capture pipeline: CameraX lifecycle integration, real-time image analysis for blur (Laplacian variance), lighting (luma computation), and edge alignment (ML Kit text bounding box boundary detection), spoken real-time auditory coaching for blind users ("Move phone up", "Too dark, move near light", "Hold steady"), automatic shutter capture upon 3-second stability, multi-page document stitching, and multimodal vision extraction using Groq `Llama 3.2 Vision` to produce structured menu entities.

### 2. Prerequisites & Entry Criteria
- Phase 4 completed.
- Camera permission handling integrated.
- Physical printed or digital restaurant menu card available for testing.

### 3. Detailed Technical Architecture & Component Design
1. **CameraX Real-Time Analyzer Pipeline**:
   - `ImageAnalysis.Builder()` configured with `STRATEGY_KEEP_ONLY_LATEST` and `OUTPUT_IMAGE_FORMAT_YUV_420_888` at 1080p resolution.
   - **Quality Metric 1: Luminance (Luma)**: Compute average Y-plane pixel intensity. Threshold: Luma < 40 = "Too dark", Luma > 230 = "Too bright / glare".
   - **Quality Metric 2: Blur (Laplacian Variance)**: Compute variance of Laplacian on 320x240 downscaled grayscale bitmap. Threshold: Variance < 120 = "Blurry, hold steady".
   - **Quality Metric 3: Framing & Text Clipping**: Run ML Kit Text Recognition on low-latency frame. If recognized text bounding boxes intersect within 5% of the frame border, menu is clipped:
     - Top border cut off -> Coaching: *"Menu is cut off at the top, move phone up."*
     - Bottom border cut off -> Coaching: *"Menu is cut off at the bottom, move phone down."*
     - Right border cut off -> Coaching: *"Move phone right."*
     - Left border cut off -> Coaching: *"Move phone left."*
2. **Auditory Coaching State Machine**:
   - Throttle coaching announcements to minimum 2.5 seconds between utterances to avoid overwhelming the user.
   - Distinct O-Haptics feedback:
     - Guiding pulses as user adjusts position.
     - Sharp double click + pleasant earcon when frame is perfectly aligned and stable.
   - Auto-Capture: When framing is valid and blur is below threshold for 1,500ms, trigger `ImageCapture.takePicture()` automatically.
3. **Multimodal Menu Extraction (Groq Vision)**:
   - High-resolution captured JPEG encoded to Base64 and transmitted to `llama-3.2-11b-vision-preview`.
   - Prompt extracts: Restaurant name, sections (Starters, Mains, Breads, Beverages), items with prices in INR, vegetarian/non-vegetarian symbols, spicy ratings, and layout notes (columns, chef specials).

### 4. Step-by-Step Implementation Instructions
1. Implement `menu/camera/CameraXManager.kt` binding `Preview`, `ImageAnalysis`, and `ImageCapture` to lifecycle.
2. Build `menu/analyzer/MenuQualityAnalyzer.kt` computing luma, blur variance, and ML Kit boundary clipping.
3. Implement `menu/coaching/AuditoryCoachingEngine.kt` emitting verbalized coaching cues and haptic ticks.
4. Implement `menu/extractor/MenuExtractor.kt` interfacing with Groq Vision API to parse image into structured `Menu` domain models.
5. Create UI screen `ui/screens/camera/MenuCameraScreen.kt` featuring a full-screen camera viewfinder, high-contrast framing guide, live spoken coaching status banner, and manual shutter override.

### 5. Physical Device Testing & Validation Protocol on realme P3 Pro (Android 16)
- Test with real physical restaurant menu:
  - Hold phone deliberately too close or cut off top edge: Verify voice speaks: *"Menu is cut off at the top, move phone up."*
  - Turn off room light / cover sensor: Verify voice speaks: *"Too dark, move near light."*
  - Hold phone steady over complete menu: Verify voice speaks: *"Hold steady"*, then plays capture earcon and haptic vibration, snapping photo automatically.
- Verify multi-page support: Option to capture Page 2 or proceed to analysis.
- Verify Groq Vision response: Confirm dishes, sections, and prices are accurately extracted into domain models.

### 6. Failure Modes, Edge Cases & Recovery Protocols
- *Issue*: Poor internet connection prevents high-res image upload to Groq Vision.
  *Recovery*: Fall back to local on-device ML Kit Text Recognition to extract raw text lines and prices, accompanied by audio notification: *"Extracting menu offline; layout details may be limited."*
- *Issue*: Stylized handwritten or illegible font causes low OCR confidence.
  *Recovery*: Announce: *"Some text is unclear. Would you like to retake the photo or proceed with partial menu?"*

### 7. Git Commit & Push Checkpoint
```bash
git add .
git commit -m "feat(phase5): implement camerax menu scanner, real-time auditory coaching, auto-capture on stability, and groq vision menu extractor"
git push origin main
```

### 8. Exit Criteria & Sign-Off Checklist
- [ ] Real-time CameraX frame analysis runs smoothly at 30fps without dropping frames.
- [ ] Spoken auditory coaching guides user to proper framing eyes-closed.
- [ ] Auto-capture triggers reliably upon 1.5s of stable, unclipped framing.
- [ ] Groq Vision extracts menu items, prices, and veg/non-veg tags into Room database.
- [ ] ML Kit local fallback functional when network is severed.

---

```
================================================================================
PHASE 6: MENU STORE, SECTION NARRATION & VOICE-FIRST MENU NAVIGATION
================================================================================
```

### 1. Objective & Strategic Goal
Transform the extracted menu into an accessible, voice-navigable audio experience. Store structured menu entities into Room Database, synthesize holistic layout descriptions ("This menu has 4 sections: Starters, Mains, Breads, and Desserts, with 38 items"), build sectioned audio read-aloud with voice commands ("Next section", "Go back", "Read vegetarian only", "Read prices only"), and configure complete TalkBack semantics with 48dp+ interactive targets.

### 2. Prerequisites & Entry Criteria
- Phase 5 completed with verified menu extraction.
- Room Database DAOs from Phase 1 ready for data insertion.

### 3. Detailed Technical Architecture & Component Design
1. **Layout Narrator Engine (`LayoutNarrator`)**:
   - Analyzes menu topology to produce an immediate high-level mental map for blind and elderly diners:
     - Section count, category breakdown, price ranges (e.g., *"Starters has 9 items ranging from 140 to 280 rupees"*), and special callouts (*"5 chef specials, 22 vegetarian options"*).
2. **Voice-First Navigation State Machine**:
   - User commands handled hands-free while reading:
     - *"Next"* / *"अगला"* / *"తర్వాత"*: Skips to next dish or section.
     - *"Repeat"* / *"फिर से"* / *"మళ్ళీ"*: Repeats current item description.
     - *"Vegetarian only"* / *"సిద్ధమైన శాకాహారం"*: Filters reading queue to vegetarian items.
     - *"Skip to Breads"* / *"రోటీలు"*: Jumps directly to requested section.
3. **Accessible UI Browser (`MenuBrowserScreen`)**:
   - Visual screen for sighted companions and low-vision diners:
     - High-contrast section tabs, large font typography, distinct green veg / red non-veg square badges, bold prices, and prominent "Add to Order" action chips.

### 4. Step-by-Step Implementation Instructions
1. Implement `menu/repository/MenuRepository.kt` persisting and querying menus, sections, and items from Room.
2. Build `menu/narrator/LayoutNarrator.kt` generating localized structural audio summaries in en, hi, and te.
3. Build `menu/navigation/VoiceMenuNavigator.kt` managing active playback cursor and handling voice navigation intents.
4. Implement `ui/screens/browser/MenuBrowserScreen.kt` with TalkBack headings (`Modifier.semantics { heading() }`), content descriptions, and large touch targets.

### 5. Physical Device Testing & Validation Protocol on realme P3 Pro (Android 16)
- Load captured menu into repository.
- Close eyes and listen:
  - App speaks layout narrative: *"This menu has 4 sections..."*
  - Speak command: *"Next section"* -> App immediately announces: *"Main Course, 14 items..."*
  - Speak command: *"Read vegetarian only"* -> App filters out non-veg items and reads only veg dishes.
- Enable TalkBack on realme P3 Pro: Swipe through list -> Confirm each dish announces name, price, veg status, and action in a single logical accessibility node.

### 6. Failure Modes, Edge Cases & Recovery Protocols
- *Issue*: Extremely large menu (100+ items) causes audio narration fatigue.
  *Recovery*: Limit initial section read-out to top 3 items, then prompt: *"Would you like to hear the remaining 7 items or jump to Mains?"*

### 7. Git Commit & Push Checkpoint
```bash
git add .
git commit -m "feat(phase6): implement room menu repository, layout narrator, hands-free voice navigation, and accessible menubrowser screen"
git push origin main
```

### 8. Exit Criteria & Sign-Off Checklist
- [ ] Menu successfully persisted in Room and retrievable offline.
- [ ] Layout narrator delivers crisp high-level orientation in en, hi, and te.
- [ ] Hands-free voice commands ("Next", "Repeat", "Veg only") control playback smoothly.
- [ ] TalkBack accessibility pass achieves 100% compliance on MenuBrowserScreen.

---

```
================================================================================
PHASE 7: MULTI-TURN CONVERSATIONAL DIALOGUE MANAGER & INTENT ROUTING
================================================================================
```

### 1. Objective & Strategic Goal
Architect the central dialogue manager: a finite state machine orchestrating conversation turns, managing short-term conversational context across multi-turn interactions, executing Groq tool/function calling, fuzzy-matching transcribed names against real menu items using Indic phonetic normalization, and safeguarding culinary dish names via script transliteration.

### 2. Prerequisites & Entry Criteria
- Phase 6 completed.
- Groq LLM client and Bhashini S2S spine operational.

### 3. Detailed Technical Architecture & Component Design
1. **Dialogue State Machine (`DialogueManager`)**:
   - States: `Idle`, `Listening`, `Processing`, `Speaking`, `WaitingForConfirmation`, `ErrorRecovery`.
   - Context: Retains last 6 conversation turns, active menu ID, current cart items, and detected user preferences.
2. **Groq Tool Definitions (`ToolDefinitions`)**:
   - `filter_menu(dietary, max_spice, max_price, section)`
   - `explain_item(item_id, reading_level)`
   - `compare_items(item_ids)`
   - `add_to_order(item_id_or_name, quantity, special_notes)`
   - `remove_from_order(item_id)`
   - `confirm_order()`
   - `request_waiter(reason)`
   - `request_bill(split_mode, split_count)`
3. **Indic Phonetic Fuzzy Matcher (`FuzzyMatcher`)**:
   - Transcribed Indian dish names frequently suffer from spelling variations (e.g., "Paneer" vs "Panir", "Tikka" vs "Teeka", "Naan" vs "Nan", "Gongura" vs "Gongoora").
   - Algorithm: Combines Levenshtein distance with Indic soundex rules:
     - Normalizes long vowels: `ee` -> `i`, `oo` -> `u`, `aa` -> `a`.
     - Collapses aspirated consonants: `kh` -> `k`, `gh` -> `g`, `th` -> `t`, `dh` -> `d`, `ph` -> `p`, `bh` -> `b`.
     - Resolves sibilants: `sh` / `shh` -> `s`.

### 4. Step-by-Step Implementation Instructions
1. Implement `dialogue/state/DialogueStateMachine.kt` managing states and transitions.
2. Build `dialogue/matching/FuzzyMatcher.kt` and `dialogue/matching/IndicPhoneticNormalizer.kt` with unit test suite.
3. Build `dialogue/tools/ToolExecutor.kt` dispatching LLM tool calls to local domain repositories.
4. Integrate `dialogue/manager/DialogueManager.kt` linking Audio Ingestion -> LLM Tool Invocation -> Output Adapter -> Audio Playback.

### 5. Physical Device Testing & Validation Protocol on realme P3 Pro (Android 16)
- Test multi-turn conversational dialogue:
  - Turn 1: *"What spicy chicken starters do you have?"* -> LLM calls `filter_menu(dietary="non-veg", section="Starters", min_spice=2)` -> Responds with matching dishes.
  - Turn 2: *"Tell me more about the first one."* -> Dialogue manager resolves reference from context -> Explains dish.
  - Turn 3: *"Add two of that to my order."* -> Resolves item ID and triggers cart addition.
- Test Phonetic Matching:
  - Say: *"Add two panir teeka"* -> Matches `Paneer Tikka` with confidence > 0.92.

### 6. Failure Modes, Edge Cases & Recovery Protocols
- *Issue*: Ambiguous match (e.g., user says "Biryani" and menu has Chicken Biryani, Mutton Biryani, and Veg Biryani).
  *Recovery*: Dialogue manager asks clarifying question: *"We have Chicken Biryani, Mutton Biryani, and Veg Biryani. Which one would you prefer?"*

### 7. Git Commit & Push Checkpoint
```bash
git add .
git commit -m "feat(phase7): implement multi-turn dialogue manager, groq tool calling engine, and indic phonetic fuzzy matcher"
git push origin main
```

### 8. Exit Criteria & Sign-Off Checklist
- [ ] Multi-turn conversational flow executes seamlessly without losing context.
- [ ] Groq tool calls reliably trigger local repository methods.
- [ ] Indic phonetic fuzzy matcher resolves distorted dish transcriptions.
- [ ] State machine handles interruptions and cancel commands gracefully.

---

```
================================================================================
PHASE 8: DIETARY REASONING, ALLERGEN INTELLIGENCE & FOOD EXPLANATION
================================================================================
```

### 1. Objective & Strategic Goal
Implement intelligent food Q&A and dietary safety verification. Build a deterministic rule-based dietary validator for Vegetarian, Jain, Vegan, Nut-free, and Halal diets combined with LLM culinary ingredient inference. Guarantee safety by enforcing conservative allergen warnings and "Ask Waiter" escalations. Build the 3-tier "Explain Simpler" engine and accessible table narrator.

### 2. Prerequisites & Entry Criteria
- Phase 7 completed.
- Menu items populated with ingredients and tags.

### 3. Detailed Technical Architecture & Component Design
1. **Deterministic Dietary & Allergen Engine**:
   - **Jain Rules**: Strictly prohibits root vegetables (potato/aloo, onion/pyaz, garlic/lasun, carrot, radish, ginger). Even if dish is marked vegetarian, presence of these ingredients immediately flags a Jain violation.
   - **Vegetarian Rules**: Verifies green dot badge + absence of animal meat, poultry, seafood, gelatin.
   - **Nut Allergies**: Extreme caution. Any mention of cashew (kaju) paste, almond (badam), peanut, or generic gravy base ("shahi", "korma", "makhani" often contain cashew paste) triggers explicit warning: *"This dish typically uses cashew paste. Please verify with staff."*
   - **Disclaimer Policy**: Never provide medical advice. Always append: *"Please confirm with restaurant staff before consuming if you have severe allergies."*
2. **Three-Tier "Explain Simpler" Engine**:
   - Tier 1 (Standard): Rich culinary description with flavor profiles and spice notes.
   - Tier 2 (Simplified): Short plain-language sentences with familiar analogies (e.g., *"Dahi Vada: soft fried lentil dumplings soaked in cool yogurt with sweet and tangy chutneys"*).
   - Tier 3 (Very Simple / Cognitive & Ageing): Ultra-short sentences, familiar terms, zero culinary jargon, large fonts.
3. **Table Narrator Engine (`TableNarrator`)**:
   - When user asks to compare two or three dishes (e.g., *"Compare Dal Makhani and Dal Tadka"*), the LLM generates a structured comparison table.
   - Output Adapter converts the table into a flowing spoken narrative (`tableNarration`) for voice/blind users while rendering a clean visual comparison card for sighted companions.

### 4. Step-by-Step Implementation Instructions
1. Implement `dialogue/dietary/DietaryRuleEngine.kt` with rule sets for Jain, Veg, Vegan, Gluten-free, and Nuts.
2. Build `dialogue/explain/ExplainSimplerController.kt` managing reading level prompts and responses.
3. Build `adapt/adapter/TableNarrator.kt` generating spoken narratives from structured tables.
4. Implement UI screen `ui/screens/detail/DishDetailScreen.kt` featuring large dietary badges, simplified explanation toggle, and "Ask Waiter About This" button.

### 5. Physical Device Testing & Validation Protocol on realme P3 Pro (Android 16)
- Query Jain suitability on realme P3 Pro:
  - Ask: *"Is Paneer Butter Masala Jain?"*
  - Verify system warns: *"Paneer Butter Masala typically contains onion and garlic in the gravy, so it is not strictly Jain. We can ask the kitchen if it can be prepared without onion and garlic."*
- Test "Explain Simpler":
  - Ask: *"What is Gongura Mutton?"*
  - Toggle "Explain Simpler" button -> Verify description simplifies from complex culinary jargon to simple, easy-to-understand explanation.
- Listen to Dish Comparison:
  - Ask: *"Compare Dal Makhani and Dal Tadka"* -> Listen to clear verbal table narration comparing ingredients, spice, and price.

### 6. Failure Modes, Edge Cases & Recovery Protocols
- *Issue*: Unknown or unlisted ingredients for a local specialty dish.
  *Recovery*: Output Adapter tags confidence as `low`, sets `needsStaffConfirmation = true`, and offers one-tap button: *"Ask the waiter about ingredients."*

### 7. Git Commit & Push Checkpoint
```bash
git add .
git commit -m "feat(phase8): implement deterministic dietary rule engine, allergen warnings, explain-simpler 3-tier generator and table narrator"
git push origin main
```

### 8. Exit Criteria & Sign-Off Checklist
- [ ] Jain, vegetarian, and nut-allergy queries adhere strictly to safety rules.
- [ ] "Explain Simpler" toggle produces verified simplified text and speech.
- [ ] Table comparison generates flawless spoken narration and visual layout.
- [ ] Safety disclaimers always present on allergen inquiries.

---

```
================================================================================
PHASE 9: DETERMINISTIC CART, READ-BACK & ORDER CONFIRMATION ENGINE
================================================================================
```

### 1. Objective & Strategic Goal
Build the deterministic cart and ordering subsystem: Cart state management in Room, quantity updates, modifier management, explicit spoken read-back in the diner's language ("You have ordered: two butter naan and one paneer butter masala. Total 560 rupees. Say confirm to place the order"), high-stakes voice confirmation protocol, and TalkBack-accessible cart review screen.

### 2. Prerequisites & Entry Criteria
- Phase 8 completed.
- Menu repository and number verbalizer functional.

### 3. Detailed Technical Architecture & Component Design
1. **Deterministic Cart Engine (`CartRepository`)**:
   - LLM cannot directly modify the cart; it can only invoke `add_to_order` or `remove_from_order` tool calls.
   - Price calculations are strictly handled by Kotlin integer math (in paise) to eliminate any hallucination or rounding errors:
     $$\text{Item Total} = \text{Item Price} \times \text{Quantity}$$
     $$\text{Cart Subtotal} = \sum \text{Item Totals}$$
2. **High-Stakes Spoken Read-Back Protocol**:
   - Before any order is finalized, SarvaAI must execute a complete, unambiguous spoken summary:
     - Item quantities verbalized using `NumberVerbalizer`.
     - Subtotal verbalized with currency name.
     - Explicit prompt: *"Say 'Confirm' to place your order, or say 'Change' to make edits."*
3. **Voice Confirmation Gate**:
   - Cart moves to state `AWAITING_CONFIRMATION`.
   - Any voice input other than affirmative ("Confirm", "Yes", "हाँ", "అవును", "చేయండి") cancels or prompts for clarification, preventing accidental orders.

### 4. Step-by-Step Implementation Instructions
1. Implement `dialogue/order/CartRepository.kt` managing Room-backed cart items.
2. Implement `dialogue/order/OrderConfirmationManager.kt` constructing verbalized order summaries.
3. Build `ui/screens/cart/CartScreen.kt` with high-contrast item rows, quantity increment/decrement buttons (minimum 64dp hit area), and prominent "Confirm Order" button.
4. Implement spoken confirmation dialog overlay with live audio prompts.

### 5. Physical Device Testing & Validation Protocol on realme P3 Pro (Android 16)
- Test voice ordering on realme P3 Pro:
  - Speak: *"Add two butter naan and one kadai paneer"* -> Items appear in cart with correct prices.
  - Speak: *"Place order"* -> App plays prompt: *"You have ordered: two butter naan and one kadai paneer. Total 480 rupees. Say confirm to place order."*
  - Speak: *"Confirm"* -> Order state transitions to `ORDERED`, plays success earcon, and triggers double-pulse confirmation haptic.
- Verify TalkBack navigation on CartScreen:
  - Swipe through cart items -> Verify quantity controls announce clear accessibility labels ("Increase Butter Naan quantity, current quantity 2").

### 6. Failure Modes, Edge Cases & Recovery Protocols
- *Issue*: User mumbles or background restaurant noise is detected during confirmation turn.
  *Recovery*: Do not finalize order! Ask: *"I heard noise. Do you want to confirm your order of two items for 480 rupees? Please tap Confirm or say Yes."*

### 7. Git Commit & Push Checkpoint
```bash
git add .
git commit -m "feat(phase9): implement deterministic cart repository, verbalized spoken read-back, voice confirmation gate and accessible cart screen"
git push origin main
```

### 8. Exit Criteria & Sign-Off Checklist
- [ ] Cart calculations strictly deterministic with zero floating-point errors.
- [ ] Spoken read-back verbalizes all items, quantities, and totals accurately in en, hi, and te.
- [ ] Explicit confirmation gate prevents accidental order submissions.
- [ ] Order persists in Room database and survives application process restart.

---

```
================================================================================
PHASE 10: WAITER INTERACTION BRIDGE & DUAL-SCREEN STAFF MODE
================================================================================
```

### 1. Objective & Strategic Goal
Build the groundbreaking **Waiter Bridge**: a two-way communication bridge connecting diners with speech or hearing impairments and restaurant waitstaff. Features include a dual-view UI (Diner View vs Waiter High-Legibility Card), Bhashini TTS broadcast in the waiter's preferred language (Hindi, Telugu, or English), chunked waiter speech recognition translating staff responses into live large-print captions for deaf diners, and a context-aware Quick Phrase Board.

### 2. Prerequisites & Entry Criteria
- Phase 9 completed with ordered state.
- Bhashini ASR, NMT, and TTS operational across cross-language pairs.

### 3. Detailed Technical Architecture & Component Design
1. **Dual-View Waiter Card Interface (`WaiterBridgeScreen`)**:
   - **Diner Side**: Compact controls, Quick Phrase Board chips, live transcript of conversation.
   - **Waiter Side**: High-contrast, giant 32sp+ typography facing outward or displayed on a flip card:
     - Clear requests: *"BILL PLEASE"*, *"WATER PLEASE"*, *"LESS SPICY"*, *"IS THIS VEGETARIAN?"*.
     - Spoken loudly via Bhashini TTS in the waiter's chosen language upon tap.
2. **Two-Way Cross-Language Speech Bridge**:
   - Diner speaks or taps in Telugu -> Phone speaks in Hindi to waiter: *"कृपया बिल लेकर आइए"* (Kripya bill lekar aaiye).
   - Waiter speaks in Hindi: *"दस मिनट में लाता हूँ"* (Das minute mein laata hoon) -> Phone captures audio in 3-second VAD chunks, translates via Bhashini NMT into Telugu, and displays giant live captions on diner's screen: *"పది నిమిషాల్లో తెస్తాను"* (Padi nimishaallo testaanu).
3. **Quick Phrase Board (`PhraseBoard`)**:
   - One-tap accessible chips tailored to restaurant dining:
     - "I am deaf, please read this card"
     - "Please bring drinking water"
     - "Please bring the bill"
     - "Call the manager"
     - "Less spicy / No ice"

### 4. Step-by-Step Implementation Instructions
1. Implement `service/waiter/WaiterBridgeController.kt` managing cross-language translation queues.
2. Build `service/waiter/PhraseBoard.kt` with localized pre-recorded and synthesized phrases.
3. Implement `ui/screens/waiter/WaiterBridgeScreen.kt` featuring flip-card UI, giant staff-facing text display, and live caption stream for incoming staff speech.
4. Add staff language selector toggle (Hindi / Telugu / English).

### 5. Physical Device Testing & Validation Protocol on realme P3 Pro (Android 16)
- Test Waiter Bridge in two-person simulation:
  - Diner sets app to Telugu, Waiter language to Hindi.
  - Diner taps *"Bring the bill"* chip.
  - Phone speaks loudly in Hindi through speakers: *"कृपया बिल लेकर आइए"*, while displaying large bold text.
  - Tap "Listen to Waiter" -> Tester speaks Hindi: *"हाँ, तुरंत ला रहा हूँ"* -> Phone translates and displays Telugu caption: *"అవును, వెంటనే తెస్తున్నాను"*.
- Test High-Contrast Staff Display:
  - Verify visibility from 1.5 meters away under varying lighting conditions.

### 6. Failure Modes, Edge Cases & Recovery Protocols
- *Issue*: Waiter speaks rapidly in noisy ambient dining room.
  *Recovery*: Chunked ASR runs with aggressive energy VAD; if transcription fails, UI immediately displays fallback card: *"Please tap 'Yes' or 'No', or point at the menu."*

### 7. Git Commit & Push Checkpoint
```bash
git add .
git commit -m "feat(phase10): implement waiter bridge, cross-language staff tts, live captions for deaf diners, and quick phrase board"
git push origin main
```

### 8. Exit Criteria & Sign-Off Checklist
- [ ] Two-way cross-language bridge functional between Telugu/English user and Hindi staff.
- [ ] Staff card displays readable typography from 1.5m distance.
- [ ] Quick Phrase Board triggers instant TTS playback without network lag.
- [ ] Live captions update smoothly on diner's display during staff speech.

---

```
================================================================================
PHASE 11: BILL CALCULATION, SPLITTING ENGINE & UPI PAYMENT INTENT
================================================================================
```

### 1. Objective & Strategic Goal
Implement the restaurant billing and payment subsystem: deterministic tax calculation (CGST, SGST, configurable service charges), flexible bill splitting (equal split among $N$ diners, item-by-item split, or custom amounts), spoken verbalized breakdown per person, and NPCI UPI URI deep-linking (`upi://pay`) with high-contrast QR code rendering.

### 2. Prerequisites & Entry Criteria
- Phase 10 completed.
- Confirmed order items and prices in Room database.

### 3. Detailed Technical Architecture & Component Design
1. **Deterministic Tax & Bill Engine (`BillCalculator`)**:
   - Computes:
     $$\text{Subtotal} = \sum (\text{Price} \times \text{Qty})$$
     $$\text{CGST (2.5\%)} = \lfloor \text{Subtotal} \times 0.025 \rceil$$
     $$\text{SGST (2.5\%)} = \lfloor \text{Subtotal} \times 0.025 \rceil$$
     $$\text{Service Charge (Optional 5\%)} = \lfloor \text{Subtotal} \times 0.05 \rceil$$
     $$\text{Grand Total} = \text{Subtotal} + \text{CGST} + \text{SGST} + \text{Service Charge}$$
   - Rounding policy: Nearest whole rupee, with difference balanced on subtotal to ensure line items sum exactly to total.
2. **Bill Splitting Modes**:
   - Mode 1: Equal split across $N$ people (e.g., ₹1,200 / 4 = ₹300 per person).
   - Mode 2: By-Item assignment (User A assigned Biryani + Coke; User B assigned Naan + Paneer).
3. **NPCI UPI Deep Link & QR Engine**:
   - Generates standard UPI URI:
     `upi://pay?pa=restaurant@upi&pn=SarvaAI+Restaurant&am=300.00&cu=INR&tn=Table+4+Bill+Split`
   - Dispatches Android Intent to installed UPI apps (GPay, PhonePe, Paytm) or renders a high-contrast QR code for tabletop scanning.

### 4. Step-by-Step Implementation Instructions
1. Implement `service/bill/BillCalculator.kt` with 100% test coverage for GST edge cases.
2. Implement `service/bill/SplitBillEngine.kt` supporting equal and itemized splits.
3. Implement `service/upi/UpiIntentGenerator.kt` building compliant UPI intents and QR bitmaps.
4. Build `ui/screens/bill/BillSplitScreen.kt` featuring tax breakdown accordion, participant split slider, spoken breakdown trigger, and UPI payment button.

### 5. Physical Device Testing & Validation Protocol on realme P3 Pro (Android 16)
- Test bill calculation on realme P3 Pro:
  - Add items totaling ₹800 -> Verify CGST (₹20), SGST (₹20) -> Grand Total ₹840.
  - Select "Split among 3 people" -> Verify per-person amount is exactly ₹280.
  - Tap "Speak Breakdown" -> App speaks: *"Total bill is 840 rupees. Split among 3 people, each person pays 280 rupees."*
- Test UPI intent dispatch:
  - Tap "Pay with UPI" -> Android system sheet opens showing available UPI apps (or logged intent URI if none installed).

### 6. Failure Modes, Edge Cases & Recovery Protocols
- *Issue*: Bill does not divide evenly (e.g., ₹100 split among 3 = ₹33.33).
  *Recovery*: Split assigns ₹34 to Person 1, ₹33 to Person 2, and ₹33 to Person 3, with spoken clarification: *"Two people pay 33 rupees and one person pays 34 rupees."*

### 7. Git Commit & Push Checkpoint
```bash
git add .
git commit -m "feat(phase11): implement deterministic bill calculator, gst engine, bill splitting modes, spoken share summaries and upi deep linking"
git push origin main
```

### 8. Exit Criteria & Sign-Off Checklist
- [ ] Tax calculations mathematically exact with zero rounding drift.
- [ ] Spoken bill summary clearly verbalizes individual shares.
- [ ] Bill splitting supports both equal and itemized allocations.
- [ ] UPI URI conforms to NPCI standards and launches on physical device.

---

```
================================================================================
PHASE 12: CONDITION INFERENCE ENGINE & DYNAMIC ADAPTATION
================================================================================
```

### 1. Objective & Strategic Goal
Implement the core competitive differentiator: the on-device **Condition Inference Engine**. Collect ambient system and behavioral signals (TalkBack active, captioning enabled, muted volume, font scale >= 1.3, ASR retry count, speech pauses, touchlessness), run a weighted hysteresis scoring model to infer impairments (Visual, Hearing, Speech, Ageing), and dynamically activate reversible feature flags (`captionsOn`, `slowTts`, `phraseBoard`, `largeText`, `voiceFirst`). Build a live Debug Signal Panel to demonstrate real-time adaptation.

### 2. Prerequisites & Entry Criteria
- Phases 1 through 11 operational.
- All adaptive feature flags wired to UI and audio controllers.

### 3. Detailed Technical Architecture & Component Design
1. **Signal Collector (`SignalCollector`)**:
   - `AccessibilityManager.isTouchExplorationEnabled` -> High confidence: Visual.
   - `CaptioningManager.isEnabled` -> High confidence: Hearing.
   - `Configuration.fontScale >= 1.3f` -> High confidence: Ageing / Low Vision.
   - `AudioManager.getStreamVolume(STREAM_MUSIC) == 0` -> Medium confidence: Hearing.
   - Repeated empty/low-confidence ASR transcripts (>2 failures) -> High confidence: Speech.
   - VAD pause duration > 1,800ms between words -> High confidence: Speech / Ageing.
   - Zero screen taps in first 15 seconds after launch -> Medium confidence: Visual.
   - User repeatedly taps "Repeat" or "Slower" -> High confidence: Ageing / Hearing.
2. **Weighted Hysteresis Scoring Model (`ProfileInferenceEngine`)**:
   - Each signal adds weighted points to condition scores ($S_{vis}, S_{hear}, S_{speech}, S_{age}$).
   - Upper Threshold ($T_{high} = 70$): Feature turns **ON**.
   - Lower Threshold ($T_{low} = 40$): Feature turns **OFF** (prevents flapping).
3. **Transparency, Announcement & Undo Policy**:
   - Every adaptation is announced immediately via speech, caption, and haptic:
     *"I have enabled larger text and slower voice. Say 'Undo' or tap to revert."*
   - User maintains 100% control to override any flag in Settings.

### 4. Step-by-Step Implementation Instructions
1. Implement `adapt/signals/SignalCollector.kt` listening to system accessibility and UI telemetry.
2. Build `adapt/inference/ProfileInferenceEngine.kt` implementing the hysteresis scoring algorithm.
3. Build `adapt/flags/FeatureFlags.kt` managing reactive state flow of active flags.
4. Implement `ui/screens/debug/DebugSignalPanel.kt` with interactive sliders allowing judges and testers to inject simulated signals (e.g., simulate high font scale, low speech confidence) and watch features adapt live.

### 5. Physical Device Testing & Validation Protocol on realme P3 Pro (Android 16)
- Test automatic inference:
  - Enable TalkBack on realme P3 Pro -> Launch SarvaAI -> Verify app instantly enters `voiceFirst = true` mode, speaks welcoming orientation, and suppresses redundant visual live regions.
  - Mute device volume -> App automatically activates `captionsOn = true` with live caption banner.
- Test Debug Signal Panel:
  - Open Debug Panel -> Tap "Simulate Speech Impairment (Failed ASR x2)" -> Verify app immediately expands the Quick Phrase Board and offers tap-to-speak chips with notification: *"Switched to Phrase Board for easier ordering."*
  - Tap "Undo" -> Confirms state reverts cleanly.

### 6. Failure Modes, Edge Cases & Recovery Protocols
- *Issue*: Engine misinfers a condition due to temporary noisy room.
  *Recovery*: Never trap user in a locked mode. Feature changes are subtle feature flags, not screen blockers, and single-tap "Undo" reverses changes instantly.

### 7. Git Commit & Push Checkpoint
```bash
git add .
git commit -m "feat(phase12): implement signal collector, condition inference engine with hysteresis, reversible feature flags and interactive debug signal panel"
git push origin main
```

### 8. Exit Criteria & Sign-Off Checklist
- [ ] System signals (TalkBack, font scale, volume) detected accurately on Android 16.
- [ ] Behavioral signals (ASR retries, hesitation) update condition scores properly.
- [ ] Feature flags toggle dynamically without requiring app restart.
- [ ] Spoken & visual announcement triggers on every adaptation with instant Undo.
- [ ] Debug Signal Panel allows immediate verification of all 4 conditions.

---

```
================================================================================
PHASE 13: RESILIENT OFFLINE FALLBACKS, DEGRADED MODE & PRE-BUNDLED ASSETS
================================================================================
```

### 1. Objective & Strategic Goal
Implement comprehensive resilience and graceful failure handling (Rubric Weight: 10% + Bonus): active network monitoring, circuit breakers with exponential backoff, offline response caching in Room, pre-bundled Bhashini TTS WAV audio in `assets/` for static prompts across all 3 languages, on-device Android SpeechRecognizer and TTS fallbacks, and transparent "Degraded Mode" UI labeling.

### 2. Prerequisites & Entry Criteria
- Phase 12 completed.
- Pre-recorded / synthesized Bhashini audio clips for static phrases prepared and placed in `assets/audio/`.

### 3. Detailed Technical Architecture & Component Design
1. **Network Monitor & Circuit Breaker (`NetworkMonitor`, `CircuitBreaker`)**:
   - `ConnectivityManager.NetworkCallback` tracks Wi-Fi / Cellular state changes.
   - Circuit Breaker transitions: `CLOSED` -> 3 consecutive failures -> `OPEN` (diverts to offline fallbacks for 30 seconds) -> `HALF-OPEN` (probes single call).
2. **Pre-Bundled Static Audio Assets (`assets/audio/`)**:
   - 24 pre-generated Bhashini TTS clips in English, Hindi, and Telugu:
     - Welcome & language selection prompt.
     - Camera coaching instructions ("Hold steady", "Too dark", "Photo captured").
     - Order confirmation prompts ("Order confirmed", "Please confirm").
     - Error messages ("Network connection dropped, trying again").
     - Phrase board standard lines ("Bill please", "Water please", "Less spicy").
   - Guarantees that core blind and speech flows function 100% offline with zero network connectivity!
3. **On-Device Local Fallbacks**:
   - Fallback ASR: Android `android.speech.SpeechRecognizer` with downloaded offline packs.
   - Fallback TTS: Android `android.speech.tts.TextToSpeech` with native Hindi/Telugu voices.
   - Fallback OCR: Google ML Kit on-device text recognition.
4. **Degraded Mode User Notice**:
   - Spoken, visual, and haptic notification: *"Network unavailable. Running in offline degraded mode. Some features may be limited."*

### 4. Step-by-Step Implementation Instructions
1. Implement `failure/network/NetworkMonitor.kt` using modern Android 16 connectivity callbacks.
2. Build `failure/breaker/CircuitBreaker.kt` with state management and timeout policies.
3. Bundle pre-synthesized audio WAVs in `app/src/main/assets/audio/{en,hi,te}/`.
4. Implement `failure/offline/OfflineFallbackManager.kt` routing requests to local assets or Android system services when circuit breaker is OPEN.
5. Create visual "Offline / Degraded Mode" high-contrast badge in top banner.

### 5. Physical Device Testing & Validation Protocol on realme P3 Pro (Android 16)
- Test offline resilience on realme P3 Pro:
  - Put phone in Airplane Mode.
  - Launch SarvaAI -> Verify app plays pre-bundled welcome audio in English/Hindi without network errors.
  - Verify camera coaching still speaks pre-bundled audio cues.
  - Verify captured menu items can be browsed and added to cart from Room database.
  - Verify bill calculations and UPI QR generation work 100% offline.
- Restore Network:
  - Turn off Airplane Mode -> Verify circuit breaker closes and online Bhashini/Groq APIs resume automatically.

### 6. Failure Modes, Edge Cases & Recovery Protocols
- *Issue*: System SpeechRecognizer missing offline Indic language pack.
  *Recovery*: Fall back to text-input keyboard overlay and pre-rendered Phrase Board chips.

### 7. Git Commit & Push Checkpoint
```bash
git add .
git commit -m "feat(phase13): implement network monitor, circuit breaker, pre-bundled bhashini audio assets, system tts fallback and offline degraded mode"
git push origin main
```

### 8. Exit Criteria & Sign-Off Checklist
- [ ] Core user flows (welcome, camera coaching, cart, bill) operate in 100% Airplane Mode.
- [ ] Pre-bundled Bhashini audio plays instantaneously with zero latency.
- [ ] Circuit breaker transitions correctly between CLOSED, OPEN, and HALF-OPEN.
- [ ] UI clearly displays and announces degraded mode status to user.

---

```
================================================================================
PHASE 14: END-TO-END STRESS TESTING, ACCESSIBILITY AUDITS & PHYSICAL DEVICE POLISH
================================================================================
```

### 1. Objective & Strategic Goal
Execute exhaustive physical stress testing, field verification, accessibility compliance auditing, and production release packaging on the **realme P3 Pro (Android 16)**. Validate eyes-closed end-to-end journey, background restaurant noise tolerance, memory profiler benchmarks on 8GB RAM, latency budget verification (<4s time-to-first-audio), generation of signed release APK, and final synchronization with `https://github.com/Aditya-Lingam-9000/SarvaAI`.

### 2. Prerequisites & Entry Criteria
- All 13 previous phases fully implemented and tested.
- Android Accessibility Scanner app installed on realme P3 Pro.

### 3. Detailed Technical Architecture & Component Design
1. **Exhaustive Verification Drill Protocol**:
   - **Drill 1: Eyes-Closed Complete Customer Journey**: Tester wears a physical blindfold. Launches app via voice/TalkBack -> Sets language to Telugu -> Captures physical menu via audio coaching -> Listens to menu layout -> Inquires about vegetarian dishes -> Adds two items to cart -> Verifies spoken read-back -> Confirms order -> Calls waiter -> Splits bill. Must succeed with 0% sighted intervention.
   - **Drill 2: Ambient Restaurant Noise Stress Test**: Play loud restaurant ambient chatter audio in background at 70dB SPL -> Verify VAD thresholding and Bhashini ASR accuracy.
   - **Drill 3: Android 16 System Process Death Resilience**: Background app during cart review -> Kill process via `adb shell am kill com.sarvaai.app` -> Relaunch app -> Confirm cart, menu, and state restore perfectly from Room.
   - **Drill 4: Memory Leak & Profiler Audit**: Run Android Studio Memory Profiler for 30 minutes of continuous voice turns -> Verify memory remains stable under 180MB on 8GB RAM device with zero bitmap or audio leaks.
2. **Accessibility Scanner Compliance**:
   - Run Google Accessibility Scanner across every screen -> Target: 0 suggestions / 100% score (touch targets >= 48dp, contrast >= 7:1, valid content descriptions).
3. **Production Release Packaging**:
   - Configure ProGuard / R8 rules preserving Room entities, Kotlinx Serialization DTOs, and Media3 codecs.
   - Generate release signed APK (`app-release.apk`).

### 4. Step-by-Step Implementation Instructions
1. Configure `app/proguard-rules.pro` with necessary keep rules.
2. Build release APK:
   ```bash
   ./gradlew assembleRelease
   ```
3. Install release build on realme P3 Pro:
   ```bash
   adb install -r app/build/outputs/apk/release/app-release.apk
   ```
4. Run automated accessibility test suite and latency trace benchmarks.
5. Create a comprehensive, production-grade `README.md` documenting setup, API keys, architecture, rubric mapping, and demo video walk-through.

### 5. Physical Device Testing & Validation Protocol on realme P3 Pro (Android 16)
- Execute all 4 drills directly on the realme P3 Pro.
- Record latency measurements:
  - Time-to-first-audio on voice turn: Target < 3.2 seconds.
  - Camera auto-capture response: Target < 1.5 seconds.
  - Groq LLM structured response time: Target < 600 milliseconds.
- Run Accessibility Scanner: Capture clean pass report.

### 6. Failure Modes, Edge Cases & Recovery Protocols
- *Issue*: ProGuard/R8 strips Kotlinx Serialization serializers in release build causing JSON crash.
  *Recovery*: Ensure `-keepattributes *Annotation*,Signature,InnerClasses` and `@kotlinx.serialization.Serializable class *` rules are present in `proguard-rules.pro`.

### 7. Git Commit & Push Checkpoint
```bash
git add .
git commit -m "chore(phase14): complete end-to-end accessibility drills, memory profiling, proguard rules, release build and comprehensive documentation"
git push origin main
```

### 8. Exit Criteria & Sign-Off Checklist
- [ ] Eyes-closed end-to-end journey successfully completed on realme P3 Pro.
- [ ] 100% pass on Google Accessibility Scanner across all app screens.
- [ ] Zero memory leaks or crashes during 30-minute stress drill.
- [ ] Release APK compiled, signed, and validated on Android 16.
- [ ] Remote GitHub repository `https://github.com/Aditya-Lingam-9000/SarvaAI` up-to-date on `main`.

---

## 5. Hackathon Rubric Alignment & Competitive Winning Matrix

| Evaluation Criterion | Rubric Weight | Where SarvaAI Wins Maximum Points |
|---|:---:|---|
| **Accessibility of Outputs** | **25%** | Raw LLM output is strictly forbidden. Every turn passes through the **Output Adapter**: sentence-chunked speech (<20 words), word-highlighted live captions, spoken table narration, number verbalization in Indic languages, and 3-tier "Explain Simpler" reading levels. |
| **Depth of Conditions** | **20%** | Comprehensive implementations across **4 conditions** (Visual [mandatory], Hearing, Speech, Ageing) featuring demonstrable concrete tools: Auditory camera coaching, Waiter communication bridge, Phrase board with Indic phonetic matching, and dynamic typography up to 200%. |
| **Bhashini & Indic Language Quality** | **20%** | Full speech-to-speech loop in English, Hindi, and Telugu using AnuvaadHub / Dhruva pipelines. Features client-side speed regulation with pitch preservation, culinary dish name script transliteration (`బటర్ నాన్` / `पनीर बटर मसाला`), and dynamic capability matrix probing. |
| **User Understanding & Real-World Impact** | **15%** | Real-world Indian dining nuances: Green/Red veg dot detection, strict Jain dietary restriction enforcement, allergy warnings with staff escalation, Indian GST calculations (CGST/SGST), bill splitting, and UPI deep-linking. |
| **Technical Quality & Graceful Failure** | **10%** | Unidirectional MVI architecture, Room database persistence, circuit breakers with exponential backoff, pre-bundled Bhashini static audio in `assets/`, offline degraded mode labeling, and TalkBack double-speech collision suppression. |
| **Bonus Evaluation Points** | **+5%** | Field validation with real blind and elderly users, 4 conditions executed with extreme depth (>3 required), 100% offline degraded functionality, and dynamic condition inference engine with live demonstration debug panel. |

---

## 6. Execution Rules of Engagement
1. **Never Rush**: Do not jump to the next phase until the current phase is fully built, compiled, installed on the physical realme P3 Pro, and verified in real usage.
2. **Every Change Committed & Pushed**: Commit every working slice and push immediately to GitHub (`https://github.com/Aditya-Lingam-9000/SarvaAI`).
3. **Hardware Ground Truth**: The realme P3 Pro running Android 16 is the sole source of truth. Simulator passes are insufficient.
4. **Zero Vibe-Coding**: All code must conform to pinned library versions, typed contracts, explicit error handling, and accessibility standards.
