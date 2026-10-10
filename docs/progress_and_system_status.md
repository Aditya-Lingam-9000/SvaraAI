# SvaraAI: Technical Progress Report & System Status Documentation

---

# [PAGE 1 OF 4] Executive Overview, Architecture & Core Speech Stack

## 1. Executive Summary

**SvaraAI** is an assistive, voice-first, dual-sensory mobile platform engineered specifically for visually impaired, blind, low-vision, deaf, and mute diners in Indian restaurants. Dining independently in public establishments presents severe accessibility hurdles: inaccessible printed physical menus, noisy ambient acoustic environments, multi-lingual staff interactions, and complex bill calculations with Indian tax regulations.

SvaraAI bridges this gap by unifying multilingual Indian speech recognition, conversational voice ordering, a bidirectional waiter communication bridge, deterministic tax computation, and digital UPI payment settlement into a single, high-contrast, multi-sensory application.

```
+-----------------------------------------------------------------------------------+
|                                 SVARA AI PLATFORM                                 |
+-----------------------------------------------------------------------------------+
|   INPUT LAYER       | 🎙️ Bhashini Multilingual ASR  | 📳 Dual-Sensory Feedback    |
|   INTELLIGENCE      | 🧠 Groq Conversational LLM     | 🔤 Phonetic Dish Normalizer |
|   STORAGE & TAX     | 💾 Room DB SQLite Persistence  | 🧮 Integer-Paise GST Engine  |
|   OUTPUT & COMM     | 🔊 Bhashini / Android TTS      | 💳 NPCI UPI Payment Bridge  |
+-----------------------------------------------------------------------------------+
```

---

## 2. Technology Stack & Architectural Foundation

The platform is constructed entirely in modern native Android technologies to guarantee high frame rates, deterministic state management, and rapid hardware-level haptic/audio triggering:

- **Programming Language**: Kotlin 1.9.24 / JVM Target 17
- **UI Toolkit**: Jetpack Compose with declarative Material 3 accessible styling
- **Asynchronous Concurrency**: Kotlin Coroutines, StateFlow, and SharedFlow
- **Local Persistence**: Room SQLite Object Relational Mapping (`AppDatabase`, `MenuDao`, `CartDao`, `OrderDao`)
- **Speech & Translation Infrastructure**: Bhashini ULCA API Suite (ASR Conformer, IndicTrans2 NMT, Indic TTS)
- **Conversational Intelligence**: Groq LLM API with high-throughput LLaMA-3.3-70B-Versatile
- **Hardware Integrations**: Android CameraX (preview & image analysis), `Vibrator` / `VibratorManager`, Audio Track synthesizers, and NPCI UPI deep-linking protocols

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
- **Earcon Audio Engine (`EarconEngine`)**: Low-latency synthesized waveform tones (`ORDER_CONFIRMED`, `MENU_LOADED`, `ITEM_SCANNED`, `ERROR_ALERT`, `SPLIT_COMPLETED`) giving instant spatial and state awareness.
- **Visual Design Palette**: True OLED black background (`#121212`), Saffron accessibility primary (`#FF9933`), High-Contrast Yellow (`#FFD700`), Veg Green (`#00A86B`), and Non-Veg Red (`#D32F2F`) with minimum 48dp touch targets and live high-contrast caption overlays.

---

# [PAGE 2 OF 4] Implemented & Fully Operational Capabilities

```
+------------------------------------------------------------------------------------+
|                         OPERATIONAL SYSTEM CAPABILITIES                            |
+------------------------------------------------------------------------------------+
|  1. VOICE-FIRST ORDERING CART     |  2. WAITER INTERACTION BRIDGE                  |
|  - Real-time Speech-to-Cart       |  - Outward Flipped High-Contrast Display       |
|  - Dietary Preference Extraction  |  - Multilingual Instant Phrase Board           |
|  - Live Room DB Persistence       |  - Bilingual Waiter Speech Synthesis           |
+-----------------------------------+- ----------------------------------------------+
|  3. DETERMINISTIC BILL CALCULATOR |  4. MULTI-DINER NPCI UPI SETTLEMENT            |
|  - Exact Integer-Paise Arithmetic |  - Equal Split with Remainder Handling         |
|  - 5% Indian GST (CGST + SGST)    |  - Item-by-Item Custom Assignment              |
|  - Spoken Auditory Breakdown      |  - Direct App Intents (GPay, PhonePe, Paytm)   |
+------------------------------------------------------------------------------------+
```

