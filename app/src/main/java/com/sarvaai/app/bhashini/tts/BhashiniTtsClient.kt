package com.sarvaai.app.bhashini.tts

import android.content.Context
import android.util.Base64
import android.util.Log
import com.sarvaai.app.BuildConfig
import com.sarvaai.app.bhashini.asr.AsrNmtClient
import com.sarvaai.app.bhashini.models.AnuvaadHubMtRequest
import com.sarvaai.app.bhashini.models.AnuvaadHubMtResponse
import com.sarvaai.app.bhashini.models.AnuvaadHubTtsRequest
import com.sarvaai.app.bhashini.models.AnuvaadHubTtsResponse
import com.sarvaai.app.core.network.OkHttpFactory
import com.sarvaai.app.core.result.Result
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest

data class TtsSynthesisResult(
    val originalText: String,
    val spokenText: String,
    val audioFile: File?,
    val audioUrl: String?,
    val targetLanguage: String,
    val latencyMs: Long
)

class BhashiniTtsClient(
    private val context: Context? = null,
    private val okHttpClient: OkHttpClient = OkHttpFactory.defaultClient,
    private val json: Json = Json { ignoreUnknownKeys = true; isLenient = true; encodeDefaults = true }
) {
    companion object {
        private const val TAG = "BhashiniTtsClient"

        // AnuvaadHub Sandbox Dedicated TTS Endpoints (Primary + Fallbacks)
        private val PRIMARY_TTS_ENDPOINTS = mapOf(
            "hi" to "https://canvas.iiit.ac.in/sandboxbeprod/generate_tts/67bca89ae0b95a6a1ea34a92", // IITM_TTS_Hindi
            "te" to "https://canvas.iiit.ac.in/sandboxbeprod/generate_tts/67bca880e0b95a6a1ea34a91", // IITM_TTS_Telugu
            "en" to "https://canvas.iiit.ac.in/sandboxbeprod/generate_tts/67bca8b3e0b95a6a1ea34a93"  // IITM_TTS_English
        )

        private val FALLBACK_TTS_ENDPOINTS = mapOf(
            "hi" to "https://canvas.iiit.ac.in/sandboxbeprod/generate_tts/68c1244c747a9002950e94f8", // FastSpeech2_Hindi
            "te" to "https://canvas.iiit.ac.in/sandboxbeprod/generate_tts/68d2468f08531bc3c5ccf47d"  // FastSpeech2_Telugu
        )

        // English -> Native Target MT Endpoints
        private val MT_EN_TO_TARGET_ENDPOINTS = mapOf(
            "hi" to "https://canvas.iiit.ac.in/sandboxbeprod/check_model_status_and_infer/67b86729b5cc0eb92316383c", // English -> Hindi
            "te" to "https://canvas.iiit.ac.in/sandboxbeprod/check_model_status_and_infer/67b86729b5cc0eb92316389a"  // English -> Telugu
        )
    }

    private val cacheDir: File? by lazy {
        context?.let { ctx ->
            File(ctx.cacheDir, "tts_cache").apply { mkdirs() }
        }
    }

    private fun getCacheKey(text: String, lang: String): String {
        val md = MessageDigest.getInstance("MD5")
        val digest = md.digest("$lang:$text".toByteArray())
        return digest.joinToString("") { "%02x".format(it) }
    }

    /**
     * Sanitizes text to remove problematic symbols (e.g. ₹, &, %, /) that cause backend TTS models to return HTTP 422 or 500.
     */
    fun sanitizeForTts(text: String, lang: String): String {
        var t = text
            .replace("₹", if (lang == "hi") " रुपये " else if (lang == "te") " రూపాయలు " else " rupees ")
            .replace("&", if (lang == "hi") " और " else if (lang == "te") " మరియు " else " and ")
            .replace("+", " and ")
            .replace("/", " ")
            .replace("\\", " ")
            .replace("-", " ")
            .replace("_", " ")

        // Remove unsupported punctuation/brackets while keeping Indic and Latin alphabets, numbers, and standard pauses
        t = t.replace(Regex("[()\\[\\]{}<>@#\$%^*~`\"'|]"), " ")
        return t.replace(Regex("\\s+"), " ").trim()
    }

    private fun isAlreadyInTargetScript(text: String, lang: String): Boolean {
        return when (lang) {
            "hi" -> text.any { it in '\u0900'..'\u097F' }
            "te" -> text.any { it in '\u0C00'..'\u0C7F' }
            else -> false
        }
    }

    /**
     * Translates English text to [targetLanguage] if necessary, sanitizes content, and synthesizes audio speech.
     */
    suspend fun synthesizeSpeech(
        englishText: String,
        targetLanguage: String,
        gender: String = "female"
    ): Result<TtsSynthesisResult> = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        val lang = targetLanguage.lowercase()
        val token = if (BuildConfig.BHASHINI_INFERENCE_KEY.isNotEmpty()) BuildConfig.BHASHINI_INFERENCE_KEY else AsrNmtClient.DEFAULT_ACCESS_TOKEN

        val cleanInput = englishText.trim()
        if (cleanInput.isBlank() || !cleanInput.any { it.isLetterOrDigit() }) {
            Log.w(TAG, "Skipping TTS: input text has no speakable characters ('$englishText')")
            return@withContext Result.Error("No speakable text provided for TTS")
        }

        // Step 1: Translation if required and not already in target Indic script
        var textToSpeak = cleanInput
        val alreadyTargetScript = isAlreadyInTargetScript(cleanInput, lang)

        if (lang != "en" && !alreadyTargetScript) {
            val mtEndpoint = MT_EN_TO_TARGET_ENDPOINTS[lang]
            if (mtEndpoint != null) {
                try {
                    val mtPayload = json.encodeToString(AnuvaadHubMtRequest.serializer(), AnuvaadHubMtRequest(inputText = cleanInput))
                    val mtRequest = Request.Builder()
                        .url(mtEndpoint)
                        .header("access-token", token)
                        .post(mtPayload.toRequestBody("application/json".toMediaType()))
                        .build()

                    val mtResponse = okHttpClient.newCall(mtRequest).execute()
                    val mtBody = mtResponse.body?.string()
                    if (mtResponse.isSuccessful && !mtBody.isNullOrEmpty()) {
                        val parsed = json.decodeFromString<AnuvaadHubMtResponse>(mtBody)
                        val translated = parsed.data?.outputText?.trim()
                        if (!translated.isNullOrEmpty() && translated.any { it.isLetterOrDigit() }) {
                            textToSpeak = translated
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Translation before TTS failed, falling back to original text", e)
                }
            }
        }

        // Step 2: Sanitize text for TTS synthesis (replaces ₹ with 'rupees', cleans punctuation)
        val sanitizedText = sanitizeForTts(textToSpeak, lang)
        if (sanitizedText.isBlank() || !sanitizedText.any { it.isLetterOrDigit() }) {
            return@withContext Result.Error("Sanitized text is empty")
        }

        // Check local disk cache
        val cacheKey = getCacheKey(sanitizedText, lang)
        val cachedFile = cacheDir?.let { File(it, "$cacheKey.wav") }
        if (cachedFile != null && cachedFile.exists() && cachedFile.length() > 0) {
            Log.i(TAG, "TTS Cache HIT for [$lang] '$sanitizedText'")
            return@withContext Result.Success(
                TtsSynthesisResult(
                    originalText = cleanInput,
                    spokenText = sanitizedText,
                    audioFile = cachedFile,
                    audioUrl = null,
                    targetLanguage = lang,
                    latencyMs = System.currentTimeMillis() - startTime
                )
            )
        }

        // Step 3: Synthesize TTS with primary endpoint and fallback support
        val primaryUrl = PRIMARY_TTS_ENDPOINTS[lang] ?: PRIMARY_TTS_ENDPOINTS["en"]!!
        val fallbackUrl = FALLBACK_TTS_ENDPOINTS[lang]

        val ttsPayload = json.encodeToString(
            AnuvaadHubTtsRequest.serializer(),
            AnuvaadHubTtsRequest(text = sanitizedText, gender = gender)
        )

        var ttsResult = executeTtsCall(primaryUrl, token, ttsPayload, sanitizedText, cleanInput, lang, startTime)
        if (ttsResult is Result.Error && fallbackUrl != null) {
            Log.w(TAG, "Primary TTS failed (${ttsResult.message}), attempting fallback model: $fallbackUrl")
            ttsResult = executeTtsCall(fallbackUrl, token, ttsPayload, sanitizedText, cleanInput, lang, startTime)
        }

        ttsResult
    }

    private fun executeTtsCall(
        endpointUrl: String,
        token: String,
        ttsPayload: String,
        sanitizedText: String,
        originalText: String,
        lang: String,
        startTime: Long
    ): Result<TtsSynthesisResult> {
        val ttsRequest = Request.Builder()
            .url(endpointUrl)
            .header("access-token", token)
            .post(ttsPayload.toRequestBody("application/json".toMediaType()))
            .build()

        return try {
            val ttsResponse = okHttpClient.newCall(ttsRequest).execute()
            val ttsBody = ttsResponse.body?.string()
            if (!ttsResponse.isSuccessful || ttsBody.isNullOrEmpty()) {
                Log.e(TAG, "TTS generation error: HTTP ${ttsResponse.code} - $ttsBody")
                return Result.Error("AnuvaadHub TTS failed: HTTP ${ttsResponse.code}")
            }

            val parsedTts = json.decodeFromString<AnuvaadHubTtsResponse>(ttsBody)
            val s3Url = parsedTts.data?.s3Url
            val base64Audio = parsedTts.data?.audioContent

            var resolvedAudioFile: File? = null
            val cacheKey = getCacheKey(sanitizedText, lang)
            val destFile = cacheDir?.let { File(it, "$cacheKey.wav") } ?: File.createTempFile("tts_", ".wav", context?.cacheDir)

            if (!s3Url.isNullOrEmpty()) {
                val downloadRequest = Request.Builder().url(s3Url).build()
                val downloadResponse = okHttpClient.newCall(downloadRequest).execute()
                val audioBytes = downloadResponse.body?.bytes()
                if (audioBytes != null && audioBytes.isNotEmpty()) {
                    FileOutputStream(destFile).use { it.write(audioBytes) }
                    resolvedAudioFile = destFile
                }
            } else if (!base64Audio.isNullOrEmpty()) {
                val audioBytes = Base64.decode(base64Audio, Base64.DEFAULT)
                FileOutputStream(destFile).use { it.write(audioBytes) }
                resolvedAudioFile = destFile
            }

            val latency = System.currentTimeMillis() - startTime
            Log.i(TAG, "TTS Synthesis success in ${latency}ms for [$lang]: '$sanitizedText'")

            Result.Success(
                TtsSynthesisResult(
                    originalText = originalText,
                    spokenText = sanitizedText,
                    audioFile = resolvedAudioFile,
                    audioUrl = s3Url,
                    targetLanguage = lang,
                    latencyMs = latency
                )
            )
        } catch (e: Exception) {
            Log.e(TAG, "Exception during TTS generation", e)
            Result.Error("TTS Connection Error: ${e.localizedMessage}", e)
        }
    }
}
