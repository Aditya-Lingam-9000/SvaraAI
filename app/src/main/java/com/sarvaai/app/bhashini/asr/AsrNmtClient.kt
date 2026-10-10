package com.sarvaai.app.bhashini.asr

import android.util.Base64
import android.util.Log
import com.sarvaai.app.bhashini.auth.BhashiniConfigManager
import com.sarvaai.app.bhashini.models.InferenceResponse
import com.sarvaai.app.bhashini.models.IngestionResult
import com.sarvaai.app.core.network.OkHttpFactory
import com.sarvaai.app.core.result.Result
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

class AsrNmtClient(
    private val configManager: BhashiniConfigManager = BhashiniConfigManager(),
    private val okHttpClient: OkHttpClient = OkHttpFactory.defaultClient,
    private val json: Json = Json { ignoreUnknownKeys = true; isLenient = true }
) {
    companion object {
        private const val TAG = "AsrNmtClient"
    }

    suspend fun transcribeAndTranslate(
        wavBytes: ByteArray,
        sourceLanguage: String
    ): Result<IngestionResult> = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        val base64Audio = Base64.encodeToString(wavBytes, Base64.NO_WRAP)

        var config = configManager.getValidConfig()
        val asrServiceId = config.serviceIds["asr_$sourceLanguage"] ?: config.serviceIds["asr_hi"] ?: "ai4bharat/conformer-multilingual-indo-aryan-asr"

        val isEnglish = sourceLanguage.equals("en", ignoreCase = true)
        val nmtServiceId = if (!isEnglish) {
            config.serviceIds["translation_${sourceLanguage}_en"] ?: "ai4bharat/indictrans-v2-all-gpu"
        } else null

        val pipelineTasksJson = if (!isEnglish && nmtServiceId != null) {
            """
            [
              {
                "taskType": "asr",
                "config": {
                  "language": { "sourceLanguage": "$sourceLanguage" },
                  "serviceId": "$asrServiceId",
                  "audioFormat": "wav",
                  "samplingRate": 16000
                }
              },
              {
                "taskType": "translation",
                "config": {
                  "language": { "sourceLanguage": "$sourceLanguage", "targetLanguage": "en" },
                  "serviceId": "$nmtServiceId"
                }
              }
            ]
            """.trimIndent()
        } else {
            """
            [
              {
                "taskType": "asr",
                "config": {
                  "language": { "sourceLanguage": "$sourceLanguage" },
                  "serviceId": "$asrServiceId",
                  "audioFormat": "wav",
                  "samplingRate": 16000
                }
              }
            ]
            """.trimIndent()
        }

        val payload = """
        {
          "pipelineTasks": $pipelineTasksJson,
          "inputData": {
            "audio": [
              { "audioContent": "$base64Audio" }
            ]
          }
        }
        """.trimIndent()

        val request = Request.Builder()
            .url(config.callbackUrl)
            .header(config.authHeaderName, config.authHeaderValue)
            .post(payload.toRequestBody("application/json".toMediaType()))
            .build()

        try {
            var response = okHttpClient.newCall(request).execute()

            // Handle token expiration / 401
            if (response.code == 401 || response.code == 403) {
                Log.w(TAG, "Inference token expired (HTTP ${response.code}). Refreshing pipeline config...")
                config = configManager.getValidConfig(forceRefresh = true)
                val retryRequest = request.newBuilder()
                    .header(config.authHeaderName, config.authHeaderValue)
                    .build()
                response = okHttpClient.newCall(retryRequest).execute()
            }

            val responseBody = response.body?.string()
            if (!response.isSuccessful || responseBody.isNullOrEmpty()) {
                Log.e(TAG, "Bhashini inference failed: HTTP ${response.code} body: $responseBody")
                return@withContext Result.Error("Bhashini inference failed: HTTP ${response.code}")
            }

            val inferenceResponse = json.decodeFromString<InferenceResponse>(responseBody)
            val asrTask = inferenceResponse.pipelineResponse.firstOrNull { it.taskType == "asr" }
                ?: inferenceResponse.pipelineResponse.getOrNull(0)

            val transcript = asrTask?.output?.firstOrNull()?.source ?: ""

            val nmtTask = inferenceResponse.pipelineResponse.firstOrNull { it.taskType == "translation" }
                ?: inferenceResponse.pipelineResponse.getOrNull(1)

            val translation = if (isEnglish) {
                transcript
            } else {
                nmtTask?.output?.firstOrNull()?.target ?: transcript
            }

            val latency = System.currentTimeMillis() - startTime
            Log.i(TAG, "Ingestion Success [$sourceLanguage] (${latency}ms) -> Transcript: '$transcript' | Translation: '$translation'")

            Result.Success(
                IngestionResult(
                    originalTranscript = transcript,
                    englishTranslation = translation,
                    sourceLanguage = sourceLanguage,
                    latencyMs = latency,
                    confidence = if (transcript.isNotEmpty()) 0.95 else 0.0
                )
            )
        } catch (e: Exception) {
            Log.e(TAG, "Exception during ASR+NMT processing", e)
            Result.Error(e.localizedMessage ?: "Network or inference failure", e)
        }
    }
}
