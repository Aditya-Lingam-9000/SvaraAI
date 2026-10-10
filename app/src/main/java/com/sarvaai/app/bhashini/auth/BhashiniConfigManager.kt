package com.sarvaai.app.bhashini.auth

import android.content.Context
import android.util.Log
import com.sarvaai.app.BuildConfig
import com.sarvaai.app.bhashini.models.PipelineConfigResponse
import com.sarvaai.app.core.network.OkHttpFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

data class CachedPipelineConfig(
    val callbackUrl: String,
    val authHeaderName: String,
    val authHeaderValue: String,
    val serviceIds: Map<String, String>, // Key: "asr_te", "translation_te_en", "tts_te", etc.
    val expiresAtEpochMs: Long
)

class BhashiniConfigManager(
    private val okHttpClient: OkHttpClient = OkHttpFactory.defaultClient,
    private val json: Json = Json { ignoreUnknownKeys = true; isLenient = true }
) {
    private val mutex = Mutex()
    private var cachedConfig: CachedPipelineConfig? = null

    companion object {
        private const val TAG = "BhashiniConfigManager"
        private const val ULCA_CONFIG_URL = "https://meity-auth.ulcacontrib.org/ulca/apis/v0/model/getModelsPipeline"
        private const val DEFAULT_PIPELINE_ID = "64392f96daac500b55c543cd"
    }

    suspend fun getValidConfig(forceRefresh: Boolean = false): CachedPipelineConfig = mutex.withLock {
        val current = cachedConfig
        val now = System.currentTimeMillis()
        if (!forceRefresh && current != null && now < current.expiresAtEpochMs) {
            return current
        }

        withContext(Dispatchers.IO) {
            Log.i(TAG, "Fetching dynamic Bhashini pipeline config from ULCA...")
            val userId = BuildConfig.BHASHINI_USER_ID
            val apiKey = BuildConfig.BHASHINI_API_KEY
            val pipelineId = if (BuildConfig.BHASHINI_PIPELINE_ID.isNotEmpty()) {
                BuildConfig.BHASHINI_PIPELINE_ID
            } else {
                DEFAULT_PIPELINE_ID
            }

            // Fallback default service mapping if ULCA credentials are empty in development
            if (userId.isEmpty() || apiKey.isEmpty()) {
                Log.w(TAG, "Bhashini ULCA credentials not set in BuildConfig. Using standard fallback service mappings.")
                val fallbackMap = mapOf(
                    "asr_hi" to "ai4bharat/conformer-multilingual-indo-aryan-asr",
                    "asr_te" to "ai4bharat/conformer-multilingual-dravidian-asr",
                    "asr_en" to "ai4bharat/whisper-medium-en",
                    "translation_hi_en" to "ai4bharat/indictrans-v2-all-gpu",
                    "translation_te_en" to "ai4bharat/indictrans-v2-all-gpu",
                    "translation_en_hi" to "ai4bharat/indictrans-v2-all-gpu",
                    "translation_en_te" to "ai4bharat/indictrans-v2-all-gpu",
                    "translation_hi_te" to "ai4bharat/indictrans-v2-all-gpu",
                    "translation_te_hi" to "ai4bharat/indictrans-v2-all-gpu",
                    "tts_hi" to "ai4bharat/indic-tts-coqui-indo-aryan-gpu",
                    "tts_te" to "ai4bharat/indic-tts-coqui-dravidian-gpu",
                    "tts_en" to "ai4bharat/indic-tts-coqui-indo-aryan-gpu"
                )

                val fallbackConfig = CachedPipelineConfig(
                    callbackUrl = "https://dhruva-api.bhashini.gov.in/services/inference/pipeline",
                    authHeaderName = "Authorization",
                    authHeaderValue = BuildConfig.BHASHINI_INFERENCE_KEY,
                    serviceIds = fallbackMap,
                    expiresAtEpochMs = now + (24 * 60 * 60 * 1000L)
                )
                cachedConfig = fallbackConfig
                return@withContext fallbackConfig
            }

            val requestBodyJson = """
            {
              "pipelineTasks": [
                { "taskType": "asr", "config": { "language": { "sourceLanguage": "hi" } } },
                { "taskType": "asr", "config": { "language": { "sourceLanguage": "te" } } },
                { "taskType": "asr", "config": { "language": { "sourceLanguage": "en" } } },
                { "taskType": "translation", "config": { "language": { "sourceLanguage": "hi", "targetLanguage": "en" } } },
                { "taskType": "translation", "config": { "language": { "sourceLanguage": "te", "targetLanguage": "en" } } },
                { "taskType": "translation", "config": { "language": { "sourceLanguage": "en", "targetLanguage": "hi" } } },
                { "taskType": "translation", "config": { "language": { "sourceLanguage": "en", "targetLanguage": "te" } } },
                { "taskType": "tts", "config": { "language": { "sourceLanguage": "hi" } } },
                { "taskType": "tts", "config": { "language": { "sourceLanguage": "te" } } },
                { "taskType": "tts", "config": { "language": { "sourceLanguage": "en" } } }
              ],
              "pipelineRequestConfig": {
                "pipelineId": "$pipelineId"
              }
            }
            """.trimIndent()

            val request = Request.Builder()
                .url(ULCA_CONFIG_URL)
                .header("userID", userId)
                .header("ulcaApiKey", apiKey)
                .post(requestBodyJson.toRequestBody("application/json".toMediaType()))
                .build()

            try {
                val response = okHttpClient.newCall(request).execute()
                if (!response.isSuccessful) {
                    throw IOException("Failed to fetch Bhashini pipeline: HTTP ${response.code} - ${response.body?.string()}")
                }

                val bodyString = response.body?.string() ?: throw IOException("Empty Bhashini pipeline body")
                val parsed = json.decodeFromString<PipelineConfigResponse>(bodyString)

                val serviceMap = mutableMapOf<String, String>()
                parsed.pipelineResponseConfig.forEach { task ->
                    task.config.forEach { cfg ->
                        val source = cfg.language.sourceLanguage
                        val target = cfg.language.targetLanguage
                        val key = if (target != null) "${task.taskType}_${source}_${target}" else "${task.taskType}_${source}"
                        serviceMap[key] = cfg.serviceId
                    }
                }

                val config = CachedPipelineConfig(
                    callbackUrl = parsed.pipelineInferenceAPIEndPoint.endpoint,
                    authHeaderName = parsed.pipelineInferenceAPIEndPoint.inferenceApiKey.name,
                    authHeaderValue = parsed.pipelineInferenceAPIEndPoint.inferenceApiKey.value,
                    serviceIds = serviceMap,
                    expiresAtEpochMs = now + (12 * 60 * 60 * 1000L) // 12-hour TTL
                )
                cachedConfig = config
                Log.i(TAG, "Pipeline config resolved successfully with ${serviceMap.size} services.")
                config
            } catch (e: Exception) {
                Log.e(TAG, "Error fetching live pipeline config, using fallback defaults", e)
                val fallbackConfig = CachedPipelineConfig(
                    callbackUrl = "https://dhruva-api.bhashini.gov.in/services/inference/pipeline",
                    authHeaderName = "Authorization",
                    authHeaderValue = BuildConfig.BHASHINI_INFERENCE_KEY,
                    serviceIds = mapOf(
                        "asr_hi" to "ai4bharat/conformer-multilingual-indo-aryan-asr",
                        "asr_te" to "ai4bharat/conformer-multilingual-dravidian-asr",
                        "asr_en" to "ai4bharat/whisper-medium-en",
                        "translation_hi_en" to "ai4bharat/indictrans-v2-all-gpu",
                        "translation_te_en" to "ai4bharat/indictrans-v2-all-gpu",
                        "translation_en_hi" to "ai4bharat/indictrans-v2-all-gpu",
                        "translation_en_te" to "ai4bharat/indictrans-v2-all-gpu",
                        "tts_hi" to "ai4bharat/indic-tts-coqui-indo-aryan-gpu",
                        "tts_te" to "ai4bharat/indic-tts-coqui-dravidian-gpu"
                    ),
                    expiresAtEpochMs = now + (60 * 60 * 1000L)
                )
                cachedConfig = fallbackConfig
                fallbackConfig
            }
        }
    }

    fun invalidateCache() {
        cachedConfig = null
    }
}
