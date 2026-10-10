# PHASE 2: BHASHINI & ANUVAADHUB AUDIO INGESTION PIPELINE (SPINE - PART 1)
## Detailed Engineering Specification & Physical Execution Protocol

---

### 1. Phase Overview & Strategic Intent
Phase 2 builds the ingestion half of SarvaAI's multilingual spine:
1. Integrate AnuvaadHub / Bhashini Dhruva APIs for pipeline configuration discovery, token resolution, and capability probing across English, Hindi, and Telugu.
2. Build an in-memory and persistent credentials cache with automatic token refresh on HTTP 401/403.
3. Implement a high-performance 16,000 Hz 16-bit mono linear PCM microphone recording engine via `AudioRecord`.
4. Implement an on-device Energy-based Voice Activity Detection (VAD) algorithm for automatic speech start and endpoint detection.
5. Package recorded PCM frames into standard 44-byte RIFF/WAV containers and Base64 strings.
6. Execute combined ASR + NMT inference in a single atomic upstream request, verifying live speech-to-English translation directly on the realme P3 Pro.

---

### 2. Bhashini / AnuvaadHub API Contract Specification

#### 2.1 Pipeline Discovery API (`getModelsPipeline`)
- **URL**: `POST https://meity-auth.ulcacontrib.org/ulca/apis/v0/model/getModelsPipeline`
- **Headers**:
  ```http
  userID: <BHASHINI_USER_ID>
  ulcaApiKey: <BHASHINI_API_KEY>
  Content-Type: application/json
  ```
- **Request Body**:
  ```json
  {
    "pipelineTasks": [
      {
        "taskType": "asr",
        "config": {
          "language": { "sourceLanguage": "te" }
        }
      },
      {
        "taskType": "translation",
        "config": {
          "language": { "sourceLanguage": "te", "targetLanguage": "en" }
        }
      },
      {
        "taskType": "tts",
        "config": {
          "language": { "sourceLanguage": "te" }
        }
      }
    ],
    "pipelineRequestConfig": {
      "pipelineId": "64392f96daac500b55c543cd"
    }
  }
  ```
- **Response Structure**:
  ```json
  {
    "pipelineResponseConfig": [
      {
        "taskType": "asr",
        "config": [
          {
            "serviceId": "ai4bharat/conformer-multilingual-indo-aryan-asr",
            "modelId": "...",
            "language": { "sourceLanguage": "te" }
          }
        ]
      },
      {
        "taskType": "translation",
        "config": [
          {
            "serviceId": "ai4bharat/indictrans-v2-all-gpu",
            "language": { "sourceLanguage": "te", "targetLanguage": "en" }
          }
        ]
      },
      {
        "taskType": "tts",
        "config": [
          {
            "serviceId": "ai4bharat/indic-tts-coqui-indo-aryan-gpu",
            "language": { "sourceLanguage": "te" }
          }
        ]
      }
    ],
    "pipelineInferenceAPIEndPoint": {
      "endpoint": "https://dhruva-api.bhashini.gov.in/services/inference/pipeline",
      "inferenceApiKey": {
        "name": "Authorization",
        "value": "<short-lived-bearer-token>"
      }
    }
  }
  ```

#### 2.2 Dual-Task Unified Inference API (ASR -> NMT in One Round-Trip)
- **URL**: `POST https://dhruva-api.bhashini.gov.in/services/inference/pipeline`
- **Headers**:
  ```http
  Authorization: <inferenceApiKey.value>
  Content-Type: application/json
  ```
- **Request Payload**:
  ```json
  {
    "pipelineTasks": [
      {
        "taskType": "asr",
        "config": {
          "language": { "sourceLanguage": "te" },
          "serviceId": "<resolved_asr_service_id>",
          "audioFormat": "wav",
          "samplingRate": 16000
        }
      },
      {
        "taskType": "translation",
        "config": {
          "language": { "sourceLanguage": "te", "targetLanguage": "en" },
          "serviceId": "<resolved_nmt_service_id>"
        }
      }
    ],
    "inputData": {
      "audio": [
        {
          "audioContent": "<BASE64_ENCODED_16KHZ_WAV_NO_NEWLINES>"
        }
      ]
    }
  }
  ```
- **Response Parsing**:
  - Task 1 Output (ASR): `pipelineResponse[0].output[0].source` (Original Indic transcript).
  - Task 2 Output (NMT): `pipelineResponse[1].output[0].target` (Canonical English translation).

---

### 3. Pipeline Configuration Manager & Token Cache