## 1. Conversational Voice Ordering & Persistent Cart

The ordering module enables entirely hands-free ordering through voice interaction, supporting conversational updates, item removal, and custom cooking instructions:
- **Spoken Input & Real-Time Intent Extraction**: Diners speak naturally into the mic. The intent extractor parses the item name, count, and special instructions into structured entities (`CartItemEntity`).
- **Custom Cooking Instructions**: Handles requests such as *"Make the Paneer Tikka less spicy"* or *"Send filter coffee without sugar"*, persisting these notes directly into the database.
- **Cart Synchronization**: Backed by Room DB with live Kotlin Flows, ensuring cart updates propagate instantly to the screen, live caption bar, and bill calculation engine without data loss.
- **Auditory Cart Narration**: A single tap reads out the entire cart summary, including item quantities, individual prices, customized notes, and subtotal.

---

## 2. Waiter Interaction Bridge & Multi-Lingual Phrase Board

Dining in restaurants requires frequent, quick interactions with restaurant staff who may speak a different language. For deaf, mute, or speech-impaired diners, this interaction is a major point of friction. SvaraAI provides a comprehensive communication bridge:

```
+-------------------------------------------------------------------+
|            DUAL-SCREEN OUTWARD WAITER COMMUNICATOR                |
|                                                                   |
|   +-----------------------------------------------------------+   |
|   |                  [FLIPPED 180° FOR WAITER]                |   |
|   |                                                           |   |
|   |       "कृपया थोड़ा अतिरिक्त पानी लाएं"                     |   |
|   |       (PLEASE BRING EXTRA WATER)                          |   |
|   |                                                           |   |
|   |         🔊 [SPEAK IN HINDI]      ❌ [DISMISS]             |   |
|   +-----------------------------------------------------------+   |
|                                                                   |
|   DINER CONTROLS:                                                 |
|   [💧 Water]   [🥣 Extra Sambar]   [🧂 Less Spicy]   [💳 Bill]    |
+-------------------------------------------------------------------+
```

- **Dual-Screen Outward Display**: Renders a dedicated full-screen card rotated 180 degrees with giant high-contrast typography so the diner can hold their phone facing the waiter.
- **Instant Spoken Translation**: Renders and speaks common restaurant phrases in the waiter's preferred dialect (e.g. Hindi, Tamil, Telugu, Kannada, Marathi, Bengali) at the press of a button.
- **Pre-Configured Quick Requests**: Fast-access chips for frequent table requests (*"Please bring drinking water"*, *"Get extra spoons and bowls"*, *"Is this dish spicy?"*, *"Please call the manager"*, *"Bring the final bill"*).
- **Custom Phrase Input**: Allows diners to type or speak custom queries which are instantly translated and rendered outward.

---

## 3. Deterministic Smart Bill Calculator & GST Tax Engine

Restaurant billing requires absolute mathematical precision. Floating-point errors in mobile apps can lead to missing paise, regulatory non-compliance, and split discrepancies. SvaraAI implements a deterministic integer-paise financial calculation engine:

```
+--------------------------------------------------------------------+
|                  DETERMINISTIC BILL BREAKDOWN                      |
+--------------------------------------------------------------------+
|  Item Subtotal:                                      Rs. 460.00    |
|  CGST (2.5% Central GST):                             Rs.  11.50    |
|  SGST (2.5% State GST):                               Rs.  11.50    |
|  Restaurant Service Charge (Optional 5%):             Rs.  23.00    |
|  Tip / Rounding Adjustment:                           Rs.   0.00    |
+--------------------------------------------------------------------+
|  TOTAL PAYABLE AMOUNT:                               Rs. 506.00    |
+--------------------------------------------------------------------+
```

