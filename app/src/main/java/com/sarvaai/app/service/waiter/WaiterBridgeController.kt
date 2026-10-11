package com.sarvaai.app.service.waiter

import android.util.Log
import com.sarvaai.app.bhashini.asr.AsrNmtClient
import com.sarvaai.app.core.result.Result
import com.sarvaai.app.llm.GroqClient
import com.sarvaai.app.service.wakeword.WakeWordDetector
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

data class WaiterBridgeState(
    val dinerLanguage: String = "te",
    val staffLanguage: String = "hi",
    val activeStaffCardText: String = "नमस्ते! मैं आपकी क्या मदद कर सकता हूँ?",
    val activeStaffCardSubtext: String = "Hello! How can I help you?",
    val liveDinerCaption: String = "సర్వర్ మాట్లాడే మాటలు ఇక్కడ అనువదించబడతాయి (Server speech will appear here).",
    val isFlippedForStaff: Boolean = false,
    val isProcessing: Boolean = false,
    val activeSpeaker: String = "NONE", // "DINER", "STAFF", "NONE"
    val errorMessage: String? = null
)

class WaiterBridgeController(
    private val asrNmtClient: AsrNmtClient,
    private val groqClient: GroqClient
) {
    companion object {
        private const val TAG = "WaiterBridgeController"
    }

    private val _state = MutableStateFlow(WaiterBridgeState())
    val state: StateFlow<WaiterBridgeState> = _state.asStateFlow()

    fun setDinerLanguage(lang: String) {
        _state.value = _state.value.copy(dinerLanguage = lang.lowercase())
    }

    fun setStaffLanguage(lang: String) {
        _state.value = _state.value.copy(staffLanguage = lang.lowercase())
    }

    fun toggleFlipForStaff() {
        _state.value = _state.value.copy(isFlippedForStaff = !_state.value.isFlippedForStaff)
    }

    fun setStaffCardMessage(message: String, subtext: String = "") {
        _state.value = _state.value.copy(
            activeStaffCardText = message,
            activeStaffCardSubtext = subtext
        )
    }

    fun setDinerCaption(caption: String) {
        _state.value = _state.value.copy(liveDinerCaption = caption)
    }

    /**
     * Diner taps a quick phrase chip -> Display giant text in staff language and speak aloud in staff language.
     */
    fun selectPhrase(
        phrase: PhraseChip,
        onSpeakToStaff: (text: String, lang: String) -> Unit
    ) {
        val staffLang = _state.value.staffLanguage
        val dinerLang = _state.value.dinerLanguage

        val staffText = phrase.getTextForLanguage(staffLang)
        val dinerText = phrase.getTextForLanguage(dinerLang)
        val engSubtext = phrase.englishText

        _state.value = _state.value.copy(
            activeStaffCardText = staffText,
            activeStaffCardSubtext = engSubtext,
            liveDinerCaption = dinerText,
            activeSpeaker = "DINER",
            errorMessage = null
        )

        onSpeakToStaff(staffText, staffLang)
    }

    /**
     * Diner speaks in Diner Language -> Translates to Staff Language -> Speaks to Staff + Displays Giant Staff Card.
     */
    suspend fun processDinerSpeech(
        wavBytes: ByteArray,
        onCloseCommand: (() -> Unit)? = null,
        onSpeakToStaff: (text: String, lang: String) -> Unit
    ) = withContext(Dispatchers.IO) {
        val dinerLang = _state.value.dinerLanguage
        val staffLang = _state.value.staffLanguage

        _state.value = _state.value.copy(isProcessing = true, activeSpeaker = "DINER", errorMessage = null)

        val asrResult = asrNmtClient.transcribeAndTranslate(wavBytes, dinerLang)
        if (asrResult is Result.Success) {
            val originalDinerSpeech = asrResult.data.originalTranscript.trim()
            val engTranslation = asrResult.data.englishTranslation.trim()

            if (originalDinerSpeech.isNotBlank()) {
                if (WakeWordDetector.isCloseCommand(originalDinerSpeech)) {
                    withContext(Dispatchers.Main) {
                        _state.value = _state.value.copy(isProcessing = false)
                        onCloseCommand?.invoke()
                    }
                    return@withContext
                }

                val targetStaffText = if (dinerLang == staffLang) {
                    originalDinerSpeech
                } else {
                    translateTextLocallyOrLlm(originalDinerSpeech, dinerLang, staffLang, engTranslation)
                }

                withContext(Dispatchers.Main) {
                    _state.value = _state.value.copy(
                        isProcessing = false,
                        activeStaffCardText = targetStaffText,
                        activeStaffCardSubtext = originalDinerSpeech,
                        liveDinerCaption = originalDinerSpeech
                    )
                    onSpeakToStaff(targetStaffText, staffLang)
                }
            } else {
                withContext(Dispatchers.Main) {
                    _state.value = _state.value.copy(
                        isProcessing = false,
                        errorMessage = "Could not hear diner clearly. Please try again."
                    )
                }
            }
        } else {
            withContext(Dispatchers.Main) {
                _state.value = _state.value.copy(
                    isProcessing = false,
                    errorMessage = (asrResult as? Result.Error)?.message ?: "Transcription failed"
                )
            }
        }
    }

    /**
     * Waiter/Staff speaks in Staff Language -> Transcribes & translates -> Displays giant caption in Diner Language.
     */
    suspend fun processStaffSpeech(
        wavBytes: ByteArray,
        onCloseCommand: (() -> Unit)? = null,
        onSpeakToDiner: ((text: String, lang: String) -> Unit)? = null
    ) = withContext(Dispatchers.IO) {
        val staffLang = _state.value.staffLanguage
        val dinerLang = _state.value.dinerLanguage

        _state.value = _state.value.copy(isProcessing = true, activeSpeaker = "STAFF", errorMessage = null)

        val asrResult = asrNmtClient.transcribeAndTranslate(wavBytes, staffLang)
        if (asrResult is Result.Success) {
            val originalStaffSpeech = asrResult.data.originalTranscript.trim()
            val engTranslation = asrResult.data.englishTranslation.trim()

            if (originalStaffSpeech.isNotBlank()) {
                if (WakeWordDetector.isCloseCommand(originalStaffSpeech)) {
                    withContext(Dispatchers.Main) {
                        _state.value = _state.value.copy(isProcessing = false)
                        onCloseCommand?.invoke()
                    }
                    return@withContext
                }

                val translatedDinerText = if (staffLang == dinerLang) {
                    originalStaffSpeech
                } else {
                    translateTextLocallyOrLlm(originalStaffSpeech, staffLang, dinerLang, engTranslation)
                }

                withContext(Dispatchers.Main) {
                    _state.value = _state.value.copy(
                        isProcessing = false,
                        activeStaffCardText = originalStaffSpeech,
                        activeStaffCardSubtext = engTranslation,
                        liveDinerCaption = "🗣️ Waiter: $translatedDinerText"
                    )
                    onSpeakToDiner?.invoke(translatedDinerText, dinerLang)
                }
            } else {
                withContext(Dispatchers.Main) {
                    _state.value = _state.value.copy(
                        isProcessing = false,
                        errorMessage = "Could not hear waiter response clearly."
                    )
                }
            }
        } else {
            withContext(Dispatchers.Main) {
                _state.value = _state.value.copy(
                    isProcessing = false,
                    errorMessage = (asrResult as? Result.Error)?.message ?: "Waiter ASR failed"
                )
            }
        }
    }

    /**
     * Diner speaks feedback/complaint in Diner Language -> Translates to Staff Language -> Speaks to Staff & returns results.
     */
    suspend fun processFeedbackSpeech(
        wavBytes: ByteArray,
        onSuccess: (dinerText: String, staffText: String) -> Unit,
        onSpeakToStaff: (text: String, lang: String) -> Unit
    ) = withContext(Dispatchers.IO) {
        val dinerLang = _state.value.dinerLanguage
        val staffLang = _state.value.staffLanguage

        _state.value = _state.value.copy(isProcessing = true, activeSpeaker = "DINER", errorMessage = null)

        val asrResult = asrNmtClient.transcribeAndTranslate(wavBytes, dinerLang)
        if (asrResult is Result.Success) {
            val originalDinerSpeech = asrResult.data.originalTranscript.trim()
            val engTranslation = asrResult.data.englishTranslation.trim()

            if (originalDinerSpeech.isNotBlank()) {
                val targetStaffText = if (dinerLang == staffLang) {
                    originalDinerSpeech
                } else {
                    translateTextLocallyOrLlm(originalDinerSpeech, dinerLang, staffLang, engTranslation)
                }

                withContext(Dispatchers.Main) {
                    _state.value = _state.value.copy(
                        isProcessing = false,
                        activeStaffCardText = targetStaffText,
                        activeStaffCardSubtext = "Complaint / Feedback: $originalDinerSpeech",
                        liveDinerCaption = "📢 Feedback: $originalDinerSpeech"
                    )
                    onSuccess(originalDinerSpeech, targetStaffText)
                    onSpeakToStaff(targetStaffText, staffLang)
                }
            } else {
                withContext(Dispatchers.Main) {
                    _state.value = _state.value.copy(
                        isProcessing = false,
                        errorMessage = "Could not hear feedback clearly. Please try again."
                    )
                }
            }
        } else {
            withContext(Dispatchers.Main) {
                _state.value = _state.value.copy(
                    isProcessing = false,
                    errorMessage = (asrResult as? Result.Error)?.message ?: "Transcription failed"
                )
            }
        }
    }

    suspend fun translateTextLocallyOrLlm(
        text: String,
        sourceLang: String,
        targetLang: String,
        englishFallback: String = ""
    ): String {
        // Use Groq LLM for natural conversational translation if available
        try {
            val prompt = "Translate this restaurant customer/waiter sentence accurately from $sourceLang to $targetLang. Output ONLY the translated text in native script without extra commentary:\n\"$text\""
            val res = groqClient.processSpeechIntent(prompt, targetLang)
            if (res is Result.Success && res.data.spoken.isNotBlank()) {
                return res.data.spoken
            }
        } catch (e: Exception) {
            Log.w(TAG, "Groq translation fallback to local map: ${e.message}")
        }

        // Local semantic translation fallback for standard restaurant dialogue
        return fallbackTranslate(text, sourceLang, targetLang, englishFallback)
    }

    fun fallbackTranslate(text: String, sourceLang: String, targetLang: String, englishFallback: String = ""): String {
        val lower = text.lowercase().trim()
        // Common phrases translation map
        if (targetLang == "hi") {
            if (lower.contains("water") || lower.contains("నీరు") || lower.contains("మంచి నీరు")) return "कृपया पीने का पानी लेकर आइए।"
            if (lower.contains("bill") || lower.contains("బిల్లు")) return "कृपया बिल लेकर आइए।"
            if (lower.contains("cold") || lower.contains("reheat") || lower.contains("చల్లగా") || lower.contains("మళ్లీ వేడి")) return "खाना ठंडा हो गया है, कृपया इसे दोबारा गर्म कर दीजिए।"
            if (lower.contains("delay") || lower.contains("late") || lower.contains("ఆలస్యం")) return "ऑर्डर में बहुत देरी हो रही है, कृपया जल्दी मंगवाइए।"
            if (lower.contains("missing") || lower.contains("రాలేదు")) return "हमारे ऑर्डर में एक आइटम छूट गया है, कृपया तुरंत चेक कीजिए।"
            if (lower.contains("spicy") || lower.contains("కారం")) return "कृपया खाना कम तीखा बनाइए।"
            if (lower.contains("clean") || lower.contains("cutlery") || lower.contains("శుభ్రం") || lower.contains("చెంచా") || lower.contains("ప్లేట్")) return "कृपया टेबल साफ कर दीजिए और नई कटलरी ले आइए।"
            if (lower.contains("delicious") || lower.contains("tasty") || lower.contains("chef") || lower.contains("రుచి") || lower.contains("బాగుంది")) return "खाना बहुत स्वादिष्ट है! शेफ को बहुत धन्यवाद।"
            if (lower.contains("minute") || lower.contains("నిమిషం") || lower.contains("లా రహా")) return "दो मिनट में ला रहा हूँ।"
            if (lower.contains("yes") || lower.contains("అవును") || lower.contains("సరే")) return "जी बिल्कुल, अभी लेकर आता हूँ।"
            if (englishFallback.isNotBlank()) return englishFallback
        } else if (targetLang == "te") {
            if (lower.contains("water") || lower.contains("पानी")) return "దయచేసి మంచి నీరు తీసుకురండి."
            if (lower.contains("bill") || lower.contains("बिल")) return "దయచేసి బిల్లు తీసుకురండి."
            if (lower.contains("cold") || lower.contains("reheat") || lower.contains("ठंडा") || lower.contains("गर्म")) return "ఆహారం చల్లగా ఉంది, దయచేసి మళ్లీ వేడి చేయండి."
            if (lower.contains("delay") || lower.contains("late") || lower.contains("देरी") || lower.contains("जल्दी")) return "ఆర్డర్ ఆలస్యమైంది, దయచేసి త్వరగా తీసుకురండి."
            if (lower.contains("missing") || lower.contains("छूट") || lower.contains("कम")) return "మా ఆర్డర్‌లో ఒక ఐటమ్ రాలేదు, దయచేసి తనిఖీ చేయండి."
            if (lower.contains("तीखा") || lower.contains("spicy")) return "దయచేసి కారం తక్కువగా చేయండి."
            if (lower.contains("clean") || lower.contains("कटलरी") || lower.contains("चम्मच") || lower.contains("सफाई")) return "దయచేసి టేబుల్ శుభ్రం చేసి కొత్త ప్లేట్లు, చెంచాలు ఇవ్వండి."
            if (lower.contains("delicious") || lower.contains("tasty") || lower.contains("स्वादिष्ट") || lower.contains("शेफ")) return "ఆహారం చాలా రుచిగా ఉంది! చెఫ్‌కి అభినందనలు."
            if (lower.contains("दो मिनट") || lower.contains("two minutes")) return "రెండు నిమిషాల్లో తెస్తున్నాను."
            if (lower.contains("ला रहा") || lower.contains("लाता")) return "ఖచ్చితంగా, ఇప్పుడే తీసుకువస్తాను."
            if (lower.contains("हाँ") || lower.contains("yes")) return "అవును, తప్పకుండా."
            if (englishFallback.isNotBlank()) return englishFallback
        } else {
            if (englishFallback.isNotBlank()) return englishFallback
        }
        return text
    }
}