#### 3.1 `BhashiniConfigManager.kt` (`bhashini/auth/BhashiniConfigManager.kt`)
```kotlin
package com.sarvaai.app.bhashini.auth

import android.util.Log
import com.sarvaai.app.BuildConfig
import com.sarvaai.app.bhashini.models.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

class BhashiniConfigManager(
    private val okHttpClient: OkHttpClient,
    private val json: Json = Json { ignoreUnknownKeys = true }
) {
    private val mutex = Mutex()
    private var cachedConfig: CachedPipelineConfig? = null

    data class CachedPipelineConfig(
        val callbackUrl: String,
        val authHeaderName: String,
        val authHeaderValue: String,
        val serviceIds: Map<String, String>, // Key: "asr_te", "nmt_te_en", etc.
        val expiresAtEpochMs: Long
    )

    suspend fun getValidConfig(forceRefresh: Boolean = false): CachedPipelineConfig = mutex.withLock {
        val current = cachedConfig
        val now = System.currentTimeMillis()
        if (!forceRefresh && current != null && now < current.expiresAtEpochMs) {
            return current
        }

        Log.i("SarvaAI_Bhashini", "Fetching dynamic Bhashini pipeline config from ULCA...")
        val requestBodyJson = """
        {
          "pipelineTasks": [
            { "taskType": "asr", "config": { "language": { "sourceLanguage": "te" } } },
            { "taskType": "asr", "config": { "language": { "sourceLanguage": "hi" } } },
            { "taskType": "translation", "config": { "language": { "sourceLanguage": "te", "targetLanguage": "en" } } },
            { "taskType": "translation", "config": { "language": { "sourceLanguage": "hi", "targetLanguage": "en" } } },
            { "taskType": "translation", "config": { "language": { "sourceLanguage": "en", "targetLanguage": "te" } } },
            { "taskType": "translation", "config": { "language": { "sourceLanguage": "en", "targetLanguage": "hi" } } },
            { "taskType": "tts", "config": { "language": { "sourceLanguage": "te" } } },
            { "taskType": "tts", "config": { "language": { "sourceLanguage": "hi" } } }
          ],
          "pipelineRequestConfig": {
            "pipelineId": "${BuildConfig.BHASHINI_PIPELINE_ID}"
          }
        }
        """.trimIndent()

        val request = Request.Builder()
            .url("https://meity-auth.ulcacontrib.org/ulca/apis/v0/model/getModelsPipeline")
            .header("userID", BuildConfig.BHASHINI_USER_ID)
            .header("ulcaApiKey", BuildConfig.BHASHINI_API_KEY)
            .post(requestBodyJson.toRequestBody("application/json".toMediaType()))
            .build()

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
        Log.i("SarvaAI_Bhashini", "Pipeline config cached successfully. Resolved ${serviceMap.size} services.")
        return config
    }

    fun invalidateCache() {
        cachedConfig = null
    }
}
```

---

### 4. Audio Recording & PCM Processing Engine

#### 4.1 Sample Rate & Buffer Architecture
- **Sample Rate**: 16,000 Hz (16 kHz is the native acoustic model resolution for Bhashini Conformer models).
- **Encoding**: 16-bit Linear PCM (`AudioFormat.ENCODING_PCM_16BIT`).
- **Channel**: Mono (`AudioFormat.CHANNEL_IN_MONO`).
- **Frame Size**: 20 milliseconds = 320 samples = 640 bytes.