- **Integer-Paise Arithmetic (`BillCalculator`)**: All calculations operate strictly on `Long` values representing paise ($1\text{ Rupee} = 100\text{ paise}$), eliminating binary floating-point rounding artifacts.
- **Statutory Indian GST Compliance**: Automatically computes Central GST ($2.5\%$) and State GST ($2.5\%$) in accordance with Indian restaurant tax laws.
- **Optional Service Charge & Tips**: Configurable toggle for 5% service charge and discretionary staff tips.
- **Auditory Bill Breakdown (`BillSpeechNarrator`)**: Generates structured, natural-sounding voice summaries verbalized in any selected Indian language (e.g., *"Your subtotal is 460 rupees. Central GST is 11 rupees and 50 paise. State GST is 11 rupees and 50 paise. Total bill is 506 rupees."*).

---

## 4. Multi-Diner Bill Splitting & NPCI UPI Intent Settlement

```
+--------------------------------------------------------------------+
|                    BILL SPLITTING MODES                            |
+--------------------------------------------------------------------+
|  [MODE A: EQUAL SPLIT]                                             |
|  Total: Rs. 506.00  |  3 Diners                                    |
|  - Diner 1: Rs. 168.68 (Receives +1p remainder adjustment)          |
|  - Diner 2: Rs. 168.66                                             |
|  - Diner 3: Rs. 168.66                                             |
+--------------------------------------------------------------------+
|  [MODE B: ITEM-BY-ITEM ASSIGNMENT]                                 |
|  - Diner 1 (Masala Dosa + Filter Coffee + Tax share):  Rs. 187.00   |
|  - Diner 2 (Paneer Tikka + Roti + Tax share):         Rs. 319.00   |
+--------------------------------------------------------------------+
```

- **Equal Splitting with Fair Remainder Distribution**: Evenly divides total paise among $N$ diners. Any non-divisible remainder paise are allocated fairly to the first diners so the sum of individual shares precisely equals the total bill down to the exact paisa.
- **Item-by-Item Personalized Splitting**: Diners can assign specific ordered dishes to individual participants. Taxes and service charges are proportionally calculated based on each participant's item subtotal.
- **NPCI UPI Intent Generator (`UpiIntentGenerator`)**: Constructs standardized `upi://pay` URIs with Merchant Category Code (MCC), transaction reference IDs, and recipient VPA.
- **Direct UPI App Launching**: Deep-links directly to installed payment applications (Google Pay `gpay://`, PhonePe `phonepe://`, Paytm `paytmmp://`, or system chooser) pre-filled with the exact individual share amount.
- **On-Screen High-Contrast QR Code**: Generates on-screen QR codes so friends at the table can scan and pay their share directly from their own devices.

---

# [PAGE 3 OF 4] Current Limitations, Failures & Deep Root Cause Analysis

While the core speech, ordering, waiter communication, and bill splitting engines are fully functional, **the automated vision-based camera menu capture and camera-driven interactive menu navigation systems are currently broken and non-operational in real-world conditions**.

```
+-------------------------------------------------------------------------------------+
|                      VISION & MENU CAPTURE FAILURE ANALYSIS                         |
+-------------------------------------------------------------------------------------+
|  FAILURE MODE 1: False Positive Object Detection (Keyboards, screens as food)      |
|  FAILURE MODE 2: Layout Collapse on Multi-Column / Decorative Menus                 |
|  FAILURE MODE 3: OCR-to-LLM Network Roundtrip Stalling & High Latency               |
|  FAILURE MODE 4: Price-Item Association Drift & Missing Category Headers           |
+-------------------------------------------------------------------------------------+
```

---

## 1. Breakdown of Vision & Camera Capture Failures

### 1.1. False-Positive Triggers & Object Misidentification
The automated camera framing engine was designed to analyze video frames and guide visually impaired users to position their camera over a printed menu. In practical real-world testing:
- The system repeatedly misidentifies non-food objects—such as computer keyboards, browser tabs, patterned tablecloths, and cutlery—as menu items.
- The steady-state detector triggers capture events prematurely, capturing blurry or completely irrelevant images even when no physical menu is present.

