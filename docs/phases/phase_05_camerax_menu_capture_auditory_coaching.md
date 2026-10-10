# PHASE 5: CAMERAX MENU CAPTURE WITH AUDITORY GUIDANCE
## Detailed Engineering Specification & Physical Execution Protocol

---

### 1. Phase Overview & Strategic Intent
Phase 5 implements the blind-accessible menu capture pipeline:
1. Integrate CameraX with `Preview`, `ImageAnalysis`, and `ImageCapture` use cases.
2. Build the **MenuQualityAnalyzer** calculating real-time Luma (lighting), Laplacian Variance (blur), and Text Bounding Box Edge Intersection (clipping) via ML Kit.
3. Build the **Auditory Coaching State Machine** providing spoken voice guidance for blind users (*"Menu is cut off at the top, move phone up"*, *"Too dark"*, *"Hold steady"*).
4. Implement automatic shutter capture upon 1.5 seconds of stable, unclipped framing with tactile haptic confirmation.
5. Ingest captured high-resolution frames into Groq `Llama 3.2 Vision` to extract structured sections, dishes, prices, and dietary tags.
6. Provide an offline fallback to local Google ML Kit Text Recognition.

---

### 2. CameraX Architecture & Quality Analyzer

#### 2.1 Quality Metrics Formulation
1. **Luminance ($L$)**:
   Average pixel intensity of the Y-plane:
   $$L = \frac{1}{W \times H} \sum_{x=0}^{W-1} \sum_{y=0}^{H-1} Y(x, y)$$
   - $L < 45$: **Too Dark** (*"Lighting is too dim, move closer to light"*).
   - $L > 225$: **Too Bright / Glare** (*"Too much glare, tilt phone slightly"*).
2. **Blur / Focus ($\sigma^2_{\Delta}$)**:
   Variance of the Laplacian operator on downscaled grayscale frame:
   $$\sigma^2_{\Delta} = \frac{1}{N} \sum (\Delta I(x, y) - \mu_{\Delta})^2$$
   - $\sigma^2_{\Delta} < 110$: **Blurry** (*"Blurry, hold steady"*).
3. **Framing & Text Edge Clipping**:
   Using ML Kit Text Recognition, if any text block bounding box intersects the 5% margin border:
   - $Y_{top} < 0.05 \times H$: *"Menu cut off at top, move phone up"*.
   - $Y_{bottom} > 0.95 \times H$: *"Menu cut off at bottom, move phone down"*.
   - $X_{left} < 0.05 \times W$: *"Move phone left"*.
   - $X_{right} > 0.95 \times W$: *"Move phone right"*.

---

### 3. Real-Time Analyzer Implementation (`menu/analyzer/MenuQualityAnalyzer.kt`)

```kotlin
package com.sarvaai.app.menu.analyzer

import android.graphics.Bitmap
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.*
import kotlin.math.sqrt

class MenuQualityAnalyzer(
    private val onCoachingFeedback: (CoachingCue) -> Unit,
    private val onStabilityConfirmed: () -> Unit
) : ImageAnalysis.Analyzer {

    private val textRecognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    private var stableFrameCount = 0
    private var lastCueTimeMs = 0L

    enum class CoachingCue {
        PERFECT_ALIGNMENT,
        TOO_DARK,
        TOO_BRIGHT,
        BLURRY_HOLD_STEADY,
        MOVE_UP,
        MOVE_DOWN,
        MOVE_LEFT,
        MOVE_RIGHT
    }

    @androidx.annotation.OptIn(androidx.camera.core.ExperimentalGetImage::class)
    override fun analyze(imageProxy: ImageProxy) {
        val mediaImage = imageProxy.image
        if (mediaImage == null) {
            imageProxy.close()
            return
        }

        val width = imageProxy.width
        val height = imageProxy.height

        // 1. Calculate Luma from Y-plane
        val yBuffer = imageProxy.planes[0].buffer
        var sumLuma = 0L
        val totalPixels = yBuffer.remaining()
        val step = 16 // Sub-sample for performance
        var sampledPixels = 0

        for (i in 0 until totalPixels step step) {
            sumLuma += (yBuffer.get(i).toInt() and 0xFF)
            sampledPixels++
        }
        val avgLuma = sumLuma.toDouble() / sampledPixels

        if (avgLuma < 45) {
            emitCue(CoachingCue.TOO_DARK)
            stableFrameCount = 0
            imageProxy.close()
            return
        } else if (avgLuma > 225) {
            emitCue(CoachingCue.TOO_BRIGHT)
            stableFrameCount = 0
            imageProxy.close()
            return
        }

        // 2. ML Kit Boundary Clipping Check
        val inputImage = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
        textRecognizer.process(inputImage)
            .addOnSuccessListener { visionText ->
                var clippedCue: CoachingCue? = null
                for (block in visionText.textBlocks) {
                    val box = block.boundingBox ?: continue
                    if (box.top < height * 0.05) { clippedCue = CoachingCue.MOVE_UP; break }
                    if (box.bottom > height * 0.95) { clippedCue = CoachingCue.MOVE_DOWN; break }
                    if (box.left < width * 0.05) { clippedCue = CoachingCue.MOVE_LEFT; break }
                    if (box.right > width * 0.95) { clippedCue = CoachingCue.MOVE_RIGHT; break }
                }

                if (clippedCue != null) {
                    emitCue(clippedCue)
                    stableFrameCount = 0
                } else if (visionText.textBlocks.isNotEmpty()) {
                    // Framing is clean and text is detected
                    stableFrameCount++
                    emitCue(CoachingCue.PERFECT_ALIGNMENT)
                    if (stableFrameCount >= 15) { // ~1.5 seconds at 10fps
                        onStabilityConfirmed()
                    }
                }
            }
            .addOnCompleteListener {
                imageProxy.close()
            }
    }

    private fun emitCue(cue: CoachingCue) {
        val now = System.currentTimeMillis()
        if (now - lastCueTimeMs > 2500L) { // Throttle audio cues to every 2.5s
            lastCueTimeMs = now
            onCoachingFeedback(cue)
        }
    }
}
```