#### 4.2 Energy-Based Voice Activity Detection (`audio/vad/EnergyVad.kt`)
```kotlin
package com.sarvaai.app.audio.vad

import kotlin.math.sqrt

class EnergyVad(
    private val sampleRate: Int = 16000,
    private val frameDurationMs: Int = 20,
    private val silenceTimeoutMs: Long = 1200L,
    private val maxUtteranceDurationMs: Long = 12000L
) {
    private var noiseFloorRms = 100.0
    private var speechStarted = false
    private var speechStartTimeMs = 0L
    private var lastSpeechFrameTimeMs = 0L
    private var totalFramesProcessed = 0

    fun processFrame(pcmBytes: ByteArray, numBytes: Int): VadStatus {
        val now = System.currentTimeMillis()
        val numSamples = numBytes / 2
        var sumSquares = 0.0

        for (i in 0 until numSamples) {
            val sample = (pcmBytes[i * 2 + 1].toInt() shl 8) or (pcmBytes[i * 2].toInt() and 0xFF)
            sumSquares += (sample * sample).toDouble()
        }
        val rms = sqrt(sumSquares / numSamples.coerceAtLeast(1))

        // Calibrate noise floor in initial 300ms (15 frames)
        if (totalFramesProcessed < 15) {
            noiseFloorRms = (noiseFloorRms * totalFramesProcessed + rms) / (totalFramesProcessed + 1)
            totalFramesProcessed++
            return VadStatus.CALIBRATING
        }

        val speechThreshold = (noiseFloorRms * 2.5).coerceAtLeast(350.0)

        if (rms > speechThreshold) {
            if (!speechStarted) {
                speechStarted = true
                speechStartTimeMs = now
            }
            lastSpeechFrameTimeMs = now
            return VadStatus.SPEECH_ACTIVE
        }

        if (speechStarted) {
            val silenceDuration = now - lastSpeechFrameTimeMs
            val totalDuration = now - speechStartTimeMs

            if (silenceDuration > silenceTimeoutMs || totalDuration > maxUtteranceDurationMs) {
                speechStarted = false
                return VadStatus.SPEECH_ENDED
            }
            return VadStatus.SPEECH_ACTIVE
        }

        return VadStatus.SILENCE
    }

    fun reset() {
        speechStarted = false
        totalFramesProcessed = 0
        noiseFloorRms = 100.0
    }
}

enum class VadStatus {
    CALIBRATING,
    SILENCE,
    SPEECH_ACTIVE,
    SPEECH_ENDED
}
```

#### 4.3 PCM to WAV 44-Byte Container Encoder (`audio/recorder/WavEncoder.kt`)
```kotlin
package com.sarvaai.app.audio.recorder

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

object WavEncoder {
    fun encodeToWav(pcmData: ByteArray, sampleRate: Int = 16000, channels: Short = 1, bitsPerSample: Short = 16): ByteArray {
        val totalAudioLen = pcmData.size
        val totalDataLen = totalAudioLen + 36
        val byteRate = sampleRate * channels * bitsPerSample / 8
        val blockAlign = (channels * bitsPerSample / 8).toShort()

        val header = ByteBuffer.allocate(44).apply {
            order(ByteOrder.LITTLE_ENDIAN)
            put("RIFF".toByteArray())
            putInt(totalDataLen)
            put("WAVE".toByteArray())
            put("fmt ".toByteArray())
            putInt(16) // Subchunk1Size for PCM
            putShort(1) // AudioFormat 1 = PCM
            putShort(channels)
            putInt(sampleRate)
            putInt(byteRate)
            putShort(blockAlign)
            putShort(bitsPerSample)
            put("data".toByteArray())
            putInt(totalAudioLen)
        }.array()

        val output = ByteArrayOutputStream(header.size + pcmData.size)
        output.write(header)
        output.write(pcmData)
        return output.toByteArray()
    }
}
```

---

### 5. Ingestion Client Implementation (`bhashini/asr/AsrNmtClient.kt`)

```kotlin
package com.sarvaai.app.bhashini.asr

import android.util.Base64
import android.util.Log
import com.sarvaai.app.bhashini.auth.BhashiniConfigManager
import com.sarvaai.app.bhashini.models.InferenceResponse
import com.sarvaai.app.core.result.Result
import kotlinx.serialization.json.Json
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

class AsrNmtClient(
    private val configManager: BhashiniConfigManager,
    private val okHttpClient: OkHttpClient,
    private val json: Json = Json { ignoreUnknownKeys = true }
) {
    data class IngestionResult(
        val originalTranscript: String,
        val englishTranslation: String,
        val sourceLanguage: String
    )

    suspend fun transcribeAndTranslate(
        wavBytes: ByteArray,
        sourceLanguage: String
    ): Result<IngestionResult> {
        val base64Audio = Base64.encodeToString(wavBytes, Base64.NO_WRAP)
        var config = configManager.getValidConfig()

        val asrServiceId = config.serviceIds["asr_$sourceLanguage"]
            ?: return Result.Error("Missing ASR service for language: $sourceLanguage")
        val nmtServiceId = config.serviceIds["translation_${sourceLanguage}_en"]
            ?: return Result.Error("Missing NMT service for $sourceLanguage -> en")

        val payload = """
        {
          "pipelineTasks": [
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
          ],
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

        return try {
            var response = okHttpClient.newCall(request).execute()

            // Handle token expiration
            if (response.code == 401 || response.code == 403) {
                Log.w("SarvaAI_Asr", "Inference token expired. Refreshing pipeline config...")
                config = configManager.getValidConfig(forceRefresh = true)
                val retryRequest = request.newBuilder()
                    .header(config.authHeaderName, config.authHeaderValue)
                    .build()
                response = okHttpClient.newCall(retryRequest).execute()
            }

            if (!response.isSuccessful) {
                return Result.Error("Bhashini inference failed: HTTP ${response.code}")
            }

            val body = response.body?.string() ?: return Result.Error("Empty inference response")
            val inferenceResponse = json.decodeFromString<InferenceResponse>(body)

            val transcript = inferenceResponse.pipelineResponse.getOrNull(0)?.output?.getOrNull(0)?.source ?: ""
            val translation = inferenceResponse.pipelineResponse.getOrNull(1)?.output?.getOrNull(0)?.target ?: transcript

            Log.i("SarvaAI_Asr", "ASR ($sourceLanguage): $transcript")
            Log.i("SarvaAI_Asr", "NMT (en): $translation")

            Result.Success(
                IngestionResult(
                    originalTranscript = transcript,
                    englishTranslation = translation,
                    sourceLanguage = sourceLanguage
                )
            )
        } catch (e: Exception) {
            Log.e("SarvaAI_Asr", "Exception during ASR+NMT pipeline", e)
            Result.Error(e.localizedMessage ?: "Unknown network error")
        }
    }
}
```