### 1.2. Failure on Real-World Indian Restaurant Menu Layouts
Physical restaurant menus in India deviate drastically from uniform digital documents:
- **Complex Multi-Column Formats**: Menus frequently place categories side-by-side, use non-standard decorative fonts, include laminated glossy sheets causing severe light reflection, and mix English and regional scripts.
- **OCR Hierarchy Collapse**: The optical character recognition engine flattens column structures into a single text stream, causing category headings (e.g., *South Indian Specials*, *Tandoor Starters*) to become detached from their respective items.

### 1.3. Latency Bottlenecks & Network Timeouts
- The vision pipeline relies on sending high-resolution images to cloud APIs (Bhashini OCR / Groq Vision).
- In real restaurant environments with variable mobile data connections, uploading multi-megabyte image payloads introduces a 4 to 8 second delay.
- This high latency blocks the audio narration pipeline, creating uncomfortable pauses where the visually impaired user receives no auditory feedback on whether the scan succeeded or failed.

### 1.4. Price-Item Misalignment and Hallucination
- When processing multi-column menus, the language model frequently misaligns prices with neighboring items (e.g., assigning the price of a beverage to an expensive main course).
- Variations such as *"Half / Full"* pricing structures or *"Taxes Extra"* annotations are frequently dropped or misinterpreted as dish names.

---

## 2. Root Cause Analysis Matrix

| Component | Intended Behavior | Actual Failure State | Root Cause |
| :--- | :--- | :--- | :--- |
| **Camera Framing Guidance** | Guide blind user to frame menu edges accurately | Triggers randomly on keyboards and background tables | Reliance on simple edge contrast rather than a trained local document detection model |
| **OCR Text Extraction** | Extract clean structured text from printed paper | Merges adjacent columns and garbles regional font scripts | Generic cloud OCR engines lack layout-aware document bounding-box awareness |
| **LLM Menu Structuring** | Categorize dishes, prices, and dietary tags | Misses headers, misaligns prices, hallucinates missing data | Unstructured OCR string inputs lose spatial $(X, Y)$ coordinate relationships |
| **Auditory Layout Overview** | Provide instant spoken summary of available categories | Takes 6–10 seconds to respond or fails silently on timeout | Synchronous serial chain: Image Capture $\rightarrow$ Cloud OCR $\rightarrow$ LLM Structuring $\rightarrow$ Cloud TTS |

---

# [PAGE 4 OF 4] Pending Initiatives, Technical Debt & Next Steps Roadmap

```
+-------------------------------------------------------------------------------------+
|                            STRATEGIC ROADMAP & NEXT STEPS                           |
+-------------------------------------------------------------------------------------+
|  NEAR TERM (Immediate Fixes)       |  MEDIUM TERM (Spatial & Safety Enhancements)   |
|  - QR-First Menu Ingestion         |  - Tactile Clock-Face Spatial Table Guide      |
|  - On-Device Document Detector     |  - Deep Ingredient & Allergen Graph            |
|  - Pre-Formatted Digital Menus     |  - Direct Kitchen POS Integration              |
+------------------------------------+------------------------------------------------+
```

---

## 1. Pending Technical Initiatives

### 1.1. Tactile & Spatial Table Layout Guidance
Visually impaired diners face significant challenges orienting items on their physical dining table (locating water glasses, hot curry bowls, plates, and spoons without knocking them over).
- **Pending Implementation**: A spatial table narrator using a standard clock-face reference system (e.g., *"Water glass is at 2 o'clock, main plate is at 6 o'clock, hot dal bowl is at 11 o'clock"*).
- **Technical Requirement**: On-device object detection model running locally in real-time to track table boundaries and tableware coordinates without cloud latency.

