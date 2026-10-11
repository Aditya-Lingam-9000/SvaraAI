package com.sarvaai.app.bhashini.asr

import android.util.Log
import com.sarvaai.app.BuildConfig
import com.sarvaai.app.bhashini.models.AnuvaadHubAsrResponse
import com.sarvaai.app.bhashini.models.AnuvaadHubMtRequest
import com.sarvaai.app.bhashini.models.AnuvaadHubMtResponse
import com.sarvaai.app.bhashini.models.IngestionResult
import com.sarvaai.app.core.network.OkHttpFactory
import com.sarvaai.app.core.result.Result
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

class AsrNmtClient(
    private val okHttpClient: OkHttpClient = OkHttpFactory.defaultClient,
    private val json: Json = Json { ignoreUnknownKeys = true; isLenient = true; encodeDefaults = true }
) {
    companion object {
        private const val TAG = "AsrNmtClient"

        // Master JWT Access Token for AnuvaadHub Sandbox
        const val DEFAULT_ACCESS_TOKEN = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJ1c2VyX2lkIjoiNmFjYTM2MmE2YWQ0MDM3OTU1Y2Q4YmVhIiwicm9sZSI6Im1lZ2F0aG9uX3N0dWRlbnQiLCJleHAiOjE3OTE4NjU4MDB9.MvE-w5j4jErD7lHlYpd8ibEe8F4Dj4HJHNLtGtkXR2k"

        // Dedicated AnuvaadHub ASR Model Endpoints
        val ASR_ENDPOINTS = mapOf(
            "te" to "https://canvas.iiit.ac.in/sandboxbeprod/infer_asr/6979e11150f13fc15e60353b", // AI4Bharat-ASR-Telugu
            "hi" to "https://canvas.iiit.ac.in/sandboxbeprod/infer_asr/6979e11150f13fc15e603533", // AI4Bharat-ASR-Hindi
            "kn" to "https://canvas.iiit.ac.in/sandboxbeprod/infer_asr/6979e11150f13fc15e603534", // AI4Bharat-ASR-Kannada
            "ml" to "https://canvas.iiit.ac.in/sandboxbeprod/infer_asr/6979e11150f13fc15e603535", // AI4Bharat-ASR-Malayalam
            "ta" to "https://canvas.iiit.ac.in/sandboxbeprod/infer_asr/6979e11150f13fc15e60353a", // AI4Bharat-ASR-Tamil
            "en" to "https://canvas.iiit.ac.in/sandboxbeprod/infer_asr/6979e11150f13fc15e603531"  // AI4Bharat-ASR-English
        )

        // Native -> English MT Endpoints (for unified LLM reasoning)
        val MT_TO_EN_ENDPOINTS = mapOf(
            "te" to "https://canvas.iiit.ac.in/sandboxbeprod/check_model_status_and_infer/67b86729b5cc0eb923163869", // IIITH-Bhashaverse-Telugu-English
            "hi" to "https://canvas.iiit.ac.in/sandboxbeprod/check_model_status_and_infer/67b86729b5cc0eb92316384a", // IIITH-Bhashaverse-Hindi-English
            "kn" to "https://canvas.iiit.ac.in/sandboxbeprod/check_model_status_and_infer/67b86729b5cc0eb92316389d", // IIITH-Bhashaverse-Kannada-English
            "ta" to "https://canvas.iiit.ac.in/sandboxbeprod/check_model_status_and_infer/6872173a4f34535ffa89bafd", // IIITH-Bhashaverse-Tamil-English
            "ml" to "https://canvas.iiit.ac.in/sandboxbeprod/check_model_status_and_infer/687217344f34535ffa89b9d4"  // IIITH-Bhashaverse-Malayalam-English
        )

        // English -> Native Staff MT Endpoints (for restaurant staff/kitchen bridge)
        val MT_EN_TO_STAFF_ENDPOINTS = mapOf(
            "te" to "https://canvas.iiit.ac.in/sandboxbeprod/check_model_status_and_infer/67b86729b5cc0eb92316389a", // IIITH-Bhashaverse-English-Telugu
            "hi" to "https://canvas.iiit.ac.in/sandboxbeprod/check_model_status_and_infer/67b86729b5cc0eb92316383c", // IIITH-Bhashaverse-English-Hindi
            "kn" to "https://canvas.iiit.ac.in/sandboxbeprod/check_model_status_and_infer/67b86729b5cc0eb923163823", // IIITH-Bhashaverse-English-Kannada
            "ta" to "https://canvas.iiit.ac.in/sandboxbeprod/check_model_status_and_infer/67b86729b5cc0eb9231638be", // IIITH-Bhashaverse-English-Tamil
            "ml" to "https://canvas.iiit.ac.in/sandboxbeprod/check_model_status_and_infer/67b86729b5cc0eb9231638b7"  // IIITH-Bhashaverse-English-Malayalam
        )

        val SUPPORTED_LANGUAGE_CODES = setOf("te", "hi", "ta", "kn", "ml", "en")
    }

    suspend fun transcribeAndTranslate(
        wavBytes: ByteArray,
        sourceLanguage: String,
        targetStaffLanguage: String = "hi"
    ): Result<IngestionResult> = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        val lang = sourceLanguage.lowercase()

        // 1. Resolve ASR Endpoint
        val asrUrl = ASR_ENDPOINTS[lang] ?: ASR_ENDPOINTS["te"] ?: ASR_ENDPOINTS["hi"]!!
        val token = if (BuildConfig.BHASHINI_INFERENCE_KEY.isNotEmpty()) BuildConfig.BHASHINI_INFERENCE_KEY else DEFAULT_ACCESS_TOKEN

        Log.i(TAG, "Initiating AnuvaadHub ASR [$lang] -> $asrUrl (WAV bytes: ${wavBytes.size})")

        // 2. Build Multipart ASR Request
        val requestBody = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart(
                "audio_file",
                "audio_${System.currentTimeMillis()}.wav",
                wavBytes.toRequestBody("audio/wav".toMediaType())
            )
            .build()

        val asrRequest = Request.Builder()
            .url(asrUrl)
            .header("access-token", token)
            .post(requestBody)
            .build()

        val transcript: String
        try {
            val asrResponse = okHttpClient.newCall(asrRequest).execute()
            val asrBody = asrResponse.body?.string()
            if (!asrResponse.isSuccessful || asrBody.isNullOrEmpty()) {
                Log.e(TAG, "AnuvaadHub ASR error: HTTP ${asrResponse.code} - $asrBody")
                return@withContext Result.Error("AnuvaadHub ASR failed: HTTP ${asrResponse.code}")
            }

            val parsedAsr = json.decodeFromString<AnuvaadHubAsrResponse>(asrBody)
            transcript = parsedAsr.data?.recognizedText?.trim() ?: ""
            Log.i(TAG, "ASR Success: '$transcript'")
        } catch (e: Exception) {
            Log.e(TAG, "Exception during ASR request", e)
            return@withContext Result.Error("ASR Connection Error: ${e.localizedMessage}", e)
        }

        if (transcript.isEmpty()) {
            return@withContext Result.Success(
                IngestionResult(
                    originalTranscript = "",
                    englishTranslation = "",
                    sourceLanguage = lang,
                    latencyMs = System.currentTimeMillis() - startTime
                )
            )
        }

        // 3. Translation Handling
        var englishTranslation = transcript
        var staffTranslation: String? = null
        var staffTargetLanguage: String? = null

        if (lang != "en") {
            // Non-English: Translate to English for unified AI processing
            val mtUrl = MT_TO_EN_ENDPOINTS[lang]
            if (mtUrl != null) {
                try {
                    val mtPayload = json.encodeToString(AnuvaadHubMtRequest.serializer(), AnuvaadHubMtRequest(inputText = transcript))
                    val mtRequest = Request.Builder()
                        .url(mtUrl)
                        .header("access-token", token)
                        .post(mtPayload.toRequestBody("application/json".toMediaType()))
                        .build()

                    val mtResponse = okHttpClient.newCall(mtRequest).execute()
                    val mtBody = mtResponse.body?.string()
                    if (mtResponse.isSuccessful && !mtBody.isNullOrEmpty()) {
                        val parsedMt = json.decodeFromString<AnuvaadHubMtResponse>(mtBody)
                        val translatedText = parsedMt.data?.outputText?.trim()
                        if (!translatedText.isNullOrEmpty()) {
                            englishTranslation = translatedText
                        }
                    } else {
                        Log.w(TAG, "MT warning: HTTP ${mtResponse.code} - $mtBody")
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "MT request failed, falling back to original transcript", e)
                }
            }

            // Domain Lexical Normalization (e.g. 'mistakes/faults' -> 'dosas')
            englishTranslation = RestaurantLexicalNormalizer.postProcessTranslation(
                rawEnglishTranslation = englishTranslation,
                nativeTranscript = transcript,
                sourceLanguage = lang
            )
        } else {
            // English mode: English goes directly to LLM dialog engine (0ms NMT latency).
            // Concurrently generate staff translation (English -> staff language) for waiter / kitchen assistive display
            val staffMtUrl = MT_EN_TO_STAFF_ENDPOINTS[targetStaffLanguage.lowercase()] ?: MT_EN_TO_STAFF_ENDPOINTS["hi"]
            if (staffMtUrl != null) {
                try {
                    val mtPayload = json.encodeToString(AnuvaadHubMtRequest.serializer(), AnuvaadHubMtRequest(inputText = transcript))
                    val mtRequest = Request.Builder()
                        .url(staffMtUrl)
                        .header("access-token", token)
                        .post(mtPayload.toRequestBody("application/json".toMediaType()))
                        .build()

                    val mtResponse = okHttpClient.newCall(mtRequest).execute()
                    val mtBody = mtResponse.body?.string()
                    if (mtResponse.isSuccessful && !mtBody.isNullOrEmpty()) {
                        val parsedMt = json.decodeFromString<AnuvaadHubMtResponse>(mtBody)
                        val translatedText = parsedMt.data?.outputText?.trim()
                        if (!translatedText.isNullOrEmpty()) {
                            staffTranslation = translatedText
                            staffTargetLanguage = when (targetStaffLanguage.lowercase()) {
                                "te" -> "Telugu"
                                "hi" -> "Hindi"
                                "ta" -> "Tamil"
                                "kn" -> "Kannada"
                                "ml" -> "Malayalam"
                                else -> "English"
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Staff MT translation failed gracefully", e)
                }
            }
        }

        val totalLatency = System.currentTimeMillis() - startTime
        Log.i(TAG, "Ingestion Complete in ${totalLatency}ms: Native='$transcript' | English='$englishTranslation' | Staff='$staffTranslation'")

        Result.Success(
            IngestionResult(
                originalTranscript = transcript,
                englishTranslation = englishTranslation,
                sourceLanguage = lang,
                latencyMs = totalLatency,
                staffTranslation = staffTranslation,
                staffTargetLanguage = staffTargetLanguage
            )
        )
    }
}
