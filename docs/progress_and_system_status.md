# SvaraAI: Technical Progress Report & System Status Documentation

---

# [PAGE 1 OF 4] Executive Overview, Architecture & Core Speech Stack

## 1. Executive Summary

**SvaraAI** is an assistive, voice-first, dual-sensory mobile platform engineered specifically for visually impaired, blind, low-vision, deaf, and mute diners in Indian restaurants. Dining independently in public establishments presents severe accessibility hurdles: inaccessible printed physical menus, noisy ambient acoustic environments, multi-lingual staff interactions, and complex bill calculations with Indian tax regulations.

SvaraAI bridges this gap by unifying multilingual Indian speech recognition, conversational voice ordering, a bidirectional waiter communication bridge, deterministic tax computation, and multi-diner bill splitting into a single, high-contrast, multi-sensory application.

---

## 2. Technology Stack & Architectural Foundation

The platform is constructed entirely in modern native Android technologies to guarantee high frame rates, deterministic state management, and rapid hardware-level haptic/audio triggering:

- **Programming Language**: Kotlin 1.9.24 / JVM Target 17
- **UI Toolkit**: Jetpack Compose with declarative Material 3 accessible styling
- **Asynchronous Concurrency**: Kotlin Coroutines, StateFlow, and SharedFlow
- **Local Persistence**: Room SQLite Object Relational Mapping (`AppDatabase`, `MenuDao`, `CartDao`, `OrderDao`)
- **Speech & Translation Infrastructure**: Bhashini ULCA API Suite (ASR Conformer, IndicTrans2 NMT, Indic TTS)
- **Conversational Intelligence**: Groq LLM API with high-throughput LLaMA-3.3-70B-Versatile
- **Hardware Integrations**: Android CameraX (preview & image analysis), `Vibrator` / `VibratorManager`, Audio Track synthesizers

---

## 3. Core Multilingual Speech & Language Stack (Fully Operational)

### 3.1. Multilingual Bhashini Pipeline
SvaraAI implements native integration with the Government of India Bhashini ecosystem across 10 major scheduled languages and Indian English:
- **Languages Supported**: Hindi (`hi`), Bengali (`bn`), Tamil (`ta`), Telugu (`te`), Marathi (`mr`), Gujarati (`gu`), Kannada (`kn`), Malayalam (`ml`), Odia (`or`), Punjabi (`pa`), and Indian English (`en`).
- **Automatic Speech Recognition (ASR)**: Captures 16 kHz raw PCM audio directly via `AudioRecorder`, streaming chunks to Bhashini endpoints with silence thresholding and dynamic energy calculation.
- **Neural Machine Translation (NMT)**: IndicTrans2 bridges user voice utterances with localized menu data and restaurant staff dialects in sub-second translation cycles.
- **Text-to-Speech (TTS)**: High-fidelity natural voice synthesis providing spoken auditory confirmation for every screen state, bill line item, and order modification.

### 3.2. Indian English Phonetic Normalizer & Code-Switching Engine
Restaurant ordering in India frequently involves heavy code-switching (e.g., *"Ek Masala Dosa aur do filter coffee dena"* or *"Get one plate Chole Bhature with extra bhatura"*). Standard global speech engines routinely fail on Indian food vocabulary.

To resolve this, SvaraAI includes a dedicated `DishNormalizer` powered by phonetic Levenshtein distance metrics and soundex hashing against a comprehensive dictionary of 150+ regional Indian dishes:
- Automatically rectifies phonetic misinterpretations (e.g., *"do sa"* -> *Dosa*, *"paneer butter masala"* -> *Paneer Butter Masala*, *"idly"* -> *Idli*).
- Normalizes quantity keywords (*"ek"*, *"do"*, *"teen"*, *"one"*, *"two"*, *"half"*, *"full"*, *"single"*, *"double"*).
- Extracts dietary modifiers (*"without onion"*, *"jain"*, *"less spicy"*, *"extra butter"*).

### 3.3. Offline Fallback & Resiliency Engine
To prevent complete system lockup in basement restaurants or areas with dead cellular reception, an autonomous offline fallback system activates transparently:
- **TTS Fallback**: Automatically switches from cloud Bhashini neural TTS to local Android `TextToSpeech` engine whenever network roundtrip times exceed 1800ms.
- **Pre-Cached Lexicon**: Caches standard menu items, navigation earcons, and system prompts locally on device storage.
- **Graceful Error Handling**: Implements `Result<T>` monads across all network repositories to prevent application crashes on connection drops.

