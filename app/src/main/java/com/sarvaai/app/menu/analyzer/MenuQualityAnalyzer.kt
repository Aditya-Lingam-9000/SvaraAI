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
    MOVE_RIGHT,
    SEARCHING_MENU
}

class MenuQualityAnalyzer(
    private val onCoachingFeedback: (CoachingCue) -> Unit,
    private val onStabilityConfirmed: () -> Unit
) : ImageAnalysis.Analyzer {

    private val textRecognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    private var stableFrameCount = 0
    private var lastCueTimeMs = 0L
    private var isProcessingMlKit = false

    private val keyboardNoiseTokens = listOf(
        "ctrl", "shift", "caps", "tab", "esc", "enter", "delete", "f1", "f2", "f3", "f4", "f5",
        "f6", "f7", "f8", "f9", "f10", "f11", "f12", "fn", "alt", "option", "command", "backspace",
        "prtscn", "insert", "home", "end", "pgup", "pgdn", "windows", "qwer", "asdf", "zxcv",
        "lenovo", "dell", "hp", "asus", "acer", "apple", "macbook", "intel", "amd", "touchpad",
        "keyboard", "chrome", "firefox", "http", "www", "github", "vscode", "terminal"
    )

    private val foodKeywords = listOf(
        "menu", "dosa", "idli", "curry", "paneer", "chicken", "biryani", "rice", "roti", "naan", "dal",
        "sambar", "coffee", "tea", "chai", "tiffin", "thali", "veg", "starter", "soup", "dessert",
        "beverage", "salad", "gravy", "fry", "masala", "tikka", "kebab", "bhaji", "pav", "paratha",
        "lassi", "juice", "sweet", "chutney", "vada", "poori", "chapati", "noodle", "manchurian",
        "pizza", "burger", "sandwich", "pasta", "food", "combo", "breakfast", "lunch", "dinner",
        "items", "special", "rate", "price",
        "మెనూ", "దోశ", "ఇడ్లీ", "కూర", "రైస్", "బిర్యానీ", "భోజనం", "కాఫీ", "టీ", "సాంబార్",
        "చట్నీ", "పనీర్", "చికెన్", "స్వీట్", "వాటర్", "ఆర్డర్", "బిల్లు", "ధర", "రూపాయలు", "ప్లేట్",
        "मेनू", "डोसा", "इडली", "पनीर", "चावल", "बिरयानी", "रोटी", "सब्ज़ी", "दाल", "चाय", "कॉफ़ी", "सांभर", "मिठाई", "थाली"
    )

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
                val allText = visionText.text.lowercase()

                if (blocks.isEmpty() || allText.length < 5) {
                    stableFrameCount = 0
                    return@addOnSuccessListener
                }

                // Check for keyboard / non-menu noise
                val keyboardMatchCount = keyboardNoiseTokens.count { allText.contains(it) }
                val foodMatchCount = foodKeywords.count { allText.contains(it) }
                val priceMatches = Regex("(?:₹|rs|inr|/\\-)\\s*\\d+|\\b\\d{2,4}\\s*(?:/\\-|\\.00)?\\b").findAll(allText).count()

                val isConfidentMenu = (foodMatchCount >= 1 || priceMatches >= 2) && (keyboardMatchCount < 2)

                if (!isConfidentMenu) {
                    // Not a menu (e.g. keyboard, browser tab, random room clutter)
                    stableFrameCount = 0
                    emitCue(CoachingCue.SEARCHING_MENU)
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
                val boxWidthRatio = (maxRight - minLeft).toFloat() / frameWidth
                val boxHeightRatio = (maxBottom - minTop).toFloat() / frameHeight

                // Check boundary clipping and centering
                val isCutOffTop = minTop < frameHeight * 0.06f
                val isCutOffBottom = maxBottom > frameHeight * 0.94f
                val isCutOffLeft = minLeft < frameWidth * 0.06f
                val isCutOffRight = maxRight > frameWidth * 0.94f

                val cue: CoachingCue = when {
                    isCutOffTop || centerY < 0.32f -> CoachingCue.MOVE_UP
                    isCutOffBottom || centerY > 0.68f -> CoachingCue.MOVE_DOWN
                    isCutOffLeft || centerX < 0.32f -> CoachingCue.MOVE_LEFT
                    isCutOffRight || centerX > 0.68f -> CoachingCue.MOVE_RIGHT
                    boxWidthRatio < 0.25f && boxHeightRatio < 0.15f -> CoachingCue.SEARCHING_MENU
                    else -> CoachingCue.PERFECT_ALIGNMENT
                }

                emitCue(cue)

                if (cue == CoachingCue.PERFECT_ALIGNMENT) {
                    stableFrameCount++
                    if (stableFrameCount >= 6) { // Stable for ~0.8-1.0 sec
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