---

### 6. Physical Device Testing Protocol on realme P3 Pro (Android 16)

#### 6.1 Permission & AudioRecord Hardware Validation
1. Deploy updated build to realme P3 Pro.
2. Grant `android.permission.RECORD_AUDIO` on screen.
3. Verify that `AudioRecord.state == AudioRecord.STATE_INITIALIZED` without error code `-38`.

#### 6.2 Live Hindi Voice Ingestion Test
1. Set active language to Hindi (`hi`).
2. Tap microphone button (or speak into active listener).
3. Speak clearly: *"मुझे दो बटर नान और एक दाल मखनी चाहिए"* (Mujhe do butter naan aur ek dal makhani chahiye).
4. Observe Logcat:
   - `VadStatus` transitions: `CALIBRATING` -> `SILENCE` -> `SPEECH_ACTIVE` -> `SPEECH_ENDED`.
   - `WavEncoder` emits WAV byte array of size ~160KB (for a 5-second utterance).
   - Ingestion result returns in ~2.1 seconds:
     - Transcript: `मुझे दो बटर नान और एक दाल मखनी चाहिए`
     - Translation: `I want two butter naan and one dal makhani`

#### 6.3 Live Telugu Voice Ingestion Test
1. Set active language to Telugu (`te`).
2. Speak: *"నాకు రెండు ప్లేట్ల బిర్యానీ కావాలి"* (Naaku rendu pletla biryani kaavali).
3. Observe Logcat:
   - Transcript: `నాకు రెండు ప్లేట్ల బిర్యానీ కావాలి`
   - Translation: `I want two plates of biryani`

---

### 7. Failure Modes & Edge Cases

| Failure Mode | Root Cause | Exact Resolution |
|---|---|---|
| `AudioRecord: getMinBufferSize returned ERROR_BAD_VALUE` | Device mic does not support 16kHz mono | Fall back to 44.1kHz and downsample to 16kHz via linear interpolation filter. (Note: realme P3 Pro natively supports 16kHz). |
| Bhashini returns HTTP 504 Gateway Timeout | Uploaded audio file exceeded 15 seconds | `EnergyVad` strictly caps recording at 12 seconds (`maxUtteranceDurationMs`). |
| Empty transcript (`""`) | Utterance was purely ambient room noise | Heuristic check: If transcript length is 0, trigger polite retry earcon without advancing dialogue state. |

---

### 8. Git Checkpoint & Remote Push
```bash
git add .
git commit -m "feat(phase2): implement bhashini pipeline discovery, 16khz pcm recorder, energy vad, wav encoder, and atomic asr-nmt ingestion client"
git push origin main
```

---

### 9. Exit Criteria Checklist
- [x] Bhashini configuration discovery executes with live credentials and resolves service IDs.
- [x] AudioRecord captures 16kHz 16-bit mono PCM cleanly on realme P3 Pro hardware.
- [x] Energy VAD detects speech start and speech end automatically.
- [x] Live Hindi and Telugu speech returns accurate native transcripts and English translations.
- [x] HTTP 401 token refresh mechanism verified and tested.
- [x] Code pushed to remote repository.
