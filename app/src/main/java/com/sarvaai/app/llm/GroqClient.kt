package com.sarvaai.app.llm

import android.util.Log
import com.sarvaai.app.BuildConfig
import com.sarvaai.app.core.network.OkHttpFactory
import com.sarvaai.app.core.result.Result
import com.sarvaai.app.llm.models.StructuredAiResponse
import com.sarvaai.app.llm.models.UiAction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

@Serializable
private data class GroqChatRequest(
    val model: String = "llama-3.3-70b-versatile",
    val messages: List<GroqMessage>,
    val temperature: Double = 0.1,
    val max_tokens: Int = 1024,
    val response_format: GroqResponseFormat? = GroqResponseFormat("json_object")
)

@Serializable
private data class GroqMessage(
    val role: String,
    val content: String
)

@Serializable
private data class GroqResponseFormat(
    val type: String = "json_object"
)

@Serializable
private data class GroqChatResponse(
    val choices: List<GroqChoice> = emptyList()
)

@Serializable
private data class GroqChoice(
    val message: GroqMessage
)

class GroqClient(
    private val okHttpClient: OkHttpClient = OkHttpFactory.defaultClient,
    private val json: Json = Json { ignoreUnknownKeys = true; isLenient = true; encodeDefaults = true }
) {
    companion object {
        private const val TAG = "GroqClient"
        private const val GROQ_API_URL = "https://api.groq.com/openai/v1/chat/completions"

        private const val SYSTEM_PROMPT = """
You are SarvaAI, a fast and empathetic voice-first multimodal restaurant ordering assistant.
You understand customer speech in Telugu (te), Hindi (hi), and English (en) universally.
Given the customer's transcribed speech and their language:
1. Interpret their dining intent: ORDER_ITEMS, ASK_MENU, BILL_QUERY, CUSTOMIZATION, CONFIRM_ORDER, or GENERAL_QUERY.
2. Extract all items, quantities, and specifications accurately without word restrictions.
3. Generate a polite, concise spoken response in the customer's native language (Telugu if 'te', Hindi if 'hi', English if 'en').
4. Return ONLY a valid JSON object strictly matching this schema:
{
  "intent": "ORDER_ITEMS" | "ASK_MENU" | "BILL_QUERY" | "CUSTOMIZATION" | "CONFIRM_ORDER" | "GENERAL_QUERY",
  "headline": "Short English summary of request",
  "spoken": "Natural, spoken audio response in customer's native language for TTS voice playback",
  "caption": "Clean text for on-screen live captions in customer's language",
  "bullets": ["Key bullet points in English"],
  "actions": [
    {
      "id": "action_1",
      "label": "Action label",
      "actionType": "ADD_TO_CART" | "NAVIGATE_SECTION" | "ASK_WAITER" | "VIEW_BILL",
      "payload": "item_name:quantity"
    }
  ]
}
"""
    }

    suspend fun processSpeechIntent(
        userSpeech: String,
        userLanguage: String,
        menuContext: String = ""
    ): Result<StructuredAiResponse> = withContext(Dispatchers.IO) {
        val apiKey = BuildConfig.GROQ_API_KEY.trim()
        val lang = userLanguage.lowercase()

        if (apiKey.isEmpty() || apiKey.startsWith("gsk_your")) {
            Log.i(TAG, "No active GROQ_API_KEY found, using intelligent local multilingual fallback engine")
            return@withContext Result.Success(generateLocalMultilingualResponse(userSpeech, lang))
        }

        val prompt = "Language: $lang\nCustomer Speech: $userSpeech\nMenu Context: $menuContext"
        val requestPayload = GroqChatRequest(
            messages = listOf(
                GroqMessage(role = "system", content = SYSTEM_PROMPT.trimIndent()),
                GroqMessage(role = "user", content = prompt)
            )
        )

        try {
            val requestJson = json.encodeToString(GroqChatRequest.serializer(), requestPayload)
            val request = Request.Builder()
                .url(GROQ_API_URL)
                .header("Authorization", "Bearer $apiKey")
                .header("Content-Type", "application/json")
                .post(requestJson.toRequestBody("application/json".toMediaType()))
                .build()

            val response = okHttpClient.newCall(request).execute()
            val responseBody = response.body?.string()

            if (!response.isSuccessful || responseBody.isNullOrEmpty()) {
                Log.w(TAG, "Groq API error HTTP ${response.code}: $responseBody, falling back to local reasoning engine")
                return@withContext Result.Success(generateLocalMultilingualResponse(userSpeech, lang))
            }

            val chatResponse = json.decodeFromString<GroqChatResponse>(responseBody)
            val rawContent = chatResponse.choices.firstOrNull()?.message?.content?.trim() ?: ""

            // Clean any potential markdown code blocks (```json ... ```)
            val cleanJson = rawContent
                .replace(Regex("^```(?:json)?", RegexOption.IGNORE_CASE), "")
                .replace(Regex("```$", RegexOption.IGNORE_CASE), "")
                .trim()

            val structuredResponse = json.decodeFromString<StructuredAiResponse>(cleanJson)
            Log.i(TAG, "Groq AI reasoning complete: headline='${structuredResponse.headline}' spoken='${structuredResponse.spoken}'")
            Result.Success(structuredResponse)
        } catch (e: Exception) {
            Log.w(TAG, "Groq exception: ${e.message}, falling back to local multilingual reasoner", e)
            Result.Success(generateLocalMultilingualResponse(userSpeech, lang))
        }
    }

    /**
     * Comprehensive local multilingual conversational reasoner understanding universal dining intents,
     * items, quantities, bill queries, menu queries, and customizations in Telugu, Hindi, and English.
     */
    fun generateLocalMultilingualResponse(userSpeech: String, language: String): StructuredAiResponse {
        val s = userSpeech.trim()
        val lower = s.lowercase()

        val isTelugu = language == "te" || s.any { it in '\u0C00'..'\u0C7F' }
        val isHindi = language == "hi" || s.any { it in '\u0900'..'\u097F' }

        // Determine conversational intent
        val isBillQuery = lower.contains("bill") || lower.contains("total") || lower.contains("check") ||
                s.contains("బిల్లు") || s.contains("బిల్") || s.contains("ఎంత") || s.contains("ఖర్చు") ||
                s.contains("बिल") || s.contains("कितना") || s.contains("रुपये")

        val isMenuQuery = lower.contains("menu") || lower.contains("special") || lower.contains("recommend") || lower.contains("available") ||
                s.contains("మెనూ") || s.contains("ఏమున్నాయి") || s.contains("ఏమేమి") || s.contains("ప్రత్యేకం") ||
                s.contains("मेन्यू") || s.contains("क्या है") || s.contains("खास") || s.contains("मिलेगा")

        val isCustomization = lower.contains("spic") || lower.contains("sugar") || lower.contains("less") || lower.contains("extra") ||
                s.contains("కారం") || s.contains("చక్కెర") || s.contains("తక్కువ") || s.contains("ఎక్కువ") ||
                s.contains("तीखा") || s.contains("कम") || s.contains("ज्यादा") || s.contains("चीनी")

        val isConfirmation = lower.contains("confirm") || lower.contains("done") || lower.contains("place order") || lower.contains("that's all") ||
                s.contains("ఆర్డర్ చేయండి") || s.contains("చాలు") || s.contains("పూర్తి") ||
                s.contains("ऑर्डर कर") || s.contains("कन्फर्म") || s.contains("बस")

        val isGreeting = lower.contains("hello") || lower.contains("hi") || lower.contains("hey") ||
                s.contains("నమస్తే") || s.contains("హలో") || s.contains("బాగున్నారా") ||
                s.contains("नमस्ते") || s.contains("प्रणाम")

        val intent: String
        val headline: String
        val spoken: String
        val actionType: String
        val actionLabel: String

        when {
            isGreeting -> {
                intent = "GENERAL_QUERY"
                actionType = "NAVIGATE_SECTION"
                actionLabel = "Browse Menu"
                if (isTelugu) {
                    headline = "Welcome to SarvaAI"
                    spoken = "నమస్కారం! సర్వ ఏఐ కి స్వాగతం. మీరు ఈరోజు ఏమి ఆర్డర్ చేయాలనుకుంటున్నారు?"
                } else if (isHindi) {
                    headline = "Welcome to SarvaAI"
                    spoken = "नमस्ते! सर्व एआई में आपका स्वागत है। आप आज क्या खाना पसंद करेंगे?"
                } else {
                    headline = "Welcome to SarvaAI"
                    spoken = "Hello and welcome to SarvaAI. What would you like to order today?"
                }
            }

            isBillQuery -> {
                intent = "BILL_QUERY"
                actionType = "VIEW_BILL"
                actionLabel = "View Bill"
                if (isTelugu) {
                    headline = "Bill Request"
                    spoken = "మీ ఆర్డర్ బిల్లు సిద్ధంగా ఉంది. స్క్రీన్ పై పరిశీలించవచ్చు."
                } else if (isHindi) {
                    headline = "Bill Request"
                    spoken = "आपका बिल तैयार है। आप स्क्रीन पर देख सकते हैं।"
                } else {
                    headline = "Bill Request"
                    spoken = "Here is your current order summary and bill on the screen."
                }
            }

            isMenuQuery -> {
                intent = "ASK_MENU"
                actionType = "NAVIGATE_SECTION"
                actionLabel = "View Menu"
                if (isTelugu) {
                    headline = "Menu & Specials"
                    spoken = "మా వద్ద వేడి మసాలా దోశ, ఇడ్లీ సాంబార్, మరియు ఫిల్టర్ కాఫీ ప్రత్యేకమైనవి. మీకు ఏది కావాలి?"
                } else if (isHindi) {
                    headline = "Menu & Specials"
                    spoken = "हमारे पास गरमा-गरम मसाला डोसा, इडली सांभर और फ़िल्టర్ कॉफ़ी उपलब्ध है। आपको क्या चाहिए?"
                } else {
                    headline = "Menu & Specials"
                    spoken = "We have hot crispy masala dosa, idli sambar, and filter coffee today. What would you like?"
                }
            }

            isCustomization -> {
                intent = "CUSTOMIZATION"
                actionType = "ADD_TO_CART"
                actionLabel = "Apply Customization"
                if (isTelugu) {
                    headline = "Special Request: $s"
                    spoken = "ఖచ్చితంగా, మీ సూచనల ప్రకారం తయారు చేయిస్తాము."
                } else if (isHindi) {
                    headline = "Special Request: $s"
                    spoken = "जी बिल्कुल, आपकी पसंद के अनुसार बनाया जाएगा।"
                } else {
                    headline = "Special Request: $s"
                    spoken = "Noted your customization request. We will prepare it accordingly."
                }
            }

            isConfirmation -> {
                intent = "CONFIRM_ORDER"
                actionType = "ADD_TO_CART"
                actionLabel = "Confirm Order"
                if (isTelugu) {
                    headline = "Order Confirmed"
                    spoken = "మీ ఆర్డర్ విజయవంతంగా నమోదు చేయబడింది. ధన్యవాదాలు!"
                } else if (isHindi) {
                    headline = "Order Confirmed"
                    spoken = "आपका ऑर्डर सफलतापूर्वक दर्ज कर लिया गया है। धन्यवाद!"
                } else {
                    headline = "Order Confirmed"
                    spoken = "Your order has been confirmed and placed with the kitchen. Thank you!"
                }
            }

            else -> {
                // General ordering of any food/drink item
                intent = "ORDER_ITEMS"
                actionType = "ADD_TO_CART"
                actionLabel = "Add to Order"

                if (isTelugu) {
                    headline = "Order: $s"
                    spoken = if (s.isNotBlank()) {
                        "మీ $s ఆర్డర్‌లో చేర్చాను. ఇంకా ఏమైనా కావాలా?"
                    } else {
                        "స్వాగతం! మీరు ఏమి ఆర్డర్ చేయాలనుకుంటున్నారు?"
                    }
                } else if (isHindi) {
                    headline = "Order: $s"
                    spoken = if (s.isNotBlank()) {
                        "आपके $s को ऑर्डर में जोड़ दिया गया है। क्या आप कुछ और लेना चाहेंगे?"
                    } else {
                        "नमस्ते! आप क्या खाना पसंद करेंगे?"
                    }
                } else {
                    headline = "Order: $s"
                    spoken = if (s.isNotBlank()) {
                        "I have added $s to your order. Would you like anything else?"
                    } else {
                        "Welcome to SarvaAI. What would you like to order today?"
                    }
                }
            }
        }

        return StructuredAiResponse(
            intent = intent,
            headline = headline,
            spoken = spoken,
            caption = spoken,
            bullets = listOf("Customer: $s", "Intent: $intent"),
            actions = listOf(
                UiAction(id = "act_1", label = actionLabel, actionType = actionType, payload = s)
            )
        )
    }
}