### 3.4. Dual-Sensory Accessibility Framework
The user interface adheres to strict WCAG AAA guidelines designed for non-visual and low-vision operation:
- **Haptic Feedback Engine (`HapticFeedbackEngine`)**: Six distinct hardware vibration patterns (`CONFIRMATION`, `ERROR`, `EDGE_WARNING`, `NAVIGATION`, `CLICK`, `ITEM_ADDED`).
- **Earcon Audio Engine (`EarconEngine`)**: Low-latency synthesized waveform tones (`ORDER_CONFIRMED`, `LISTEN_START`, `LISTEN_STOP`, `ERROR_ALERT`) giving instant spatial and state awareness.
- **Visual Design Palette**: True OLED black background (`#121212`), Saffron accessibility primary (`#FF9933`), High-Contrast Yellow (`#FFD700`), Veg Green (`#00A86B`), and Non-Veg Red (`#D32F2F`) with minimum 48dp touch targets and live high-contrast caption overlays.

---

# [PAGE 2 OF 4] Implemented & Fully Operational Capabilities

## 1. Conversational Voice Ordering & Persistent Cart

The ordering module enables entirely hands-free ordering through voice interaction, supporting conversational updates, item removal, and custom cooking instructions:
- **Spoken Input & Real-Time Intent Extraction**: Diners speak naturally into the mic. The intent extractor parses the item name, count, and special instructions into structured entities (`CartItemEntity`).
- **Custom Cooking Instructions**: Handles requests such as *"Make the Paneer Tikka less spicy"* or *"Send filter coffee without sugar"*, persisting these notes directly into the database.
- **Cart Synchronization**: Backed by Room DB with live Kotlin Flows, ensuring cart updates propagate instantly to the screen, live caption bar, and bill calculation engine without data loss.
- **Auditory Cart Narration**: A single tap reads out the entire cart summary, including item quantities, individual prices, customized notes, and subtotal.

---

## 2. Waiter Interaction Bridge & Multi-Lingual Phrase Board

To assist deaf, mute, and non-verbal diners during table service, SvaraAI includes a dual-display communication bridge:
- **Outward Flipped Display**: Displays large, high-contrast text cards rotated 180 degrees so the diner can present their screen directly to the waiter.
- **Multi-Lingual Voice Translation**: Translates and vocalizes frequent restaurant requests into the waiter's preferred regional language (Hindi, Telugu, Tamil, Kannada, Marathi, Bengali, etc.).
- **Quick Request Chips**: Instant access buttons for common dining needs (*"Please bring drinking water"*, *"Extra spoons and bowls"*, *"Is this spicy?"*, *"Call the manager"*, *"Bring the bill"*).

---

## 3. Deterministic Smart Bill Calculator & GST Tax Engine

Restaurant billing requires absolute mathematical precision. Floating-point arithmetic in mobile apps can introduce rounding errors. SvaraAI implements a deterministic integer-paise financial calculation engine:
- **Integer-Paise Arithmetic (`BillCalculator`)**: All calculations operate strictly on `Long` values representing paise ($1\text{ Rupee} = 100\text{ paise}$), eliminating binary floating-point rounding artifacts.
- **Statutory Indian GST Compliance**: Automatically computes Central GST ($2.5\%$) and State GST ($2.5\%$) in accordance with Indian restaurant tax laws.
- **Optional Service Charge & Tips**: Configurable toggle for 5% service charge and discretionary staff tips.
- **Auditory Bill Breakdown (`BillSpeechNarrator`)**: Generates structured, natural-sounding voice summaries verbalized in any selected Indian language (e.g., *"Your subtotal is 460 rupees. Central GST is 11 rupees and 50 paise. State GST is 11 rupees and 50 paise. Total bill is 506 rupees."*).

---

## 4. Multi-Diner Bill Splitting (Equal & Item-by-Item)

The billing module provides robust split calculations for group dining:
- **Equal Splitting with Fair Remainder Distribution**: Evenly divides total paise among $N$ diners. Any non-divisible remainder paise are allocated fairly to the first diners so the sum of individual shares precisely equals the total bill down to the exact paisa.
- **Item-by-Item Personalized Splitting**: Diners can assign specific ordered dishes to individual participants. Taxes and service charges are proportionally calculated based on each participant's item subtotal.
- **Individual Spoken Share Summaries**: Each participant can tap to hear their exact individual share read aloud in their preferred language.
- **Multiple Input Channels**: Diners can add dishes to the bill via voice mic commands (*"Add 2 Filter Coffee"*), quick-add dish chips, or manual entry.

---

# [PAGE 3 OF 4] Current Failure Points & Root Cause Analysis

While the core speech, ordering, waiter communication, and bill splitting engines are fully functional, **the automated vision-based camera menu capture and camera-driven interactive menu navigation systems are currently broken in real-world conditions**.

---

## 1. Summary of Current Vision & Capture Failure Points

### 1.1. False-Positive Object Triggers
- **Failure**: The camera analysis loop frequently mistakes non-menu objects (laptop keyboards, browser tabs, tables, cutlery) for food items.
- **Impact**: The camera captures irrelevant scenes automatically, generating false items.

