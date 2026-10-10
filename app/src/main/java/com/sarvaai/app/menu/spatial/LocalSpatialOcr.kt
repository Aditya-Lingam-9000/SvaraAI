package com.sarvaai.app.menu.spatial

import android.graphics.BitmapFactory
import android.graphics.Rect
import android.util.Log
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.sarvaai.app.menu.models.ExtractedItemDto
import com.sarvaai.app.menu.models.ExtractedMenuDto
import com.sarvaai.app.menu.models.ExtractedSectionDto
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

data class SpatialLine(
    val text: String,
    val bounds: Rect,
    val isHeaderCandidate: Boolean = false,
    val hasPrice: Boolean = false,
    val priceValue: Long? = null
)

object LocalSpatialOcr {
    private const val TAG = "LocalSpatialOcr"
    private val PRICE_REGEX = Regex("(?:[₹\$]|rs\\.?|inr)?\\s*(\\d{2,5})(?:\\s*/-)?", RegexOption.IGNORE_CASE)

    suspend fun extractSpatialText(jpegBytes: ByteArray): String = suspendCancellableCoroutine { cont ->
        if (jpegBytes.isEmpty()) {
            cont.resume("")
            return@suspendCancellableCoroutine
        }

        val bitmap = BitmapFactory.decodeByteArray(jpegBytes, 0, jpegBytes.size)
        if (bitmap == null) {
            cont.resume("")
            return@suspendCancellableCoroutine
        }

        val image = InputImage.fromBitmap(bitmap, 0)
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

        recognizer.process(image)
            .addOnSuccessListener { visionText ->
                val lines = extractAndClusterLines(visionText, bitmap.width, bitmap.height)
                cont.resume(lines)
            }
            .addOnFailureListener { e ->
                Log.w(TAG, "ML Kit OCR failed: ${e.message}")
                cont.resume("")
            }
    }

    private fun extractAndClusterLines(visionText: Text, imgWidth: Int, imgHeight: Int): String {
        val rawLines = mutableListOf<SpatialLine>()

        for (block in visionText.textBlocks) {
            for (line in block.lines) {
                val text = line.text.trim()
                if (text.isBlank()) continue
                val box = line.boundingBox ?: Rect(0, 0, 0, 0)

                val priceMatch = PRICE_REGEX.find(text)
                val priceVal = priceMatch?.groupValues?.get(1)?.toLongOrNull()

                val isAllUpper = text.length > 3 && text.all { it.isUpperCase() || !it.isLetter() }
                val isHeader = (isAllUpper || text.contains("SPECIAL", true) || text.contains("MENU", true)) && priceVal == null

                rawLines.add(
                    SpatialLine(
                        text = text,
                        bounds = box,
                        isHeaderCandidate = isHeader,
                        hasPrice = priceVal != null,
                        priceValue = priceVal
                    )
                )
            }
        }

        if (rawLines.isEmpty()) return ""

        // Multi-Column Detection
        val midX = imgWidth / 2
        val leftColumn = rawLines.filter { it.bounds.centerX() < midX }
        val rightColumn = rawLines.filter { it.bounds.centerX() >= midX }

        val isTwoColumnLayout = leftColumn.size >= 3 && rightColumn.size >= 3 &&
                leftColumn.any { it.hasPrice } && rightColumn.any { it.hasPrice }

        val orderedLines = if (isTwoColumnLayout) {
            // Sort Column 1 by Y, then Column 2 by Y
            val col1 = leftColumn.sortedBy { it.bounds.top }
            val col2 = rightColumn.sortedBy { it.bounds.top }
            col1 + col2
        } else {
            // Single column layout: Sort strictly by top coordinate Y, then left coordinate X
            rawLines.sortedWith(compareBy({ it.bounds.top }, { it.bounds.left }))
        }

        return orderedLines.joinToString("\n") { it.text }
    }

    /**
     * Deterministic local fallback menu builder if cloud network is down.
     */
    fun parseDeterministicFallback(ocrText: String): ExtractedMenuDto {
        val lines = ocrText.lines().map { it.trim() }.filter { it.isNotBlank() }
        if (lines.isEmpty()) {
            return ExtractedMenuDto(restaurantName = "Restaurant Menu", sections = emptyList())
        }

        var currentSectionTitle = "Main Menu"
        val sectionsMap = mutableMapOf<String, MutableList<ExtractedItemDto>>()

        for (line in lines) {
            val priceMatch = Regex("(.*?)(?:[₹\$]|rs\\.?|inr)?\\s*(\\d{2,5})(?:\\s*/-)?$", RegexOption.IGNORE_CASE).find(line)

            if (priceMatch != null) {
                val rawName = priceMatch.groupValues[1].replace(Regex("[.\\-_]+$"), "").trim()
                val price = priceMatch.groupValues[2].toLongOrNull() ?: 100L

                if (rawName.length >= 2 && !rawName.all { it.isDigit() }) {
                    val isNonVeg = rawName.contains("chicken", true) || rawName.contains("mutton", true) ||
                            rawName.contains("fish", true) || rawName.contains("egg", true) ||
                            rawName.contains("prawn", true) || rawName.contains("meat", true)

                    val item = ExtractedItemDto(
                        name = rawName,
                        price = price,
                        isVegetarian = !isNonVeg,
                        isJain = !isNonVeg && !rawName.contains("onion", true) && !rawName.contains("garlic", true) && !rawName.contains("potato", true) && !rawName.contains("aloo", true) && !rawName.contains("batata", true),
                        isVegan = !isNonVeg && !rawName.contains("paneer", true) && !rawName.contains("butter", true) && !rawName.contains("ghee", true) && !rawName.contains("curd", true),
                        isNutFree = true,
                        spiceLevel = if (rawName.contains("spicy", true) || rawName.contains("masala", true) || rawName.contains("chilli", true)) 2 else 1,
                        description = ""
                    )
                    sectionsMap.getOrPut(currentSectionTitle) { mutableListOf() }.add(item)
                }
            } else {
                // Potential section header
                if (line.length in 3..40 && (line.all { it.isUpperCase() || !it.isLetter() } ||
                            line.contains("special", true) || line.contains("combo", true) ||
                            line.contains("dosa", true) || line.contains("starter", true) ||
                            line.contains("curry", true) || line.contains("bread", true) ||
                            line.contains("rice", true) || line.contains("beverage", true) ||
                            line.contains("dessert", true) || line.contains("tiffin", true))) {
                    currentSectionTitle = line.replace(Regex("[^a-zA-Z0-9 &/-]"), "").trim()
                }
            }
        }

        val sections = sectionsMap.map { (title, items) ->
            ExtractedSectionDto(sectionName = title, items = items)
        }

        return ExtractedMenuDto(
            restaurantName = "Restaurant Menu",
            sections = sections
        )
    }
}
