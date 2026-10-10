package com.sarvaai.app.bhashini.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// ==========================================
// Standard Bhashini ULCA / Dhruva DTOs
// ==========================================

@Serializable
data class PipelineConfigRequest(
    val pipelineTasks: List<PipelineTaskConfig>,
    val pipelineRequestConfig: PipelineRequestConfig
)

@Serializable
data class PipelineTaskConfig(
    val taskType: String, // "asr", "translation", "tts"
    val config: TaskLanguageConfig
)

@Serializable
data class TaskLanguageConfig(
    val language: LanguagePairConfig,
    val serviceId: String? = null,
    val audioFormat: String? = null,
    val samplingRate: Int? = null,
    val gender: String? = null
)

@Serializable
data class LanguagePairConfig(
    val sourceLanguage: String,
    val targetLanguage: String? = null
)

@Serializable
data class PipelineRequestConfig(
    val pipelineId: String
)

@Serializable
data class PipelineConfigResponse(
    val pipelineResponseConfig: List<PipelineResponseTaskConfig> = emptyList(),
    val pipelineInferenceAPIEndPoint: PipelineInferenceEndpoint = PipelineInferenceEndpoint()
)

@Serializable
data class PipelineResponseTaskConfig(
    val taskType: String,
    val config: List<ResponseTaskDetail> = emptyList()
)

@Serializable
data class ResponseTaskDetail(
    val serviceId: String = "",
    val modelId: String? = null,
    val language: LanguagePairConfig
)

@Serializable
data class PipelineInferenceEndpoint(
    @SerialName("callbackUrl") val endpoint: String = "https://dhruva-api.bhashini.gov.in/services/inference/pipeline",
    val inferenceApiKey: InferenceApiKey = InferenceApiKey()
)

@Serializable
data class InferenceApiKey(
    val name: String = "Authorization",
    val value: String = ""
)

@Serializable
data class InferenceResponse(
    val pipelineResponse: List<InferenceTaskResponse> = emptyList()
)

@Serializable
data class InferenceTaskResponse(
    val taskType: String = "",
    val output: List<InferenceOutputItem>? = null,
    val audio: List<InferenceAudioItem>? = null
)

@Serializable
data class InferenceOutputItem(
    val source: String? = null,
    val target: String? = null
)

@Serializable
data class InferenceAudioItem(
    val audioContent: String? = null,
    val audioUri: String? = null
)

// ==========================================
// AnuvaadHub / IIIT Canvas Sandbox DTOs
// ==========================================

@Serializable
data class AnuvaadHubMtRequest(
    @SerialName("input_text") val inputText: String
)

@Serializable
data class AnuvaadHubMtResponse(
    val status: String,
    val message: String? = null,
    val data: AnuvaadHubMtData? = null,
    val error: String? = null,
    val code: Int = 200
)

@Serializable
data class AnuvaadHubMtData(
    @SerialName("output_text") val outputText: String = ""
)

@Serializable
data class AnuvaadHubAsrResponse(
    val status: String,
    val message: String? = null,
    val data: AnuvaadHubAsrData? = null,
    val error: String? = null,
    val code: Int = 200
)

@Serializable
data class AnuvaadHubAsrData(
    @SerialName("recognized_text") val recognizedText: String = ""
)

@Serializable
data class AnuvaadHubTtsRequest(
    val text: String,
    val gender: String = "female"
)

@Serializable
data class AnuvaadHubTtsResponse(
    val status: String,
    val message: String? = null,
    val data: AnuvaadHubTtsData? = null,
    val error: String? = null,
    val code: Int = 200
)

@Serializable
data class AnuvaadHubTtsData(
    @SerialName("s3_url") val s3Url: String? = null,
    @SerialName("audioContent") val audioContent: String? = null
)

// ==========================================
// High-Level Domain Results
// ==========================================

data class IngestionResult(
    val originalTranscript: String,
    val englishTranslation: String,
    val sourceLanguage: String,
    val latencyMs: Long = 0L,
    val confidence: Double = 1.0,
    val staffTranslation: String? = null,
    val staffTargetLanguage: String? = null
)
