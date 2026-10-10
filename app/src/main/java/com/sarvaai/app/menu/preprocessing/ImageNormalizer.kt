package com.sarvaai.app.menu.preprocessing

import android.graphics.*
import android.media.ExifInterface
import android.util.Log
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import kotlin.math.max
import kotlin.math.min

object ImageNormalizer {
    private const val TAG = "ImageNormalizer"
    private const val MAX_DIMENSION = 1600

    /**
     * Corrects orientation, scales to optimal dimension for OCR, and applies contrast enhancement.
     */
    fun normalizeForOcr(rawJpegBytes: ByteArray): ByteArray {
        if (rawJpegBytes.isEmpty()) return rawJpegBytes

        try {
            // 1. Read EXIF orientation
            var rotationDegrees = 0
            try {
                val exif = ExifInterface(ByteArrayInputStream(rawJpegBytes))
                val orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
                rotationDegrees = when (orientation) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> 90
                    ExifInterface.ORIENTATION_ROTATE_180 -> 180
                    ExifInterface.ORIENTATION_ROTATE_270 -> 270
                    else -> 0
                }
            } catch (e: Exception) {
                Log.w(TAG, "Exif orientation read error: ${e.message}")
            }

            // 2. Decode bitmap bounds
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(rawJpegBytes, 0, rawJpegBytes.size, options)
            val origWidth = options.outWidth
            val origHeight = options.outHeight

            if (origWidth <= 0 || origHeight <= 0) return rawJpegBytes

            // 3. Compute sample size for memory-safe decoding
            val maxDim = max(origWidth, origHeight)
            var inSampleSize = 1
            while (maxDim / (inSampleSize * 2) >= MAX_DIMENSION) {
                inSampleSize *= 2
            }

            val decodeOptions = BitmapFactory.Options().apply {
                this.inSampleSize = inSampleSize
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            val decodedBitmap = BitmapFactory.decodeByteArray(rawJpegBytes, 0, rawJpegBytes.size, decodeOptions)
                ?: return rawJpegBytes

            // 4. Rotate if needed
            val orientedBitmap = if (rotationDegrees != 0) {
                val matrix = Matrix().apply { postRotate(rotationDegrees.toFloat()) }
                Bitmap.createBitmap(decodedBitmap, 0, 0, decodedBitmap.width, decodedBitmap.height, matrix, true)
            } else {
                decodedBitmap
            }

            // 5. Scale down precisely to MAX_DIMENSION if still larger
            val finalWidth: Int
            val finalHeight: Int
            val currentMax = max(orientedBitmap.width, orientedBitmap.height)
            if (currentMax > MAX_DIMENSION) {
                val scale = MAX_DIMENSION.toFloat() / currentMax.toFloat()
                finalWidth = (orientedBitmap.width * scale).toInt()
                finalHeight = (orientedBitmap.height * scale).toInt()
            } else {
                finalWidth = orientedBitmap.width
                finalHeight = orientedBitmap.height
            }

            val scaledBitmap = if (finalWidth != orientedBitmap.width || finalHeight != orientedBitmap.height) {
                Bitmap.createScaledBitmap(orientedBitmap, finalWidth, finalHeight, true)
            } else {
                orientedBitmap
            }

            // 6. Apply contrast and sharpness enhancement for OCR readability
            val enhancedBitmap = enhanceContrast(scaledBitmap)

            // 7. Compress to clean JPEG
            val outStream = ByteArrayOutputStream()
            enhancedBitmap.compress(Bitmap.CompressFormat.JPEG, 92, outStream)
            return outStream.toByteArray()
        } catch (e: Exception) {
            Log.e(TAG, "Normalization failed: ${e.message}", e)
            return rawJpegBytes
        }
    }

    /**
     * Enhances local contrast and brightness to make faint menu text sharp and readable.
     */
    private fun enhanceContrast(src: Bitmap): Bitmap {
        val dest = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(dest)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        // ColorMatrix: 1.25x contrast boost, subtle brightness lift (+5)
        val contrast = 1.25f
        val brightness = 5.0f
        val cm = ColorMatrix(floatArrayOf(
            contrast, 0f, 0f, 0f, brightness,
            0f, contrast, 0f, 0f, brightness,
            0f, 0f, contrast, 0f, brightness,
            0f, 0f, 0f, 1f, 0f
        ))
        paint.colorFilter = ColorMatrixColorFilter(cm)
        canvas.drawBitmap(src, 0f, 0f, paint)
        return dest
    }
}