### 1.2. Layout Collapse on Multi-Column Menus
- **Failure**: Real Indian restaurant menus use complex multi-column layouts, decorative fonts, and laminations with glare.
- **Impact**: Cloud OCR flattens columns into a single text stream, detaching category headers from their dishes and losing section hierarchy.

### 1.3. Price-Item Misalignment
- **Failure**: Unstructured OCR output causes the language model to pair prices with the wrong neighboring items (e.g., beverage price assigned to a main course).
- **Impact**: Inaccurate item pricing and missing dish details.

### 1.4. Network Latency & Audio Narration Lag
- **Failure**: Uploading full-resolution images to cloud OCR and LLM endpoints takes 4 to 8 seconds on typical mobile connections.
- **Impact**: The audio overview stalls or freezes, leaving visually impaired diners without immediate feedback.

---

## 2. Root Cause Analysis of Pipeline Bottlenecks

| Pipeline Step | Current Loophole | Core Root Cause |
| :--- | :--- | :--- |
| **Image Analysis** | Triggers capture on arbitrary textures | Simple edge contrast check without document contour validation |
| **Text Extraction** | Multi-column text merged together | OCR text sent as raw string without retaining 2D spatial coordinates |
| **Item Structuring** | Prices shift between adjacent items | LLM prompt receives unstructured flat text lacking line-by-line anchors |
| **Audio Overview** | Long delays before speech starts | Synchronous end-to-end cloud processing before initiating audio playback |

---

# [PAGE 4 OF 4] Solutions for Existing Pipeline & Pending Initiatives

## 1. Targeted Fixes for Current Pipeline Loopholes

Rather than redesigning the system, the existing camera and OCR pipeline will be stabilized through direct targeted fixes:

```
+------------------------------------------------------------------------------------+
|                         EXISTING PIPELINE FIXES                                    |
+------------------------------------------------------------------------------------+
|  1. CONTOUR VALIDATION       | Enforce 4-corner document boundary detection in      |
|                              | CameraX before triggering image capture.             |
|  2. SPATIAL OCR PAIRING      | Retain (X, Y) word coordinates during OCR to keep    |
|                              | category headers and item prices strictly aligned.   |
|  3. STRUCTURED PROMPTING     | Provide spatial row anchors to the LLM to prevent   |
|                              | price drifting across columns.                       |
|  4. LOCAL AUDIO PRE-CACHING  | Speak instant local earcon and state cues while      |
|                              | background text structuring completes.              |
+------------------------------------------------------------------------------------+
```

- **Fix 1: Document Boundary Validation**: Add a local quadrilateral contour check in CameraX analysis to ensure capture only occurs when a genuine rectangular document is detected, eliminating keyboard and background false positives.
- **Fix 2: Coordinate-Aware OCR Ingestion**: Update the OCR parsing layer to preserve spatial $(X, Y)$ coordinates for each detected word block, maintaining column separation before sending text for categorization.
- **Fix 3: Structured Line-Item Prompting**: Refine the extraction prompt to enforce strict line-by-line price association using the spatial coordinates, preventing price hallucination.
- **Fix 4: Progressive Audio Streaming**: Play immediate local audio cues upon capture and stream category names as they are parsed, removing the 4–8 second audio freeze.

---

## 2. Pending Capabilities

- **Tactile Tabletop Spatial Guide**: An assistive spatial audio guide using a standard clock-face reference (e.g., *"Water glass at 2 o'clock, main plate at 6 o'clock"*) to help blind diners locate items on their table.
- **Deep Allergen Safety Engine**: An ingredient rule engine flagging common allergens (nuts, gluten, dairy, mustard) and cross-contamination risks for sensitive diners.
- **Kitchen POS Integration**: Direct transmission of placed orders to restaurant Point of Sale printers and kitchen display systems.

---

## 3. System Status Summary

| Capability | Status | Notes |
| :--- | :--- | :--- |
| **Bhashini Speech & TTS** | Working | 10 Indian languages + Indian English operational |
| **Phonetic Dish Normalizer** | Working | 150+ regional dishes normalized with fuzzy matching |
| **Dual-Sensory System** | Working | 6 haptic profiles, synthesized earcons, WCAG AAA theme |
| **Voice Ordering & Cart** | Working | Speech-to-cart, dietary customizations, Room DB sync |
| **Waiter Bridge Screen** | Working | 180° outward flipped card and multi-lingual voice phrases |
| **Smart Bill & GST Engine** | Working | Integer-paise calculations, 2.5% CGST + 2.5% SGST |
| **Multi-Diner Splitter** | Working | Equal split with remainder handling & item-by-item split |
| **Camera Menu Capture** | Broken | False-positive triggers, column collapse, audio lag |
| **Spatial Tabletop Guide** | Pending | Clock-face tableware orientation to be built |
| **Allergen Safety Engine** | Pending | Detailed ingredient rule engine to be built |

---
*Documentation Updated: October 2026 | SvaraAI Technical Documentation*
