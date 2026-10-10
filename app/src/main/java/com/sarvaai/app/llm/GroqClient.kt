package com.sarvaai.app.llm

import android.content.Context
import android.util.Log
import com.sarvaai.app.BuildConfig
import com.sarvaai.app.core.config.ApiKeyManager
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
    val model: String = "openai/gpt-oss-20b",
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
    private val context: Context? = null,
    private val okHttpClient: OkHttpClient = OkHttpFactory.defaultClient,
    private val json: Json = Json { ignoreUnknownKeys = true; isLenient = true; encodeDefaults = true }
) {
    companion object {
        private const val TAG = "GroqClient"
        private const val GROQ_API_URL = "https://api.groq.com/openai/v1/chat/completions"

        private const val SYSTEM_PROMPT = """
You are SarvaAI, a fast and empathetic voice-first multimodal restaurant ordering assistant.
You understand customer speech in Telugu (te), Hindi (hi), and English (en) universally.
Given the customer's transcribed speech, active language, and current cart/menu context:
1. Interpret customer dining intent:
   - "ORDER_ITEMS": adding items (with quantities, customizations like "less spicy", "no onion", "extra hot", etc.)
   - "REMOVE_ITEMS": removing or reducing items from the cart
   - "CLEAR_CART": clearing all items
   - "BILL_QUERY": asking for total, subtotal, tax, or bill breakdown
   - "SPLIT_BILL": splitting bill among N people (e.g. "split among 3 people", "3 మందికి పంచండి", "3 लोगों में बांटो")
   - "CONFIRM_ORDER": customer wants to place or finalize the order
   - "ASK_MENU": inquiring about available dishes or specials
   - "GENERAL_QUERY": greetings or assistance requests

2. Actions to return in the "actions" array:
   - For adding: actionType = "ADD_TO_CART", payload = "Item Name:Quantity:Customization Notes" (e.g. "Masala Dosa:2:less spicy", "Filter Coffee:1:extra hot")
   - For removing: actionType = "REMOVE_FROM_CART", payload = "Item Name"
   - For clearing: actionType = "CLEAR_CART", payload = "all"
   - For splitting: actionType = "SPLIT_BILL", payload = "3" (number of people)
   - For checkout: actionType = "CONFIRM_ORDER", payload = "confirm"

3. Generate a polite, concise spoken response in the customer's active native language (Telugu if 'te', Hindi if 'hi', English if 'en').
4. Return ONLY a valid JSON object strictly matching this schema:
{
  "intent": "ORDER_ITEMS" | "REMOVE_ITEMS" | "CLEAR_CART" | "BILL_QUERY" | "SPLIT_BILL" | "CONFIRM_ORDER" | "ASK_MENU" | "GENERAL_QUERY",
  "headline": "Short English summary of request",
  "spoken": "Natural, spoken audio response in customer's native language for TTS voice playback",
  "caption": "Clean text for on-screen live captions in customer's language",
  "bullets": ["Key bullet points in English"],
  "actions": [
    {
      "id": "action_1",
      "label": "Action label",
      "actionType": "ADD_TO_CART" | "REMOVE_FROM_CART" | "CLEAR_CART" | "SPLIT_BILL" | "CONFIRM_ORDER" | "NAVIGATE_SECTION" | "ASK_WAITER" | "VIEW_BILL",
      "payload": "Item Name:Quantity:Customization Notes"
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
        val apiKey = if (context != null) ApiKeyManager.getGroqApiKey(context) else BuildConfig.GROQ_API_KEY.trim()
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
                .header("User-Agent", "SarvaAI/1.0")
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
     * items, quantities, bill queries, bill splitting, menu queries, and customizations in Telugu, Hindi, and English.
     */
    fun generateLocalMultilingualResponse(userSpeech: String, language: String): StructuredAiResponse {
        val s = userSpeech.trim()
        val lower = s.lowercase()

        val isTelugu = language == "te" || s.any { it in '\u0C00'..'\u0C7F' }
        val isHindi = language == "hi" || s.any { it in '\u0900'..'\u097F' }

        // Determine conversational intent
        val isSplitQuery = lower.contains("split") || lower.contains("divide") ||
                s.contains("విభజించండి") || s.contains("పంచండి") || s.contains("మందికి") ||
                s.contains("बांटो") || s.contains("बांट") || s.contains("विभाजित") || s.contains("लोगों में")

        val isClearCart = lower.contains("clear cart") || lower.contains("empty cart") || lower.contains("cancel all") ||
                s.contains("క్లియర్ చేయండి") || s.contains("మొత్తం తీసేయండి") || s.contains("ఖాళీ చేయండి") ||
                s.contains("सब हटाओ") || s.contains("खाली करो") || s.contains("रद्द करो")

        val isRemoveItem = lower.contains("remove") || lower.contains("delete") || lower.contains("don't want") ||
                s.contains("తీసేయండి") || s.contains("వద్దు") || s.contains("తీసివేయండి") ||
                s.contains("हटाओ") || s.contains("निकालो") || s.contains("नहीं चाहिए")

        val isBillQuery = lower.contains("bill") || lower.contains("total") || lower.contains("check") ||
                s.contains("బిల్లు") || s.contains("బిల్") || s.contains("ఎంత") || s.contains("ఖర్చు") ||
                s.contains("बिल") || s.contains("कितना") || s.contains("रुपये")

        val isMenuQuery = lower.contains("menu") || lower.contains("special") || lower.contains("recommend") || lower.contains("available") ||
                s.contains("మెనూ") || s.contains("ఏమున్నాయి") || s.contains("ఏమేమి") || s.contains("ప్రత్యేకం") ||
                s.contains("मेन्यू") || s.contains("क्या है") || s.contains("खास") || s.contains("मिलेगा")

        val isCustomization = lower.contains("spic") || lower.contains("sugar") || lower.contains("less") || lower.contains("extra") ||
                s.contains("కారం") || s.contains("చక్కెర") || s.contains("తక్కువ") || s.contains("ఎక్కువ") ||
                s.contains("तीखा") || s.contains("कम") || s.contains("ज्यादा") || s.contains("चीनी")

        val isConfirmation = lower.contains("confirm") || lower.contains("place order") || lower.contains("that's all") || lower.contains("checkout") ||
                s.contains("ఆర్డర్ చేయండి") || s.contains("చాలు") || s.contains("ఖరారు") ||
                s.contains("ऑर्डर करो") || s.contains("कन्फर्म") || s.contains("पक्का करो")

        val isGreeting = lower.contains("hello") || lower.contains("hi") || lower.contains("hey") ||
                s.contains("నమస్తే") || s.contains("హలో") || s.contains("బాగున్నారా") ||
                s.contains("नमस्ते") || s.contains("प्रणाम")

        val intent: String
        val headline: String
        val spoken: String
        val actionType: String
        val actionLabel: String
        val payload: String

        when {
            isGreeting -> {
                intent = "GENERAL_QUERY"
                actionType = "NAVIGATE_SECTION"
                actionLabel = "Browse Menu"
                payload = ""
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

            isSplitQuery -> {
                intent = "SPLIT_BILL"
                actionType = "SPLIT_BILL"
                actionLabel = "Split Bill"
                // Extract digit if present e.g. "split for 3" or "3 మందికి"
                val digitMatch = Regex("(\\d+)").find(s)?.value?.toIntOrNull() ?: 2
                val count = digitMatch.coerceIn(2, 10)
                payload = count.toString()
                if (isTelugu) {
                    headline = "Split Bill for $count"
                    spoken = "$count మందికి బిల్లు సమానంగా విభజించబడింది."
                } else if (isHindi) {
                    headline = "Split Bill for $count"
                    spoken = "$count लोगों में बिल बराबर बांट दिया गया है।"
                } else {
                    headline = "Split Bill for $count"
                    spoken = "Bill split evenly for $count people."
                }
            }

            isClearCart -> {
                intent = "CLEAR_CART"
                actionType = "CLEAR_CART"
                actionLabel = "Clear Cart"
                payload = "all"
                if (isTelugu) {
                    headline = "Cart Cleared"
                    spoken = "మీ కార్ట్ ఖాళీ చేయబడింది."
                } else if (isHindi) {
                    headline = "Cart Cleared"
                    spoken = "आपकी कार्ट खाली कर दी गई है।"
                } else {
                    headline = "Cart Cleared"
                    spoken = "Your cart has been cleared."
                }
            }

            isRemoveItem -> {
                intent = "REMOVE_ITEMS"
                actionType = "REMOVE_FROM_CART"
                actionLabel = "Remove Item"
                // clean item name
                val cleanedName = s.replace(Regex("(?i)remove|delete|తీసేయండి|తీసివేయండి|వద్దు|हटाओ|निकालो"), "").trim()
                payload = if (cleanedName.isNotBlank()) cleanedName else "item"
                if (isTelugu) {
                    headline = "Removed: $payload"
                    spoken = "$payload ఆర్డర్ నుండి తొలగించబడింది."
                } else if (isHindi) {
                    headline = "Removed: $payload"
                    spoken = "$payload को ऑर्डर से हटा दिया गया है।"
                } else {
                    headline = "Removed: $payload"
                    spoken = "Removed $payload from your order."
                }
            }

            isConfirmation -> {
                intent = "CONFIRM_ORDER"
                actionType = "CONFIRM_ORDER"
                actionLabel = "Confirm Order"
                payload = "confirm"
                if (isTelugu) {
                    headline = "Order Placed"
                    spoken = "మీ ఆర్డర్ విజయవంతంగా నమోదయింది. కిచెన్‌లో తయారు చేయబడుతోంది."
                } else if (isHindi) {
                    headline = "Order Placed"
                    spoken = "आपका ऑर्डर सफलतापूर्वक दर्ज हो गया। किचन में तैयारी शुरू हो गई है।"
                } else {
                    headline = "Order Placed"
                    spoken = "Your order has been placed successfully and is being prepared in the kitchen."
                }
            }

            isBillQuery -> {
                intent = "BILL_QUERY"
                actionType = "VIEW_BILL"
                actionLabel = "View Bill"
                payload = ""
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
                payload = ""
                if (isTelugu) {
                    headline = "Menu & Specials"
                    spoken = "మా వద్ద వేడి మసాలా దోశ, ఇడ్లీ సాంబార్, మరియు ఫిల్టర్ కాఫీ ప్రత్యేకమైనవి. మీకు ఏది కావాలి?"
                } else if (isHindi) {
                    headline = "Menu & Specials"
                    spoken = "हमारे पास गरमा-गरम मसाला डोसा, इडली सांभर और फ़िल्टर कॉफ़ी उपलब्ध है। आपको क्या चाहिए?"
                } else {
                    headline = "Menu & Specials"
                    spoken = "We have hot crispy masala dosa, idli sambar, and filter coffee today. What would you like?"
                }
            }

            isCustomization -> {
                intent = "CUSTOMIZATION"
                actionType = "ADD_TO_CART"
                actionLabel = "Apply Customization"
                payload = "Special Customization:1:$s"
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

            else -> {
                // General ordering of any food/drink item
                intent = "ORDER_ITEMS"
                actionType = "ADD_TO_CART"
                actionLabel = "Add to Order"

                // Extract quantity if present (e.g. "2 masala dosa", "ఒక దోశ", "दो चाय")
                var qty = 1
                val qtyMatch = Regex("^(\\d+)").find(s)
                if (qtyMatch != null) {
                    qty = qtyMatch.groupValues[1].toIntOrNull() ?: 1
                } else if (lower.contains("two") || s.contains("రెండు") || s.contains("दो")) {
                    qty = 2
                } else if (lower.contains("three") || s.contains("మూడు") || s.contains("तीन")) {
                    qty = 3
                } else if (lower.contains("four") || s.contains("నాలుగు") || s.contains("चार")) {
                    qty = 4
                }

                val cleanedItem = s.replace(Regex("^(\\d+|ఒక|రెండు|మూడు|నాలుగు|एक|दो|तीन|चार|one|two|three|four)\\s*", RegexOption.IGNORE_CASE), "").trim()
                val itemName = if (cleanedItem.isNotBlank()) cleanedItem else s
                payload = "$itemName:$qty:"

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
                UiAction(id = "act_1", label = actionLabel, actionType = actionType, payload = payload)
            )
        )
    }
}

