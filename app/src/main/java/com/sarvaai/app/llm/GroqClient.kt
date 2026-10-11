package com.sarvaai.app.llm

import android.content.Context
import android.util.Log
import com.sarvaai.app.BuildConfig
import com.sarvaai.app.core.config.ApiKeyManager
import com.sarvaai.app.core.network.OkHttpFactory
import com.sarvaai.app.core.result.Result
import com.sarvaai.app.data.entities.MenuItemEntity
import com.sarvaai.app.data.entities.MenuSectionEntity
import com.sarvaai.app.llm.models.StructuredAiResponse
import com.sarvaai.app.llm.models.StructuredTable
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
    private val context: Context? = null,
    private val okHttpClient: OkHttpClient = OkHttpFactory.defaultClient,
    private val json: Json = Json { ignoreUnknownKeys = true; isLenient = true; encodeDefaults = true }
) {
    companion object {
        private const val TAG = "GroqClient"
        private const val GROQ_API_URL = "https://api.groq.com/openai/v1/chat/completions"
        const val MODEL_LLAMA_70B = "llama-3.3-70b-versatile"
        const val MODEL_LLAMA_8B = "llama-3.1-8b-instant"

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

        private const val DIETARY_SYSTEM_PROMPT = """
You are SarvaAI's expert culinary and dietary intelligence AI assistant for Indian and international restaurants.
You understand customer queries in Telugu (te), Hindi (hi), and English (en) universally.

DIETARY & CULINARY KNOWLEDGE:
1. Vegetarian: 100% plant or dairy based. No meat (chicken, mutton, beef, pork), no poultry, no fish or seafood, no eggs, no animal rennet or gelatin.
2. Jain Vegetarian: Strict vegetarian with ABSOLUTELY NO root vegetables (NO onions, NO garlic, NO potatoes/aloo, NO carrots, NO radishes/mooli, NO ginger, NO beetroot, NO mushrooms).
   - Note: Standard North Indian gravies (Paneer Butter Masala, Shahi Paneer, Dal Makhani) and South Indian tiffin fillings (Masala Dosa potato filling, onion chutney) contain onion, garlic, or root vegetables unless specifically prepared Jain.
3. Nut-Free & Allergen Safety:
   - Cashew paste (kaju gravy) is heavily used in North Indian gravies (Paneer Butter Masala, Korma, Malai Kofta, Pasanda, Shahi dishes).
   - Peanuts (singdana / groundnut chutney) are common in South Indian breakfasts and Maharashtrian dishes.
   - Almonds (badam), pistachios (pista), walnuts are used in rich gravies, biryanis, and desserts.
   - Always flag potential nut cross-contact or cashew pastes for nut-allergic diners.
4. Vegan: 100% plant-based with NO dairy (no milk, no butter/makhani, no ghee, no paneer, no cream/malai, no curd/dahi/raita, no cheese, no honey).
5. Dairy: Identify butter, ghee, milk, paneer, cream, curd/yogurt, cheese.
6. Spice Levels: 0 = Mild, 1 = Medium, 2 = Spicy, 3 = Very Spicy.

ACTION TYPES TO RETURN:
- "FILTER_VEG": When customer asks to filter or view vegetarian items (payload: "vegetarian").
- "FILTER_JAIN": When customer asks for Jain dishes or to filter Jain options (payload: "jain").
- "FILTER_NUT_FREE": When customer asks for nut-free dishes (payload: "nut_free").
- "HIGHLIGHT_ITEM": When discussing, explaining, or recommending a specific dish (payload: "<itemId or Item Name>").
- "ADD_TO_CART": When customer wants to order or add an item (payload: "<Item Name>:<Quantity>:<Customization Notes>").
- "ANSWER_DIETARY": When providing dietary advice, ingredient breakdown, or answering questions (payload: "<Item Name or Topic>").
- "ASK_WAITER": When allergen safety or kitchen preparation needs waiter confirmation (payload: "<Question for staff>").

OUTPUT FORMAT:
Return ONLY valid JSON matching this schema:
{
  "intent": "DIETARY_QUERY" | "ORDER_ITEMS" | "ASK_MENU" | "GENERAL_QUERY",
  "headline": "Short English summary of dietary response",
  "spoken": "Natural, polite spoken response in customer's native language (Telugu if 'te', Hindi if 'hi', English if 'en')",
  "caption": "Clean text for on-screen live captions in customer's language",
  "bullets": ["Key bullet points in English"],
  "table": {
    "columns": ["Dish", "Dietary Tags", "Price", "Spice Level"],
    "rows": [["Dish Name", "Veg, Nut-Free", "₹250", "Mild (0/3)"]]
  },
  "tableNarration": "Optional spoken narration of table summary",
  "simpleLevel": 1,
  "confidence": "high" | "medium" | "low",
  "needsStaffConfirmation": true | false,
  "actions": [
    {
      "id": "act_1",
      "label": "Action label",
      "actionType": "FILTER_VEG" | "FILTER_JAIN" | "FILTER_NUT_FREE" | "HIGHLIGHT_ITEM" | "ADD_TO_CART" | "ANSWER_DIETARY" | "ASK_WAITER",
      "payload": "Payload string"
    }
  ],
  "followupPrompt": "Optional helpful follow-up question"
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
            model = MODEL_LLAMA_70B,
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

    suspend fun processMenuDietaryQa(
        userQuery: String,
        userLanguage: String,
        restaurantName: String,
        menuItems: List<MenuItemEntity>,
        sections: List<MenuSectionEntity>
    ): Result<StructuredAiResponse> = withContext(Dispatchers.IO) {
        val apiKey = if (context != null) ApiKeyManager.getGroqApiKey(context) else BuildConfig.GROQ_API_KEY.trim()
        val lang = userLanguage.lowercase()

        if (apiKey.isEmpty() || apiKey.startsWith("gsk_your")) {
            Log.i(TAG, "No active GROQ_API_KEY found, using intelligent local dietary reasoner")
            return@withContext Result.Success(
                generateLocalDietaryResponse(
                    userQuery = userQuery,
                    language = lang,
                    restaurantName = restaurantName,
                    menuItems = menuItems,
                    sections = sections
                )
            )
        }

        val sectionMap = sections.associateBy { it.sectionId }
        val menuBuilder = StringBuilder()
        menuBuilder.appendLine("Restaurant: $restaurantName")
        menuBuilder.appendLine("Menu Items:")
        for (item in menuItems) {
            val sectionName = sectionMap[item.sectionId]?.titleEnglish ?: "General"
            val vegStr = if (item.isVegetarian) "Vegetarian" else "Non-Vegetarian"
            val jainStr = if (item.isJain) "Jain-Safe" else "Not-Jain-Tagged"
            val nutStr = if (item.isNutFree) "Nut-Free" else "Contains-Nuts/Cashews"
            val veganStr = if (item.isVegan) "Vegan" else "Non-Vegan"
            val spiceStr = when (item.spiceLevel) {
                0 -> "Mild"
                1 -> "Medium"
                2 -> "Spicy"
                3 -> "Very-Spicy"
                else -> "Spice-${item.spiceLevel}"
            }
            val desc = if (!item.description.isNullOrBlank()) " [Desc: ${item.description}]" else ""
            menuBuilder.appendLine("- ID:${item.itemId} | ${item.nameEnglish} (₹${item.priceRupees}) | Section: $sectionName | $vegStr | $jainStr | $nutStr | $veganStr | $spiceStr$desc")
        }

        val prompt = """
Language: $lang (te=Telugu, hi=Hindi, en=English)
Restaurant: $restaurantName
User Query: $userQuery

$menuBuilder
""".trimIndent()

        val requestPayload = GroqChatRequest(
            model = MODEL_LLAMA_70B,
            messages = listOf(
                GroqMessage(role = "system", content = DIETARY_SYSTEM_PROMPT.trimIndent()),
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
                Log.w(TAG, "Groq Dietary QA API error HTTP ${response.code}: $responseBody, falling back to local dietary reasoner")
                return@withContext Result.Success(
                    generateLocalDietaryResponse(
                        userQuery = userQuery,
                        language = lang,
                        restaurantName = restaurantName,
                        menuItems = menuItems,
                        sections = sections
                    )
                )
            }

            val chatResponse = json.decodeFromString<GroqChatResponse>(responseBody)
            val rawContent = chatResponse.choices.firstOrNull()?.message?.content?.trim() ?: ""

            val cleanJson = rawContent
                .replace(Regex("^```(?:json)?", RegexOption.IGNORE_CASE), "")
                .replace(Regex("```$", RegexOption.IGNORE_CASE), "")
                .trim()

            val structuredResponse = json.decodeFromString<StructuredAiResponse>(cleanJson)
            Log.i(TAG, "Groq Dietary QA reasoning complete: headline='${structuredResponse.headline}' spoken='${structuredResponse.spoken}'")
            Result.Success(structuredResponse)
        } catch (e: Exception) {
            Log.w(TAG, "Groq Dietary QA exception: ${e.message}, falling back to local dietary reasoner", e)
            Result.Success(
                generateLocalDietaryResponse(
                    userQuery = userQuery,
                    language = lang,
                    restaurantName = restaurantName,
                    menuItems = menuItems,
                    sections = sections
                )
            )
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

    /**
     * Intelligent local dietary reasoner evaluating Indian restaurant culinary knowledge:
     * - Vegetarian: 100% plant / dairy based, no meat, fish, eggs, or animal rennet.
     * - Jain: strict vegetarian with NO root vegetables (no onions, no garlic, no potatoes, no carrots, no radishes, no mushrooms).
     * - Nut-Free: flags dishes with cashew paste (kaju gravy), almonds (badam), peanuts (singdana / groundnut chutney), pistachio, or walnut garnishes.
     * - Vegan & Dairy: flags paneer, butter, ghee, milk, cream, curd/raita.
     * - Spice levels and Add-to-Cart actions.
     */
    fun generateLocalDietaryResponse(
        userQuery: String,
        language: String,
        restaurantName: String,
        menuItems: List<MenuItemEntity>,
        sections: List<MenuSectionEntity> = emptyList()
    ): StructuredAiResponse {
        val s = userQuery.trim()
        val lower = s.lowercase()

        val isTelugu = language == "te" || s.any { it in '\u0C00'..'\u0C7F' }
        val isHindi = language == "hi" || s.any { it in '\u0900'..'\u097F' }

        val matchedItem = findMatchingMenuItem(s, menuItems)

        // Query intent detection
        val isAddToCart = lower.startsWith("add ") || lower.contains("add to cart") || lower.contains("add to order") ||
                lower.startsWith("order ") || lower.contains("ఆర్డర్") || lower.contains("చేర్చండి") ||
                lower.contains("जोड़ो") || lower.contains("ऑर्डर करो")

        val isJainQuery = lower.contains("jain") || lower.contains("onion") || lower.contains("garlic") ||
                lower.contains("root") || lower.contains("potato") || lower.contains("aloo") ||
                lower.contains("carrot") || lower.contains("radish") || lower.contains("mooli") ||
                s.contains("జైన్") || s.contains("ఉల్లిపాయ") || s.contains("వెల్లుల్లి") || s.contains("బంగాళాదుంప") ||
                s.contains("जैन") || s.contains("प्याज") || s.contains("लहसुन") || s.contains("आलू")

        val isNutQuery = lower.contains("nut") || lower.contains("cashew") || lower.contains("kaju") ||
                lower.contains("peanut") || lower.contains("singdana") || lower.contains("groundnut") ||
                lower.contains("almond") || lower.contains("badam") || lower.contains("pista") || lower.contains("walnut") ||
                s.contains("నట్") || s.contains("కాజు") || s.contains("జీడిపప్పు") || s.contains("వేరుశెనగ") || s.contains("బాదం") ||
                s.contains("नट") || s.contains("काजू") || s.contains("मूंगफली") || s.contains("बादाम") || s.contains("पिस्ता")

        val isVeganQuery = lower.contains("vegan") || lower.contains("plant-based") || lower.contains("dairy-free") ||
                s.contains("వీగన్") || s.contains("డైరీ ఫ్రీ") || s.contains("वीगन") || s.contains("डेयरी फ्री")

        val isDairyQuery = lower.contains("dairy") || lower.contains("butter") || lower.contains("ghee") ||
                lower.contains("milk") || lower.contains("cream") || lower.contains("lactose") || lower.contains("makhani") ||
                s.contains("వెన్న") || s.contains("నెయ్యి") || s.contains("పాలు") || s.contains("డైరీ") ||
                s.contains("मक्खन") || s.contains("घी") || s.contains("दूध") || s.contains("मलाई")

        val isVegQuery = lower.contains("vegetarian") || lower.contains("pure veg") || lower.contains("non-veg") ||
                lower.contains("meat") || lower.contains("chicken") || lower.contains("fish") || lower.contains("egg") ||
                lower.contains("starter") || lower.contains("శాకాహార") || lower.contains("శాకాహారి") ||
                lower.contains("శాకాహారం") || lower.contains("మాంసాహార") || s.contains("शाकाहारी") || s.contains("मांसाहारी")

        val isSpiceQuery = lower.contains("spic") || lower.contains("mild") || lower.contains("hot") ||
                s.contains("కారం") || s.contains("తీఖ") || s.contains("तीखा") || s.contains("कम तीखा")

        // Multi-dimensional ask or dietary profile check (e.g. "Ask: is this vegetarian, Jain or nut-free?")
        val isComprehensiveDietaryAsk = matchedItem != null && (
                lower.startsWith("ask:") || lower.startsWith("ask ") ||
                lower.contains("overview") || lower.contains("profile") ||
                (listOf(isVegQuery, isJainQuery, isNutQuery, isVeganQuery, isDairyQuery).count { it } >= 2)
        )

        when {
            // 1. ADD TO CART QUERY
            isAddToCart -> {
                var qty = 1
                val qtyMatch = Regex("^(\\d+)").find(s)
                if (qtyMatch != null) {
                    qty = qtyMatch.groupValues[1].toIntOrNull() ?: 1
                } else if (lower.contains("two") || s.contains("రెండు") || s.contains("दो")) {
                    qty = 2
                } else if (lower.contains("three") || s.contains("మూడు") || s.contains("तीन")) {
                    qty = 3
                }

                val targetItemName = matchedItem?.nameEnglish ?: s.replace(Regex("(?i)add|to cart|to order|order|చేర్చండి|ఆర్డర్|जोड़ो|ऑर्डर करो"), "").trim()
                val payload = "$targetItemName:$qty:"

                val headline = "Added to Cart: $targetItemName"
                val spoken = if (isTelugu) {
                    "$targetItemName ($qty) కార్ట్‌లో చేర్చాను. ఇంకా ఏమైనా కావాలా?"
                } else if (isHindi) {
                    "$targetItemName ($qty) को कार्ट में जोड़ दिया गया है। क्या आप कुछ और लेना चाहेंगे?"
                } else {
                    "Added $qty $targetItemName to your order. Would you like anything else?"
                }

                return StructuredAiResponse(
                    intent = "ORDER_ITEMS",
                    headline = headline,
                    spoken = spoken,
                    caption = spoken,
                    bullets = listOf("Item: $targetItemName", "Quantity: $qty", "Status: Added to Cart"),
                    actions = listOf(
                        UiAction(id = "act_add_cart", label = "Add $targetItemName", actionType = "ADD_TO_CART", payload = payload)
                    )
                )
            }

            // 2. COMPREHENSIVE 360-DEGREE DIETARY PROFILE FOR AN ITEM
            isComprehensiveDietaryAsk -> {
                val isVeg = matchedItem.isVegetarian
                val isJain = matchedItem.isJain
                val isNutFree = matchedItem.isNutFree && !isNutRiskName(matchedItem.nameEnglish)
                val hasDairy = hasDairyKeyword(matchedItem.nameEnglish)

                val spoken = if (isTelugu) {
                    "${matchedItem.nameEnglish} ${if (isVeg) "శాకాహార వంటకం" else "మాంసాహార వంటకం"}. ఇది ${if (isJain) "జైన్ అనుకూలం" else "జైన్ కాదు (ఉల్లిపాయ/వెల్లుల్లి ఉండవచ్చు)"}, మరియు ${if (isNutFree) "నట్-ఫ్రీ" else "జీడిపప్పు పేస్ట్ ఉండే అవకాశం ఉంది"}."
                } else if (isHindi) {
                    "${matchedItem.nameEnglish} ${if (isVeg) "शाकाहारी है" else "मांसाहारी है"}। यह ${if (isJain) "जैन अनुकूल है" else "जैन प्रमाणित नहीं है (प्याज/लहसुन हो सकता है)"}, और ${if (isNutFree) "नट-फ्री है" else "इसकी ग्रेवी में काजू हो सकता है"}।"
                } else {
                    "${matchedItem.nameEnglish} is ${if (isVeg) "100% vegetarian" else "non-vegetarian"}. It is ${if (isJain) "Jain-safe (no onion or garlic)" else "not tagged Jain (may contain onion, garlic, or root vegetables)"}, and ${if (isNutFree) "is nut-free" else "may contain rich cashew nut gravy"}."
                }

                val rows = listOf(
                    listOf("Vegetarian", if (isVeg) "Yes" else "No", if (isVeg) "Plant/Dairy based" else "Contains meat/fish/egg"),
                    listOf("Jain Safe", if (isJain) "Yes" else "Staff Verification Needed", if (isJain) "No root vegetables" else "May contain onion/garlic/potato"),
                    listOf("Nut-Free", if (isNutFree) "Yes (Safe)" else "Contains Cashew/Nuts", if (isNutFree) "No nuts used" else "Gravy uses cashew paste"),
                    listOf("Dairy", if (hasDairy) "Contains Dairy" else "No Direct Dairy", if (hasDairy) "Butter/Cream/Paneer" else "Plant/Water based")
                )

                return StructuredAiResponse(
                    intent = "DIETARY_QUERY",
                    headline = "${matchedItem.nameEnglish} Dietary Profile",
                    spoken = spoken,
                    caption = spoken,
                    bullets = listOf(
                        "Dish: ${matchedItem.nameEnglish} (₹${matchedItem.priceRupees})",
                        "Vegetarian: ${if (isVeg) "Yes" else "No"}",
                        "Jain: ${if (isJain) "Compliant" else "Not Certified"}",
                        "Nut Safety: ${if (isNutFree) "Nut-Free" else "Caution (Cashews/Nuts)"}"
                    ),
                    table = StructuredTable(
                        columns = listOf("Attribute", "Status", "Details"),
                        rows = rows
                    ),
                    confidence = if (!isNutFree || !isJain) "medium" else "high",
                    needsStaffConfirmation = (!isNutFree || !isJain),
                    actions = listOf(
                        UiAction(id = "act_dietary_ans", label = "Dietary Profile", actionType = "ANSWER_DIETARY", payload = matchedItem.itemId),
                        UiAction(id = "act_highlight_${matchedItem.itemId}", label = "Highlight Dish", actionType = "HIGHLIGHT_ITEM", payload = matchedItem.itemId)
                    )
                )
            }

            // 3. JAIN QUERIES
            isJainQuery -> {
                if (matchedItem != null) {
                    val isExplicitJain = matchedItem.isJain
                    val needsConfirmation = !isExplicitJain
                    val headline = if (isExplicitJain) "${matchedItem.nameEnglish} is Jain Friendly" else "${matchedItem.nameEnglish} - Jain Verification"
                    val spoken = if (isExplicitJain) {
                        if (isTelugu) {
                            "${matchedItem.nameEnglish} జైన్ అనుకూలమైనది. ఇందులో ఉల్లిపాయ, వెల్లుల్లి లేదా దుంపలు ఉండవు."
                        } else if (isHindi) {
                            "${matchedItem.nameEnglish} 100% जैन अनुकूल है। इसमें प्याज, लहसुन या कंदमूल नहीं है।"
                        } else {
                            "${matchedItem.nameEnglish} is 100% Jain friendly and prepared without onion, garlic, or root vegetables."
                        }
                    } else {
                        if (isTelugu) {
                            "${matchedItem.nameEnglish} లో సాధారణంగా ఉల్లిపాయ, వెల్లుల్లి లేదా బంగాళాదుంప ఉంటాయి మరియు ఇది జైన్ విభాగంలో లేదు. జైన్ పద్ధతిలో చేయగలరా అని సిబ్బందిని అడగండి."
                        } else if (isHindi) {
                            "साधारण ${matchedItem.nameEnglish} में आमतौर पर प्याज, लहसुन या आलू होता है। क्या आप स्टाफ से जैन विकल्प के लिए पूछना चाहेंगे?"
                        } else {
                            "Standard ${matchedItem.nameEnglish} typically contains onion, garlic, or root vegetables and is not tagged Jain. Please check with the waiter for a Jain customization."
                        }
                    }

                    val actions = mutableListOf(
                        UiAction(id = "act_highlight_${matchedItem.itemId}", label = "View ${matchedItem.nameEnglish}", actionType = "HIGHLIGHT_ITEM", payload = matchedItem.itemId)
                    )
                    if (needsConfirmation) {
                        actions.add(
                            UiAction(id = "act_ask_waiter", label = "Ask Waiter for Jain Prep", actionType = "ASK_WAITER", payload = "Can ${matchedItem.nameEnglish} be made Jain without onion and garlic?")
                        )
                    }

                    return StructuredAiResponse(
                        intent = "DIETARY_QUERY",
                        headline = headline,
                        spoken = spoken,
                        caption = spoken,
                        bullets = listOf(
                            "Dish: ${matchedItem.nameEnglish}",
                            "Jain Status: ${if (isExplicitJain) "Compliant (No root veg/onion/garlic)" else "Not Certified Jain (Contains root veg/onion/garlic)"}",
                            "Price: ₹${matchedItem.priceRupees}"
                        ),
                        confidence = if (isExplicitJain) "high" else "medium",
                        needsStaffConfirmation = needsConfirmation,
                        actions = actions
                    )
                } else {
                    val jainItems = menuItems.filter { it.isJain }
                    if (jainItems.isNotEmpty()) {
                        val sampleNames = jainItems.take(3).joinToString(", ") { it.nameEnglish }
                        val spoken = if (isTelugu) {
                            "మా వద్ద అందుబాటులో ఉన్న జైన్ వంటకాలు: $sampleNames. ఇవి ఉల్లిపాయ, వెల్లుల్లి లేకుండా తయారు చేయబడతాయి."
                        } else if (isHindi) {
                            "यहाँ उपलब्ध जैन व्यंजन: $sampleNames। ये बिना प्याज, लहसुन और कंदमूल के बनते हैं।"
                        } else {
                            "Here are our Jain-friendly dishes: $sampleNames. All prepared strictly without onion, garlic, or root vegetables."
                        }

                        val rows = jainItems.take(5).map { item ->
                            listOf(item.nameEnglish, "₹${item.priceRupees}", "Jain Safe", "${item.spiceLevel}/3")
                        }

                        return StructuredAiResponse(
                            intent = "DIETARY_QUERY",
                            headline = "Jain Friendly Dishes (${jainItems.size})",
                            spoken = spoken,
                            caption = spoken,
                            bullets = jainItems.map { "${it.nameEnglish} - ₹${it.priceRupees} (Jain Safe)" },
                            table = StructuredTable(
                                columns = listOf("Dish Name", "Price", "Dietary", "Spice Level"),
                                rows = rows
                            ),
                            actions = listOf(
                                UiAction(id = "act_filter_jain", label = "Filter Jain Menu", actionType = "FILTER_JAIN", payload = "jain")
                            )
                        )
                    } else {
                        val spoken = if (isTelugu) {
                            "మెనూలో ప్రత్యేకంగా జైన్ వంటకాలు లేవు. ఉల్లిపాయ, వెల్లుల్లి లేకుండా తయారు చేయవచ్చా అని సిబ్బందిని అడగండి."
                        } else if (isHindi) {
                            "मेनू में कोई व्यंजन स्पष्ट रूप से जैन चिह्नित नहीं है। कृपया वेटर से पूछें कि क्या बिना प्याज-लहसुन का भोजन मिल सकता है।"
                        } else {
                            "No dishes are explicitly marked Jain on the current menu. Please ask our staff if the kitchen can prepare your meal without onion and garlic."
                        }

                        return StructuredAiResponse(
                            intent = "DIETARY_QUERY",
                            headline = "No Explicit Jain Dishes",
                            spoken = spoken,
                            caption = spoken,
                            bullets = listOf("No pre-marked Jain dishes found", "Staff confirmation recommended for custom preparation"),
                            needsStaffConfirmation = true,
                            actions = listOf(
                                UiAction(id = "act_ask_waiter", label = "Ask Staff for Jain Prep", actionType = "ASK_WAITER", payload = "Are Jain meals available without onion and garlic?")
                            )
                        )
                    }
                }
            }

            // 4. NUT ALLERGY & NUT-FREE QUERIES
            isNutQuery -> {
                if (matchedItem != null) {
                    val hasNutRisk = !matchedItem.isNutFree || isNutRiskName(matchedItem.nameEnglish)
                    val headline = if (hasNutRisk) "${matchedItem.nameEnglish} - Contains Nuts / Cashews" else "${matchedItem.nameEnglish} is Nut-Free"
                    val spoken = if (hasNutRisk) {
                        if (isTelugu) {
                            "${matchedItem.nameEnglish} లో రిచ్ గ్రేవీ కోసం కాజు (జీడిపప్పు) పేస్ట్ ఉండే అవకాశం ఉంది. మీకు నట్ అలర్జీ ఉంటే దయచేసి సిబ్బందిని సంప్రదించండి."
                        } else if (isHindi) {
                            "${matchedItem.nameEnglish} में गाढ़ी ग्रेवी के लिए काजू का पेस्ट हो सकता है। यदि आपको नट एलर्जी है, तो कृपया स्टाफ से पुष्टि करें।"
                        } else {
                            "${matchedItem.nameEnglish} typically contains rich cashew paste (kaju) in the gravy. If you have a nut allergy, please confirm with restaurant staff before ordering."
                        }
                    } else {
                        if (isTelugu) {
                            "${matchedItem.nameEnglish} లో ఎటువంటి జీడిపప్పు లేదా నట్స్ లేవు (నట్-ఫ్రీ)."
                        } else if (isHindi) {
                            "${matchedItem.nameEnglish} नट-फ्री है। इसमें काजू, मूंगफली या बादाम नहीं हैं।"
                        } else {
                            "${matchedItem.nameEnglish} is nut-free and contains no cashews, peanuts, or tree nuts."
                        }
                    }

                    return StructuredAiResponse(
                        intent = "DIETARY_QUERY",
                        headline = headline,
                        spoken = spoken,
                        caption = spoken,
                        bullets = listOf(
                            "Dish: ${matchedItem.nameEnglish}",
                            "Nut Status: ${if (hasNutRisk) "Contains / May Contain Cashew (Kaju) or Nut Paste" else "Nut-Free Safe"}",
                            "Vegetarian: ${if (matchedItem.isVegetarian) "Yes" else "No"}"
                        ),
                        confidence = if (hasNutRisk) "medium" else "high",
                        needsStaffConfirmation = hasNutRisk,
                        actions = listOf(
                            UiAction(id = "act_nut_dietary", label = "Dietary Profile", actionType = "ANSWER_DIETARY", payload = matchedItem.itemId),
                            UiAction(id = "act_highlight_${matchedItem.itemId}", label = "View ${matchedItem.nameEnglish}", actionType = "HIGHLIGHT_ITEM", payload = matchedItem.itemId)
                        )
                    )
                } else {
                    val nutFreeItems = menuItems.filter { it.isNutFree && !isNutRiskName(it.nameEnglish) }
                    val sampleNames = nutFreeItems.take(3).joinToString(", ") { it.nameEnglish }
                    val spoken = if (isTelugu) {
                        "మా వద్ద $sampleNames వంటి నట్-ఫ్రీ వంటకాలు ఉన్నాయి. తీవ్రమైన అలర్జీ ఉంటే దయచేసి సిబ్బందికి తెలియజేయండి."
                    } else if (isHindi) {
                        "हमारे पास $sampleNames जैसे नट-फ्री व्यंजन उपलब्ध हैं। गंभीर एलर्जी के लिए स्टाफ को अवश्य बताएं।"
                    } else {
                        "We have safe nut-free options like $sampleNames. For severe allergies, please notify the staff."
                    }

                    val rows = nutFreeItems.take(5).map { item ->
                        listOf(item.nameEnglish, "₹${item.priceRupees}", if (item.isVegetarian) "Veg, Nut-Free" else "Nut-Free", "${item.spiceLevel}/3")
                    }

                    return StructuredAiResponse(
                        intent = "DIETARY_QUERY",
                        headline = "Nut-Free Options (${nutFreeItems.size})",
                        spoken = spoken,
                        caption = spoken,
                        bullets = nutFreeItems.take(5).map { "${it.nameEnglish} - ₹${it.priceRupees} (Nut-Free)" },
                        table = StructuredTable(
                            columns = listOf("Dish Name", "Price", "Dietary", "Spice Level"),
                            rows = rows
                        ),
                        actions = listOf(
                            UiAction(id = "act_filter_nut", label = "Filter Nut-Free Menu", actionType = "FILTER_NUT_FREE", payload = "nut_free")
                        )
                    )
                }
            }

            // 5. VEGAN & DAIRY-FREE QUERIES
            isVeganQuery -> {
                val veganItems = menuItems.filter { it.isVegan || (it.isVegetarian && !hasDairyKeyword(it.nameEnglish)) }
                val sampleNames = veganItems.take(3).joinToString(", ") { it.nameEnglish }
                val spoken = if (isTelugu) {
                    "ఇక్కడ పాలు, వెన్న, నెయ్యి లేని స్వచ్ఛమైన వీగన్ వంటకాలు: $sampleNames."
                } else if (isHindi) {
                    "यहाँ बिना दूध, मक्खन और घी वाले वीगन व्यंजन: $sampleNames।"
                } else {
                    "Here are 100% plant-based vegan options without dairy, butter, or ghee: $sampleNames."
                }

                val rows = veganItems.take(5).map { item ->
                    listOf(item.nameEnglish, "₹${item.priceRupees}", "Vegan Plant-Based", "${item.spiceLevel}/3")
                }

                return StructuredAiResponse(
                    intent = "DIETARY_QUERY",
                    headline = "Vegan & Plant-Based Options (${veganItems.size})",
                    spoken = spoken,
                    caption = spoken,
                    bullets = veganItems.take(5).map { "${it.nameEnglish} - ₹${it.priceRupees} (Vegan)" },
                    table = StructuredTable(
                        columns = listOf("Dish Name", "Price", "Type", "Spice Level"),
                        rows = rows
                    ),
                    actions = listOf(
                        UiAction(id = "act_filter_vegan", label = "Filter Vegan Options", actionType = "FILTER_VEG", payload = "vegan")
                    )
                )
            }

            // 6. DAIRY / BUTTER / CREAM QUERIES
            isDairyQuery -> {
                if (matchedItem != null) {
                    val containsDairy = hasDairyKeyword(matchedItem.nameEnglish)
                    val headline = if (containsDairy) "${matchedItem.nameEnglish} Contains Dairy" else "${matchedItem.nameEnglish} - Dairy Details"
                    val spoken = if (containsDairy) {
                        if (isTelugu) {
                            "${matchedItem.nameEnglish} లో వెన్న, నెయ్యి మరియు క్రీమ్ వంటి పాల ఉత్పత్తులు ఉంటాయి."
                        } else if (isHindi) {
                            "${matchedItem.nameEnglish} में मक्खन, घी और मलाई (डेयरी) शामिल हैं।"
                        } else {
                            "${matchedItem.nameEnglish} contains dairy, including butter and cream, which gives it its rich texture."
                        }
                    } else {
                        if (isTelugu) {
                            "${matchedItem.nameEnglish} లో ప్రత్యక్షంగా వెన్న లేదా పాల ఉత్పత్తులు లేవు."
                        } else if (isHindi) {
                            "${matchedItem.nameEnglish} में प्रत्यक्ष रूप से मक्खन या दूध नहीं है।"
                        } else {
                            "${matchedItem.nameEnglish} does not contain obvious butter or milk dairy products."
                        }
                    }

                    return StructuredAiResponse(
                        intent = "DIETARY_QUERY",
                        headline = headline,
                        spoken = spoken,
                        caption = spoken,
                        bullets = listOf(
                            "Dish: ${matchedItem.nameEnglish}",
                            "Dairy Status: ${if (containsDairy) "Contains Dairy (Butter/Cream/Ghee/Paneer)" else "No Direct Dairy"}"
                        ),
                        actions = listOf(
                            UiAction(id = "act_dairy_ans", label = "Dietary Details", actionType = "ANSWER_DIETARY", payload = matchedItem.itemId)
                        )
                    )
                } else {
                    val nonDairyItems = menuItems.filter { !hasDairyKeyword(it.nameEnglish) }
                    val sampleNames = nonDairyItems.take(3).joinToString(", ") { it.nameEnglish }
                    val spoken = if (isTelugu) {
                        "పాల ఉత్పత్తులు లేని వంటకాలు: $sampleNames."
                    } else if (isHindi) {
                        "बिना डेयरी वाले व्यंजन: $sampleNames।"
                    } else {
                        "Non-dairy options include: $sampleNames."
                    }

                    return StructuredAiResponse(
                        intent = "DIETARY_QUERY",
                        headline = "Dairy-Free Options",
                        spoken = spoken,
                        caption = spoken,
                        bullets = nonDairyItems.take(5).map { "${it.nameEnglish} - ₹${it.priceRupees}" },
                        actions = listOf(
                            UiAction(id = "act_answer_dietary", label = "View Options", actionType = "ANSWER_DIETARY", payload = "dairy_free")
                        )
                    )
                }
            }

            // 7. VEGETARIAN / GENERAL FOOD QUERIES
            isVegQuery -> {
                if (matchedItem != null) {
                    val isVeg = matchedItem.isVegetarian
                    val headline = "${matchedItem.nameEnglish} - ${if (isVeg) "Vegetarian" else "Non-Vegetarian"}"
                    val spoken = if (isVeg) {
                        if (isTelugu) {
                            "${matchedItem.nameEnglish} 100% స్వచ్ఛమైన శాకాహార వంటకం."
                        } else if (isHindi) {
                            "${matchedItem.nameEnglish} 100% शुद्ध शाकाहारी व्यंजन है।"
                        } else {
                            "${matchedItem.nameEnglish} is 100% vegetarian."
                        }
                    } else {
                        if (isTelugu) {
                            "${matchedItem.nameEnglish} మాంసాహార వంటకం."
                        } else if (isHindi) {
                            "${matchedItem.nameEnglish} मांसाहारी व्यंजन है।"
                        } else {
                            "${matchedItem.nameEnglish} is a non-vegetarian dish."
                        }
                    }

                    return StructuredAiResponse(
                        intent = "DIETARY_QUERY",
                        headline = headline,
                        spoken = spoken,
                        caption = spoken,
                        bullets = listOf(
                            "Dish: ${matchedItem.nameEnglish}",
                            "Classification: ${if (isVeg) "Vegetarian (Green Dot)" else "Non-Vegetarian (Red Dot)"}",
                            "Price: ₹${matchedItem.priceRupees}"
                        ),
                        actions = listOf(
                            UiAction(id = "act_highlight_${matchedItem.itemId}", label = "View ${matchedItem.nameEnglish}", actionType = "HIGHLIGHT_ITEM", payload = matchedItem.itemId)
                        )
                    )
                } else {
                    val vegItems = menuItems.filter { it.isVegetarian }
                    val sampleNames = vegItems.take(3).joinToString(", ") { it.nameEnglish }
                    val spoken = if (isTelugu) {
                        "మా వద్ద $sampleNames వంటి 100% శాకాహార వంటకాలు ఉన్నాయి."
                    } else if (isHindi) {
                        "हमारे पास $sampleNames जैसे 100% शाकाहारी विकल्प उपलब्ध हैं।"
                    } else {
                        "We have ${vegItems.size} vegetarian dishes available, including $sampleNames."
                    }

                    val rows = vegItems.take(5).map { item ->
                        listOf(item.nameEnglish, "₹${item.priceRupees}", "Vegetarian", "${item.spiceLevel}/3")
                    }

                    return StructuredAiResponse(
                        intent = "DIETARY_QUERY",
                        headline = "Vegetarian Menu (${vegItems.size} items)",
                        spoken = spoken,
                        caption = spoken,
                        bullets = vegItems.take(5).map { "${it.nameEnglish} - ₹${it.priceRupees} (Veg)" },
                        table = StructuredTable(
                            columns = listOf("Dish Name", "Price", "Category", "Spice Level"),
                            rows = rows
                        ),
                        actions = listOf(
                            UiAction(id = "act_filter_veg", label = "Filter Vegetarian Menu", actionType = "FILTER_VEG", payload = "vegetarian")
                        )
                    )
                }
            }

            // 8. SPICE LEVEL QUERIES
            isSpiceQuery -> {
                val mildItems = menuItems.filter { it.spiceLevel == 0 || it.spiceLevel == 1 }
                val sampleNames = mildItems.take(3).joinToString(", ") { it.nameEnglish }
                val spoken = if (isTelugu) {
                    "కారం తక్కువగా ఉండే వంటకాలు: $sampleNames."
                } else if (isHindi) {
                    "कम तीखे और हल्के व्यंजन: $sampleNames।"
                } else {
                    "Mild and low-spice dishes include: $sampleNames."
                }

                return StructuredAiResponse(
                    intent = "DIETARY_QUERY",
                    headline = "Mild & Low-Spice Dishes",
                    spoken = spoken,
                    caption = spoken,
                    bullets = mildItems.take(5).map { "${it.nameEnglish} - Spice: ${it.spiceLevel}/3" },
                    actions = listOf(
                        UiAction(id = "act_dietary_spice", label = "View Mild Dishes", actionType = "ANSWER_DIETARY", payload = "mild")
                    )
                )
            }

            // 9. GENERAL OVERVIEW FALLBACK
            else -> {
                val spoken = if (isTelugu) {
                    "$restaurantName లోని వంటకాలపై జైన్, శాకాహారం లేదా అలర్జీ వివరాలను తెలుసుకోవడానికి నన్ను అడగండి."
                } else if (isHindi) {
                    "$restaurantName के व्यंजनों के लिए जैन, शाकाहारी या नट-एलर्जी की जानकारी के लिए मुझसे पूछें।"
                } else {
                    "Ask me about vegetarian, Jain, nut-free, vegan, or allergen information for any dish at $restaurantName."
                }

                return StructuredAiResponse(
                    intent = "DIETARY_QUERY",
                    headline = "Dietary & Allergen Assistance",
                    spoken = spoken,
                    caption = spoken,
                    bullets = listOf(
                        "Restaurant: $restaurantName",
                        "Supported Inquiries: Vegetarian, Jain (No onion/garlic), Nut-free (Kaju/Peanut), Vegan, Dairy & Spice level"
                    ),
                    actions = listOf(
                        UiAction(id = "act_dietary_general", label = "Explore Dietary Options", actionType = "ANSWER_DIETARY", payload = "general")
                    )
                )
            }
        }
    }

    private fun findMatchingMenuItem(query: String, items: List<MenuItemEntity>): MenuItemEntity? {
        val qLower = query.lowercase().trim()
        if (qLower.isEmpty() || items.isEmpty()) return null

        // 1. Direct name match in English, Hindi, Telugu, or transliteration
        items.firstOrNull { item ->
            qLower.contains(item.nameEnglish.lowercase()) ||
                    (!item.transliteration.isNullOrBlank() && qLower.contains(item.transliteration.lowercase())) ||
                    (!item.nameHindi.isNullOrBlank() && query.contains(item.nameHindi)) ||
                    (!item.nameTelugu.isNullOrBlank() && query.contains(item.nameTelugu))
        }?.let { return it }

        // 2. Transliteration of Indic dish names in Telugu/Hindi script
        val indicDishMappings = listOf(
            listOf("పనీర్", "पनीर") to "paneer",
            listOf("దోశ", "దోస", "డోసా", "डोसा") to "dosa",
            listOf("ఇడ్లీ", "इडली") to "idli",
            listOf("వడ", "వడా", "वड़ा", "वडा") to "vada",
            listOf("సాంబార్", "సాంబారు", "सांभर") to "sambar",
            listOf("దాల్", "పప్పు", "दाल") to "dal",
            listOf("మఖని", "మఖనీ", "मखनी") to "makhani",
            listOf("బిర్యానీ", "బిరియాని", "बिरयानी") to "biryani",
            listOf("కబాబ్", "కబాబు", "कबाब") to "kebab",
            listOf("రోటీ", "రొట్టె", "रोटी") to "roti",
            listOf("నాన్", "नान") to "naan",
            listOf("ఉత్తప్పం", "ఉతప్పం", "उत्तपम") to "uttapam",
            listOf("ఛోలే", "చోలే", "छोले") to "chole",
            listOf("భటూరా", "భటూరే", "भटूरा", "भटूरे") to "bhature",
            listOf("పావ్", "పావ్ భాజీ", "पाव भाजी") to "pav bhaji",
            listOf("కాఫీ", "कॉफ़ी", "कॉफी") to "coffee",
            listOf("టీ", "చాయ్", "चाय") to "tea",
            listOf("లస్సీ", "लस्सी") to "lassi",
            listOf("గులాబ్ జామున్", "गुलाब जामुन") to "gulab jamun"
        )

        for ((indicKeys, engKeyword) in indicDishMappings) {
            if (indicKeys.any { query.contains(it) }) {
                val match = items.firstOrNull { it.nameEnglish.lowercase().contains(engKeyword) }
                if (match != null) return match
            }
        }

        // 3. Multi-word English dish names
        val commonDishKeywords = listOf(
            "paneer butter masala", "butter masala", "shahi paneer", "paneer tikka",
            "masala dosa", "plain dosa", "rava dosa", "mysore bonda",
            "dal makhani", "dal tadka", "idli sambar", "medu vada", "hara bhara kebab",
            "veg biryani", "chicken biryani", "mutton biryani", "chole bhature", "pav bhaji",
            "filter coffee", "masala chai", "gulab jamun"
        )
        for (kw in commonDishKeywords) {
            if (qLower.contains(kw)) {
                val found = items.firstOrNull { it.nameEnglish.lowercase().contains(kw) }
                if (found != null) return found
            }
        }

        // 4. Significant token matching excluding stop words and dietary filter words
        val genericStopWords = setOf(
            "the", "and", "with", "for", "special", "hot", "cold", "fresh", "delicious",
            "jain", "veg", "vegetarian", "vegan", "non-veg", "food", "dish", "dishes",
            "item", "items", "menu", "starter", "starters", "main", "course", "tiffin",
            "tiffins", "mild", "spicy", "here", "what", "which", "are", "have", "does",
            "this", "that", "ask", "tell", "show", "is", "it", "any", "some"
        )

        return items.firstOrNull { item ->
            val words = item.nameEnglish.lowercase()
                .split(" ", "-", "/")
                .filter { it.length > 2 && it !in genericStopWords }
            words.isNotEmpty() && words.any { qLower.contains(it) }
        }
    }

    private fun isNutRiskName(name: String): Boolean {
        val lower = name.lowercase()
        return lower.contains("kaju") || lower.contains("cashew") || lower.contains("butter masala") ||
                lower.contains("shahi") || lower.contains("korma") || lower.contains("malai kofta") ||
                lower.contains("pasanda") || lower.contains("makhani") || lower.contains("mughlai") ||
                lower.contains("badam") || lower.contains("almond") || lower.contains("pista")
    }

    private fun hasDairyKeyword(name: String): Boolean {
        val lower = name.lowercase()
        return lower.contains("paneer") || lower.contains("butter") || lower.contains("ghee") ||
                lower.contains("milk") || lower.contains("cream") || lower.contains("malai") ||
                lower.contains("curd") || lower.contains("dahi") || lower.contains("raita") ||
                lower.contains("cheese") || lower.contains("lassi") || lower.contains("makhani")
    }
}
