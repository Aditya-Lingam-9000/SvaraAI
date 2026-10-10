package com.sarvaai.app.bhashini.ocr

import android.util.Log
import com.sarvaai.app.core.network.OkHttpFactory
import com.sarvaai.app.core.result.Result
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

class BhashiniOcrClient(
    private val okHttpClient: OkHttpClient = OkHttpFactory.defaultClient,
    private val json: Json = Json { ignoreUnknownKeys = true; isLenient = true }
) {
    companion object {
        private const val TAG = "BhashiniOcrClient"
        const val DEFAULT_ACCESS_TOKEN = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJ1c2VyX2lkIjoiNmFjYTM2MmE2YWQ0MDM3OTU1Y2Q4YmVhIiwicm9sZSI6Im1lZ2F0aG9uX3N0dWRlbnQiLCJleHAiOjE3OTE4NjU4MDB9.MvE-w5j4jErD7lHlYpd8ibEe8F4Dj4HJHNLtGtkXR2k"

        // Official IIIT-Hyderabad / AnuvaadHub Canvas Sandbox OCR Endpoints
        val LANGUAGE_OCR_ENDPOINTS = mapOf(
            "en" to "https://canvas.iiit.ac.in/sandboxbeprod/check_ocr_status_and_infer/6937cbf473b6e4905f682970",
            "english" to "https://canvas.iiit.ac.in/sandboxbeprod/check_ocr_status_and_infer/6937cbf473b6e4905f682970",
            "hi" to "https://canvas.iiit.ac.in/sandboxbeprod/check_ocr_status_and_infer/6937cc3573b6e4905f682974",
            "hindi" to "https://canvas.iiit.ac.in/sandboxbeprod/check_ocr_status_and_infer/6937cc3573b6e4905f682974",
            "te" to "https://canvas.iiit.ac.in/sandboxbeprod/check_ocr_status_and_infer/6937ce2573b6e4905f682991",
            "telugu" to "https://canvas.iiit.ac.in/sandboxbeprod/check_ocr_status_and_infer/6937ce2573b6e4905f682991",
            "ta" to "https://canvas.iiit.ac.in/sandboxbeprod/check_ocr_status_and_infer/6937ce0773b6e4905f68298f",
            "tamil" to "https://canvas.iiit.ac.in/sandboxbeprod/check_ocr_status_and_infer/6937ce0773b6e4905f68298f",
            "kn" to "https://canvas.iiit.ac.in/sandboxbeprod/check_ocr_status_and_infer/6937cc6573b6e4905f682976",
            "kannada" to "https://canvas.iiit.ac.in/sandboxbeprod/check_ocr_status_and_infer/6937cc6573b6e4905f682976",
            "mr" to "https://canvas.iiit.ac.in/sandboxbeprod/check_ocr_status_and_infer/6937cd2473b6e4905f682981",
            "marathi" to "https://canvas.iiit.ac.in/sandboxbeprod/check_ocr_status_and_infer/6937cd2473b6e4905f682981",
            "gu" to "https://canvas.iiit.ac.in/sandboxbeprod/check_ocr_status_and_infer/6937cc1873b6e4905f682972",
            "gujarati" to "https://canvas.iiit.ac.in/sandboxbeprod/check_ocr_status_and_infer/6937cc1873b6e4905f682972",
            "bn" to "https://canvas.iiit.ac.in/sandboxbeprod/check_ocr_status_and_infer/6937c9eb73b6e4905f68296b",
            "bengali" to "https://canvas.iiit.ac.in/sandboxbeprod/check_ocr_status_and_infer/6937c9eb73b6e4905f68296b",
            "pa" to "https://canvas.iiit.ac.in/sandboxbeprod/check_ocr_status_and_infer/6937cd8873b6e4905f682987",
            "punjabi" to "https://canvas.iiit.ac.in/sandboxbeprod/check_ocr_status_and_infer/6937cd8873b6e4905f682987",
            "or" to "https://canvas.iiit.ac.in/sandboxbeprod/check_ocr_status_and_infer/6937cd5c73b6e4905f682985",
            "odia" to "https://canvas.iiit.ac.in/sandboxbeprod/check_ocr_status_and_infer/6937cd5c73b6e4905f682985",
            "ml" to "https://canvas.iiit.ac.in/sandboxbeprod/check_ocr_status_and_infer/6937ccdc73b6e4905f68297e",
            "malayalam" to "https://canvas.iiit.ac.in/sandboxbeprod/check_ocr_status_and_infer/6937ccdc73b6e4905f68297e"
        )
    }

    /**
     * Performs optical character recognition using AnuvaadHub / Bhashini Sandbox API.
     */
    suspend fun recognizeText(
        jpegBytes: ByteArray,
        languageCode: String = "en",
        accessToken: String = DEFAULT_ACCESS_TOKEN
    ): Result<String> = withContext(Dispatchers.IO) {
        if (jpegBytes.isEmpty()) {
            return@withContext Result.Error("Empty image bytes for OCR")
        }

        val url = LANGUAGE_OCR_ENDPOINTS[languageCode.lowercase()]
            ?: LANGUAGE_OCR_ENDPOINTS["en"]
            ?: return@withContext Result.Error("No OCR endpoint found for language $languageCode")

        try {
            val requestBody = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart(
                    "file",
                    "menu_capture.jpg",
                    jpegBytes.toRequestBody("image/jpeg".toMediaType())
                )
                .build()

            val request = Request.Builder()
                .url(url)
                .header("access-token", accessToken)
                .header("User-Agent", "SarvaAI-Accessibility/1.0")
                .post(requestBody)
                .build()

            val response = okHttpClient.newCall(request).execute()
            val responseBody = response.body?.string()

            if (response.isSuccessful && !responseBody.isNullOrBlank()) {
                val jsonObject = json.parseToJsonElement(responseBody).jsonObject
                val status = jsonObject["status"]?.jsonPrimitive?.content ?: ""
                if (status.equals("success", ignoreCase = true)) {
                    val dataObj = jsonObject["data"]?.jsonObject
                    val decodedText = dataObj?.get("decoded_text")?.jsonPrimitive?.content ?: ""
                    Log.i(TAG, "Bhashini OCR successful ($languageCode), extracted ${decodedText.length} chars")
                    return@withContext Result.Success(decodedText)
                } else {
                    val msg = jsonObject["message"]?.jsonPrimitive?.content ?: "Bhashini OCR returned non-success"
                    Log.w(TAG, "Bhashini OCR API Error: $msg")
                    return@withContext Result.Error(msg)
                }
            } else {
                Log.w(TAG, "Bhashini OCR HTTP ${response.code}: $responseBody")
                return@withContext Result.Error("OCR request failed with code ${response.code}")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Bhashini OCR network exception: ${e.message}")
            return@withContext Result.Error("OCR network failed: ${e.localizedMessage}")
        }
    }
}
