package com.sarvaai.app.menu.analyzer

import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions

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

class MenuQualityAnalyzer(
    private val onCoachingFeedback: (CoachingCue) -> Unit,
    private val onStabilityConfirmed: () -> Unit
) : ImageAnalysis.Analyzer {

    private val textRecognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    private var stableFrameCount = 0
    private var lastCueTimeMs = 0L
    private var isProcessingMlKit = false

    @androidx.annotation.OptIn(ExperimentalGetImage::class)
    override fun analyze(imageProxy: ImageProxy) {
        val mediaImage = imageProxy.image
        if (mediaImage == null || isProcessingMlKit) {
            imageProxy.close()
            return
        }

        val width = imageProxy.width
        val height = imageProxy.height

        // 1. Calculate Average Luma from Y-plane
        val yBuffer = imageProxy.planes[0].buffer
        var sumLuma = 0L
        val totalPixels = yBuffer.remaining()
        val step = 16 // Sub-sample for speed
        var sampledPixels = 0

        for (i in 0 until totalPixels step step) {
            sumLuma += (yBuffer.get(i).toInt() and 0xFF)
            sampledPixels++
        }
        val avgLuma = if (sampledPixels > 0) sumLuma.toDouble() / sampledPixels else 128.0

        if (avgLuma < 40) {
            emitCue(CoachingCue.TOO_DARK)
            stableFrameCount = 0
            imageProxy.close()
            return
        } else if (avgLuma > 235) {
            emitCue(CoachingCue.TOO_BRIGHT)
            stableFrameCount = 0
            imageProxy.close()
            return
        }

        // 2. ML Kit Text Boundary Clipping Check
        isProcessingMlKit = true
        val inputImage = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)

        textRecognizer.process(inputImage)
            .addOnSuccessListener { visionText ->
                var clippedCue: CoachingCue? = null
                val blocks = visionText.textBlocks

                for (block in blocks) {
                    val box = block.boundingBox ?: continue
                    if (box.top < height * 0.05) {
                        clippedCue = CoachingCue.MOVE_UP
                        break
                    }
                    if (box.bottom > height * 0.95) {
                        clippedCue = CoachingCue.MOVE_DOWN
                        break
                    }
                    if (box.left < width * 0.05) {
                        clippedCue = CoachingCue.MOVE_LEFT
                        break
                    }
                    if (box.right > width * 0.95) {
                        clippedCue = CoachingCue.MOVE_RIGHT
                        break
                    }
                }

                if (clippedCue != null) {
                    emitCue(clippedCue)
                    stableFrameCount = 0
                } else if (blocks.isNotEmpty()) {
                    // Framing is clean and text is detected
                    stableFrameCount++
                    emitCue(CoachingCue.PERFECT_ALIGNMENT)
                    if (stableFrameCount >= 10) { // ~1.0-1.5 seconds of clean framing
                        onStabilityConfirmed()
                        stableFrameCount = 0
                    }
                }
            }
            .addOnFailureListener {
                // Ignore transient frame recognition failures
            }
            .addOnCompleteListener {
                isProcessingMlKit = false
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