### 1.2. Deep Ingredient Knowledge & Allergen Safety Engine
While basic dietary preferences (*"jain"*, *"veg"*, *"non-veg"*) are recognized by the voice cart, a comprehensive allergen cross-contamination safety engine remains pending:
- **Pending Implementation**: Rule-based ingredient graph mapping regional dishes to hidden allergens (peanuts, gluten, mustard, dairy/lactose, soy, sesame, tree nuts).
- **Severity-Tiered Alerts**: Explicit auditory warnings when a user with declared allergies requests a dish with high cross-contamination risk (e.g., food fried in shared peanut oil).

### 1.3. End-to-End Restaurant POS & Kitchen Display Integration
- Currently, placed orders are stored locally in the device's Room database and displayed on screen.
- Direct digital transmission to restaurant Point of Sale (POS) printers and kitchen display systems (KDS) via local network Wi-Fi / Bluetooth or cloud webhook integration remains to be built.

---

## 2. Technical Debt & Corrective Architecture Plan

To resolve the camera capture limitations and complete the remaining platform scope, the following architectural improvements are planned:

```
                  PROPOSED HYBRID MENU INGESTION PIPELINE
                  
                 +---------------------------------------+
                 |          USER ARRIVES AT TABLE        |
                 +---------------------------------------+
                                     |
                    +----------------+----------------+
                    |                                 |
           [PRIMARY: FAST PATH]              [SECONDARY: FALLBACK]
                    |                                 |
       +-------------------------+       +-------------------------+
       | Scan Table QR Code      |       | On-Device Document Edge |
       | (Direct Digital JSON)   |       | Detector (Local TFLite) |
       +-------------------------+       +-------------------------+
                    |                                 |
                    | Instant Load                    | Spatial Bounding Box OCR
                    v                                 v
       +-----------------------------------------------------------+
       |             STRUCTURED IN-MEMORY MENU DATABASE            |
       |  - Verified Category Hierarchies                         |
       |  - Exact Prices & Dietary Classifications                |
       |  - Instant Sub-50ms Spoken Auditory Navigation           |
       +-----------------------------------------------------------+
```

1. **Shift to QR-First Digital Ingestion**:
   - Provide restaurants with standard table QR codes that instantly supply pre-verified, structured JSON menu catalogs directly to the app. This completely bypasses optical scanning errors, provides 100% price accuracy, and loads in under 100 milliseconds.
2. **On-Device Edge Vision Model for Physical Menus**:
   - Replace generic cloud image uploading with an on-device lightweight document detector (TensorFlow Lite / OpenVINO). The on-device model will verify valid rectangular menu boundaries before capturing, preventing background misidentification.
3. **Bounding-Box Aware Spatial OCR**:
   - When optical scanning is required, retain bounding-box $(X, Y)$ coordinates for each word to maintain column integrity and ensure prices are tied directly to their neighboring text labels.

---

## 3. Current System Component Status Summary

| System Component | Functional Status | Verification & Readiness |
| :--- | :--- | :--- |
| **Bhashini Multilingual ASR & TTS** | Operational | Tested across 10 Indian languages + Indian English |
| **Phonetic Dish Normalizer** | Operational | 150+ regional dishes verified with fuzzy matching |
| **Dual-Sensory Haptic & Earcon System** | Operational | 6 haptic profiles and audio cues active |
| **Conversational Voice Ordering Cart** | Operational | Live speech-to-cart with dietary notes |
| **Dual-Screen Outward Waiter Bridge** | Operational | 180° flipped display and instant audio playback |
| **Deterministic GST & Paise Engine** | Operational | 100% integer arithmetic, 2.5% CGST + 2.5% SGST |
| **Equal & Itemized UPI Splitter** | Operational | NPCI deep links, remainder handling, QR generation |
| **Camera Menu Capture & OCR Parsing** | Broken | High latency, layout collapse, false triggers |
| **Spatial Tabletop Layout Guide** | Pending | Requires on-device real-time object tracking |
| **Deep Allergen Graph Engine** | Pending | Detailed ingredient taxonomy to be integrated |

---
*Report Generated: October 2026 | SvaraAI Engineering & Accessibility Core Documentation*
