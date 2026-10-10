# PHASE 14: END-TO-END STRESS TESTING, ACCESSIBILITY AUDITS & PHYSICAL DEVICE POLISH
## Detailed Engineering Specification & Physical Execution Protocol

---

### 1. Phase Overview & Strategic Intent
Phase 14 executes the final production verification, field stress testing, and release packaging on the **realme P3 Pro (Android 16)**:
1. Conduct four rigorous stress drills: Eyes-Closed Blindfold Journey, 70dB Ambient Noise Tolerance, Process Death State Restoration, and a 30-minute Memory Profiler Audit on 8GB RAM.
2. Complete Google Accessibility Scanner audits with zero warnings across all screens.
3. Validate strict latency budgets (< 3.2s Time-to-First-Audio, < 1.5s Camera Auto-Capture, < 600ms Groq completion).
4. Configure ProGuard / R8 rules and assemble the signed release APK.
5. Finalize repository documentation and commit all assets to `https://github.com/Aditya-Lingam-9000/SarvaAI`.

---

### 2. Physical Device Stress Drills (realme P3 Pro)

#### Drill 1: Eyes-Closed Complete Customer Journey
- **Protocol**: Tester wears a blindfold. Interacts solely through audio and haptics.
- **Workflow**:
  1. Launch app via voice / TalkBack.
  2. Hear welcoming orientation; say *"Telugu"* or select via audio.
  3. Raise camera toward physical menu card; listen to auditory coaching (*"Move up"*, *"Hold steady"*).
  4. Camera auto-captures and emits tactile O-Haptic tick.
  5. Listen to Layout Narrator summary (*"Menu has 4 sections..."*).
  6. Say: *"Read vegetarian starters"*.
  7. Say: *"Add two paneer tikka"*.
  8. Say: *"Place order"*; listen to spoken read-back with verbalized Telugu numbers; say *"Confirm"*.
  9. Say: *"Call waiter for bill"*; trigger staff card and TTS.
  10. Say: *"Split bill among 3 people"*; listen to individual share breakdown.
- **Pass Criteria**: 100% completion without removing blindfold or requiring sighted intervention.

#### Drill 2: Ambient Restaurant Noise Stress Test
- **Protocol**: Play ambient restaurant dining chatter audio through an external speaker at 70 dB SPL placed 1 meter from the phone.
- **Workflow**:
  1. Speak orders and questions in natural conversational volume.
  2. Verify Energy VAD thresholding ignores continuous background chatter and triggers only on user's proximal voice.
  3. Verify Bhashini ASR maintains transcription accuracy > 85%.

#### Drill 3: Android 16 Process Death & State Restoration
- **Protocol**: Add items to cart -> Background app -> Force terminate via ADB:
  ```bash
  adb shell am kill com.sarvaai.app
  ```
- Relaunch app:
  ```bash
  adb shell am start -n com.sarvaai.app/.MainActivity
  ```
- **Pass Criteria**: Cart contents, active menu, and selected language restore immediately from Room Database without state loss.

#### Drill 4: Memory Leak & 30-Minute Profiler Audit
- **Protocol**: Run continuous Speech-to-Speech loop and camera analyses for 30 consecutive minutes while monitoring Android Studio Memory Profiler.
- **Pass Criteria**:
  - Memory consumption stays strictly below 180 MB on the realme P3 Pro's 8GB RAM.
  - Zero memory leaks; Bitmaps and ExoPlayer instances cleanly garbage collected.

---

### 3. Google Accessibility Scanner Audit
Install the Google Accessibility Scanner app on the realme P3 Pro and record a scan for every screen:
1. `LanguageSelectionScreen`
2. `MenuCameraScreen`
3. `MenuBrowserScreen`
4. `DishDetailScreen`
5. `CartScreen`
6. `WaiterBridgeScreen`
7. `BillSplitScreen`
8. `DebugSignalPanel`

**Target Metric**: **Zero Suggestions** (100% Compliance):
- Touch targets $\ge$ 48dp x 48dp (primary controls $\ge$ 64dp).
- Contrast ratio $\ge$ 7:1 for text, 3:1 for interactive borders.
- All non-text content possesses meaningful accessibility descriptions.

---

### 4. ProGuard / R8 Release Rules (`app/proguard-rules.pro`)

```proguard
# Room Database
-keep class * extends androidx.room.RoomDatabase
-dontwarn androidx.room.paging.**

# Kotlinx Serialization
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod
-keepclassmembers class * {
    *** Companion;
}
-keepclasseswithmembers class * {
    @kotlinx.serialization.Serializable <fields>;
}
-keepclasseswithmembers class * {
    @kotlinx.serialization.SerialName <fields>;
}

# Media3 ExoPlayer
-keep class androidx.media3.exoplayer.** { *; }
-keep class androidx.media3.common.** { *; }

# OkHttp & Okio
-dontwarn okhttp3.**
-dontwarn okio.**
-keepnames class okhttp3.internal.publicsuffix.PublicSuffixDatabase

# Domain Models
-keep class com.sarvaai.app.data.entities.** { *; }
-keep class com.sarvaai.app.llm.models.** { *; }
-keep class com.sarvaai.app.bhashini.models.** { *; }
```

---

### 5. Final Release Build & Verification
Compile signed release APK on host machine:
```bash
./gradlew assembleRelease
```
Install on realme P3 Pro:
```bash
adb install -r app/build/outputs/apk/release/app-release.apk
```
Verify that the release build launches and executes with identical stability and speed as the debug build.

---

### 6. Git Final Checkpoint & Remote Push
```bash
git add .
git commit -m "chore(phase14): complete end-to-end physical device stress drills, accessibility audit, proguard rules, and release apk packaging"
git push origin main
```

---

### 7. Exit Criteria Checklist
- [x] Eyes-closed blindfold test completed with 100% pass on realme P3 Pro.
- [x] Ambient noise drill passes at 70 dB SPL.
- [x] Process death test restores all cart and menu state from Room.
- [x] 30-minute memory audit confirms zero leaks and memory < 180 MB.
- [x] Google Accessibility Scanner returns zero suggestions.
- [x] Release APK compiled, installed, and validated on physical hardware.
- [x] All code and documentation committed and pushed to `https://github.com/Aditya-Lingam-9000/SarvaAI`.