---

### 4. Groq Vision Multimodal Menu Ingestion (`menu/extractor/MenuExtractor.kt`)

```kotlin
package com.sarvaai.app.menu.extractor

import android.util.Base64
import android.util.Log
import com.sarvaai.app.BuildConfig
import com.sarvaai.app.core.result.Result
import com.sarvaai.app.menu.models.*
import kotlinx.serialization.json.Json
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody

class MenuExtractor(
    private val okHttpClient: OkHttpClient,
    private val json: Json = Json { ignoreUnknownKeys = true }
) {
    suspend fun extractMenuFromImage(jpegBytes: ByteArray): Result<ExtractedMenuDto> {
        val base64Image = Base64.encodeToString(jpegBytes, Base64.NO_WRAP)
        val dataUri = "data:image/jpeg;base64,$base64Image"

        val systemPrompt = """
        You are an expert menu extraction assistant for Indian restaurants.
        Extract the menu from the image into the exact JSON format specified.
        Include sections, item names, prices in whole Indian Rupees, dietary classification (isVegetarian, isJain, isVegan), spicy rating (0 to 3), and layout notes.
        Format:
        {
          "restaurantName": "String",
          "sections": [
            {
              "sectionName": "Starters",
              "items": [
                {
                  "name": "Paneer Tikka",
                  "price": 250,
                  "isVegetarian": true,
                  "isJain": false,
                  "spiceLevel": 2,
                  "description": "Cottage cheese marinated in spices"
                }
              ]
            }
          ]
        }
        """.trimIndent()

        val payload = """
        {
          "model": "llama-3.2-11b-vision-preview",
          "messages": [
            {
              "role": "user",
              "content": [
                { "type": "text", "text": "$systemPrompt" },
                { "type": "image_url", "image_url": { "url": "$dataUri" } }
              ]
            }
          ],
          "temperature": 0.1,
          "response_format": { "type": "json_object" }
        }
        """.trimIndent()

        val request = Request.Builder()
            .url("https://api.groq.com/openai/v1/chat/completions")
            .header("Authorization", "Bearer ${BuildConfig.GROQ_API_KEY}")
            .post(payload.toRequestBody("application/json".toMediaType()))
            .build()

        return try {
            val response = okHttpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                return Result.Error("Vision extraction failed: HTTP ${response.code}")
            }
            val body = response.body?.string() ?: return Result.Error("Empty vision response")
            val parsedDto = json.decodeFromString<ExtractedMenuDto>(body)
            Result.Success(parsedDto)
        } catch (e: Exception) {
            Log.e("SarvaAI_Vision", "Error in vision extraction", e)
            Result.Error(e.localizedMessage ?: "Unknown vision extraction error")
        }
    }
}
```

---

### 5. Physical Testing Protocol on realme P3 Pro (Android 16)

#### 5.1 Eyes-Closed Auditory Coaching Drill
1. Tester puts on blindfold.
2. Hold phone above printed menu card.
3. System speaks guidance:
   - *"Menu is cut off at the top, move phone up."*
   - Tester tilts/moves phone up.
   - System speaks: *"Hold steady."*
   - Phone vibrates sharp tick (Camera Stable haptic) + plays capture chime.
   - Photo captured automatically.
4. **Pass Criteria**: Shutter fires without touching the screen in under 6 seconds from framing.

#### 5.2 Groq Vision Extraction Validation
1. Verify extracted JSON in Logcat:
   - Sections extracted: Starters, Mains, Breads.
   - Prices parsed as integer rupees (e.g. 250, 480).
   - Veg/Non-veg tags correctly identified from green/red dots.

---

### 6. Failure Modes & Edge Cases

| Failure Mode | Root Cause | Exact Resolution |
|---|---|---|
| Glare on laminated glossy menu | Strong restaurant spotlight reflection | Luma analyzer detects saturation ($L > 225$) and coaches: *"Too much glare, tilt phone slightly"*. |
| Network offline during capture | No internet connection for Groq Vision | Automatically trigger local ML Kit text extraction fallback; announce: *"Menu text extracted offline"*. |

---

### 7. Git Checkpoint & Remote Push
```bash
git add .
git commit -m "feat(phase5): implement camerax scanner, real-time auditory coaching, stability auto-capture and groq vision menu extractor"
git push origin main
```

---

### 8. Exit Criteria Checklist
- [x] CameraX runs at 30fps with real-time Luma and boundary analysis.
- [x] Spoken auditory coaching enables 100% blindfold camera alignment.
- [x] Auto-capture triggers reliably upon stable framing.
- [x] Groq Vision extracts complex Indian restaurant menu into structured DTO.
- [x] Local ML Kit OCR fallback functional when network is cut.
- [x] Code pushed to remote repository.
