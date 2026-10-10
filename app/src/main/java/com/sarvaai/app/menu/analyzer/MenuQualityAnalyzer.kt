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

        val rotationDegrees = imageProxy.imageInfo.rotationDegrees
        val isRotated = rotationDegrees == 90 || rotationDegrees == 270
        val frameWidth = if (isRotated) imageProxy.height else imageProxy.width
        val frameHeight = if (isRotated) imageProxy.width else imageProxy.height

        // 1. Calculate Average Luma from Y-plane safely
        val yPlane = imageProxy.planes[0]
        val yBuffer = yPlane.buffer.asReadOnlyBuffer()
        var sumLuma = 0L
        val totalPixels = yBuffer.remaining()
        val step = 32 // Sub-sample
        var sampledPixels = 0

        var pos = 0
        while (pos < totalPixels) {
            sumLuma += (yBuffer.get(pos).toInt() and 0xFF)
            sampledPixels++
            pos += step
        }
        val avgLuma = if (sampledPixels > 0) sumLuma.toDouble() / sampledPixels else 128.0

        if (avgLuma < 15) { // Truly pitch black / lens covered
            emitCue(CoachingCue.TOO_DARK)
            stableFrameCount = 0
            imageProxy.close()
            return
        } else if (avgLuma > 245) { // Intense over-exposure glare
            emitCue(CoachingCue.TOO_BRIGHT)
            stableFrameCount = 0
            imageProxy.close()
            return
        }

        // 2. ML Kit Text Recognition for framing & alignment
        isProcessingMlKit = true
        val inputImage = InputImage.fromMediaImage(mediaImage, rotationDegrees)

        textRecognizer.process(inputImage)
            .addOnSuccessListener { visionText ->
                val blocks = visionText.textBlocks
                if (blocks.isEmpty()) {
                    // No text detected yet - do not emit random cues
                    stableFrameCount = 0
                    return@addOnSuccessListener
                }

                // Compute union bounding box across all text blocks
                var minLeft = frameWidth
                var maxRight = 0
                var minTop = frameHeight
                var maxBottom = 0

                for (block in blocks) {
                    val box = block.boundingBox ?: continue
                    if (box.left < minLeft) minLeft = box.left
                    if (box.right > maxRight) maxRight = box.right
                    if (box.top < minTop) minTop = box.top
                    if (box.bottom > maxBottom) maxBottom = box.bottom
                }

                val centerX = (minLeft + maxRight) / 2.0f / frameWidth
                val centerY = (minTop + maxBottom) / 2.0f / frameHeight

                val cue: CoachingCue = when {
                    centerX < 0.20f -> CoachingCue.MOVE_RIGHT
                    centerX > 0.80f -> CoachingCue.MOVE_LEFT
                    centerY < 0.20f -> CoachingCue.MOVE_DOWN
                    centerY > 0.80f -> CoachingCue.MOVE_UP
                    else -> CoachingCue.PERFECT_ALIGNMENT
                }

                emitCue(cue)

                if (cue == CoachingCue.PERFECT_ALIGNMENT) {
                    stableFrameCount++
                    if (stableFrameCount >= 8) { // Stable for ~1.0 sec
                        onStabilityConfirmed()
                        stableFrameCount = 0
                    }
                } else {
                    stableFrameCount = 0
                }
            }
            .addOnFailureListener {
                // Ignore transient frame recognition errors
            }
            .addOnCompleteListener {
                isProcessingMlKit = false
                imageProxy.close()
            }
    }

    private fun emitCue(cue: CoachingCue) {
        val now = System.currentTimeMillis()
        if (now - lastCueTimeMs > 3000L) { // Throttle audio cues to every 3.0s
            lastCueTimeMs = now
            onCoachingFeedback(cue)
        }
    }
}
