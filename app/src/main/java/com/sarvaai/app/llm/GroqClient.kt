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
import kotlinx.coroutines.async
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

data class CulinaryDishInfo(
    val nameEnglish: String,
    val aliases: List<String>,
    val category: String,
    val origin: String,
    val flavorProfile: String,
    val dietary: String,
    val isVeg: Boolean,
    val isJain: Boolean,
    val isNutFree: Boolean,
    val hasDairy: Boolean,
    val spiceLevel: Int, // 0=Mild, 1=Medium, 2=Spicy, 3=Very Spicy
    val spiceLevelStr: String,
    val spokenEn: String,
    val spokenHi: String,
    val spokenTe: String
)

class GroqClient(
    private val context: Context? = null,
    private val okHttpClient: OkHttpClient = OkHttpFactory.defaultClient,
    private val json: Json = Json { ignoreUnknownKeys = true; isLenient = true; encodeDefaults = true },
    private val culinaryWebSearchClient: CulinaryWebSearchClient = CulinaryWebSearchClient()
) {
    companion object {
        private const val TAG = "GroqClient"
        private const val GROQ_API_URL = "https://api.groq.com/openai/v1/chat/completions"
        const val MODEL_LLAMA_70B = "llama-3.3-70b-versatile"
        const val MODEL_LLAMA_8B = "llama-3.1-8b-instant"

        const val REDIRECTION_EN = "I am Svara, your restaurant assistant. I can answer any questions about dishes, ingredients, recipes, and your menu orders."
        const val REDIRECTION_HI = "मैं स्वरा हूँ, आपकी रेस्तरां सहायक। मैं केवल भोजन, व्यंजनों, सामग्री और आपके मेनू ऑर्डर से संबंधित सवालों के जवाब दे सकती हूँ।"
        const val REDIRECTION_TE = "నేను స్వర, మీ రెస్టారెంట్ అసిస్టెంట్. నేను ఆహారం, వంటకాలు, పదార్థాలు మరియు మెనూ ఆర్డర్‌ల గురించిన ప్రశ్నలకు మాత్రమే సమాధానం ఇవ్వగలను."

        const val SYSTEM_PROMPT = """
You are Svara (SarvaAI), a fast, empathetic, voice-first multimodal restaurant ordering and culinary assistant.
You understand customer speech in Telugu (te), Hindi (hi), and English (en) universally.

STRICT FOOD & DINING GUARDRAILS:
- You answer ONLY food, beverage, menu, dietary, ingredient, recipe, bill, and restaurant ordering questions.
- If the customer asks non-food questions (e.g. politics, coding, software, math, general trivia, weather, stocks):
  Politely decline with this exact food assistant redirection in their language:
  - English: "I am Svara, your restaurant assistant. I can answer any questions about dishes, ingredients, recipes, and your menu orders."
  - Hindi: "मैं स्वरा हूँ, आपकी रेस्तरां सहायक। मैं केवल भोजन, व्यंजनों, सामग्री और आपके मेनू ऑर्डर से संबंधित सवालों के जवाब दे सकती हूँ।"
  - Telugu: "నేను స్వర, మీ రెస్టారెంట్ అసిస్టెంట్. నేను ఆహారం, వంటకాలు, పదార్థాలు మరియు మెనూ ఆర్డర్‌ల గురించిన ప్రశ్నలకు మాత్రమే సమాధానం ఇవ్వగలను."

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

        const val CULINARY_SYSTEM_PROMPT = """
You are Svara, an expert culinary, dietary, and dining intelligence AI assistant for Indian and international restaurants.
You understand customer queries in Telugu (te), Hindi (hi), and English (en) universally.

STRICT FOOD & DINING GUARDRAILS:
- Answer ONLY food, beverage, ingredient, recipe, dietary, allergen, restaurant, and menu questions.
- If the customer asks non-food questions (e.g. politics, coding, software, math, general trivia, weather, stocks, entertainment):
  Politely decline with this exact food assistant redirection in their language:
  - English: "I am Svara, your restaurant assistant. I can answer any questions about dishes, ingredients, recipes, and your menu orders."
  - Hindi: "मैं स्वरा हूँ, आपकी रेस्तरां सहायक। मैं केवल भोजन, व्यंजनों, सामग्री और आपके मेनू ऑर्डर से संबंधित सवालों के जवाब दे सकती हूँ।"
  - Telugu: "నేను స్వర, మీ రెస్టారెంట్ అసిస్టెంట్. నేను ఆహారం, వంటకాలు, పదార్థాలు మరియు మెనూ ఆర్డర్‌ల గురించిన ప్రశ్నలకు మాత్రమే సమాధానం ఇవ్వగలను."

GENERAL CULINARY KNOWLEDGE & DISH EXPLANATIONS:
Explain ANY dish queried (e.g. "What is chilli chicken?", "What is chicken 65?", "How is biryani prepared?", "What is rasam?", "Is fish curry spicy?"):
1. Origin & Culinary style (e.g. Chilli Chicken is a classic Indo-Chinese dish made of crispy fried chicken pieces tossed with green chilies, garlic, soy sauce, and capsicum).
2. Flavor & texture profile (tangy, savory, spicy, crispy, velvety, smoky).
3. Dietary classification (Non-veg / Vegetarian, Jain suitability, Vegan, dairy-free / contains dairy, nut-free / cashew gravy, contains soy/gluten, spice level 0/3 to 3/3).
4. Natural, appetizing spoken response in customer's native language (Telugu if 'te', Hindi if 'hi', English if 'en').

DIETARY & ALLERGEN INTELLIGENCE:
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
- "ANSWER_DIETARY": When providing culinary/dietary advice, dish explanation, or answering questions (payload: "<Item Name or Topic>").
- "ASK_WAITER": When allergen safety or kitchen preparation needs waiter confirmation (payload: "<Question for staff>").

OUTPUT FORMAT:
Return ONLY valid JSON matching this schema:
{
  "intent": "DIETARY_QUERY" | "ORDER_ITEMS" | "ASK_MENU" | "GENERAL_QUERY",
  "headline": "Short English summary of culinary / dietary response",
  "spoken": "Natural, appetizing spoken response in customer's native language (Telugu if 'te', Hindi if 'hi', English if 'en')",
  "caption": "Clean text for on-screen live captions in customer's language",
  "bullets": ["Key bullet points in English"],
  "table": {
    "columns": ["Attribute", "Details"],
    "rows": [["Origin", "Indo-Chinese"], ["Flavor", "Spicy & Tangy"], ["Dietary", "Non-Veg, Nut-Free"], ["Spice Level", "2/3 (Spicy)"]]
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

        const val DIETARY_SYSTEM_PROMPT = CULINARY_SYSTEM_PROMPT

        val CULINARY_ENCYCLOPEDIA: List<CulinaryDishInfo> = listOf(
            // 1. Chilli Chicken
            CulinaryDishInfo(
                nameEnglish = "Chilli Chicken",
                aliases = listOf("chilli chicken", "chili chicken", "chilly chicken", "చిల్లీ చికెన్", "మిర్చి చికెన్", "चिल्ली चिकन", "चिली चिकन"),
                category = "Indo-Chinese Non-Vegetarian",
                origin = "Indo-Chinese (Kolkata Tangra style)",
                flavorProfile = "Tangy, savory, spicy, crispy with aromatic garlic and soy",
                dietary = "Non-Vegetarian, Dairy-Free, Nut-Free, Contains Soy & Gluten",
                isVeg = false,
                isJain = false,
                isNutFree = true,
                hasDairy = false,
                spiceLevel = 2,
                spiceLevelStr = "Spicy (2/3)",
                spokenEn = "Chilli Chicken is a classic Indo-Chinese dish made of crispy fried chicken pieces tossed with green chilies, garlic, soy sauce, and capsicum. It has a tangy, savory, and spicy flavor profile (spice level 2/3, non-vegetarian, dairy-free, and nut-free).",
                spokenHi = "चिल्ली चिकन एक प्रसिद्ध इंडो-चाइनीज व्यंजन है। इसमें कुरकुरे चिकन के टुकड़ों को हरी मिर्च, लहसुन, शिमला मिर्च और सोया सॉस के साथ पकाया जाता है। इसका स्वाद तीखा, चटपटा और कुरकुरा (तीखापन 2/3) होता है। यह मांसाहारी, डेयरी-फ्री और नट-फ्री है।",
                spokenTe = "చిల్లీ చికెన్ అనేది ఇండో-చైనీస్ శైలి వంటకం. ఇది క్రిస్పీగా వేయించిన చికెన్ ముక్కలను పచ్చిమిర్చి, వెల్లుల్లి, క్యాప్సికం మరియు సోయా సాస్‌తో టాస్ చేసి తయారు చేస్తారు. ఇది ఘాటైన, స్పైసీ రుచితో (కారం 2/3) మాంసాహార, డైరీ-ఫ్రీ మరియు నట్-ఫ్రీ వంటకం."
            ),
            // 2. Chicken 65
            CulinaryDishInfo(
                nameEnglish = "Chicken 65",
                aliases = listOf("chicken 65", "chicken sixty five", "చికెన్ 65", "చికెన్ సిక్స్టీ ఫైవ్", "चिकन 65", "चिकन सिक्सटी फाइव"),
                category = "South Indian Non-Vegetarian Starter",
                origin = "South Indian (Chennai Buhari Hotel, 1965)",
                flavorProfile = "Crispy, fiery, tangy, with aromatic fried curry leaves",
                dietary = "Non-Vegetarian, Nut-Free, Contains Dairy (Yogurt Marinade)",
                isVeg = false,
                isJain = false,
                isNutFree = true,
                hasDairy = true,
                spiceLevel = 3,
                spiceLevelStr = "Very Spicy (3/3)",
                spokenEn = "Chicken 65 is a legendary South Indian spicy deep-fried chicken appetizer from Chennai. Marinated in red chilies, ginger, garlic, yogurt, and curry leaves, it is fiery, crispy, and aromatic (spice level 3/3, non-vegetarian, nut-free).",
                spokenHi = "चिकन 65 चेन्नई का एक बेहद लोकप्रिय दक्षिण भारतीय डीप-फ्राइड स्टार्टर है। इसे लाल मिर्च, अदरक, लहसुन, दही और कढ़ी पत्ते के साथ मैरीनेट करके कुरकुरा तला जाता है। यह काफी तीखा और स्वादिष्ट (तीखापन 3/3, मांसाहारी, नट-फ्री) होता है।",
                spokenTe = "చికెన్ 65 అనేది చెన్నై నుండి వచ్చిన ప్రసిద్ధ దక్షిణ భారత స్పైసీ డీప్-ఫ్రైడ్ చికెన్ వంటకం. అల్లం, వెల్లుల్లి, ఎర్ర మిర్చి, పెరుగు మరియు కరివేపాకుతో మెరినేట్ చేసి వేయిస్తారు. ఇది చాలా కారంగా, క్రిస్పీగా (కారం 3/3, నట్-ఫ్రీ) ఉంటుంది."
            ),
            // 3. Butter Chicken
            CulinaryDishInfo(
                nameEnglish = "Butter Chicken",
                aliases = listOf("butter chicken", "murgh makhani", "murg makhani", "బటర్ చికెన్", "ముర్గ్ మఖని", "बटर चिकन", "मुर्ग मखनी"),
                category = "North Indian Non-Vegetarian Main Course",
                origin = "North Indian / Punjabi (Delhi Moti Mahal)",
                flavorProfile = "Velvety, creamy, mildly sweet, savory with fenugreek aromatics",
                dietary = "Non-Vegetarian, Contains Dairy (Butter, Cream), Contains Tree Nuts (Cashew Paste)",
                isVeg = false,
                isJain = false,
                isNutFree = false,
                hasDairy = true,
                spiceLevel = 1,
                spiceLevelStr = "Mild (1/3)",
                spokenEn = "Butter Chicken, or Murgh Makhani, is a classic North Indian dish featuring tandoori-grilled chicken in a rich, velvety gravy of tomatoes, butter, fresh cream, and cashew nut paste. It has a mild, savory-sweet flavor (spice level 1/3, contains dairy and cashews).",
                spokenHi = "बटर चिकन (मुर्ग मखनी) एक प्रसिद्ध उत्तर भारतीय व्यंजन है। इसमें तंदूरी चिकन को टमाटर, मक्खन, ताजी मलाई और काजू की ग्रेवी में पकाया जाता है। इसका स्वाद हल्का मीठा, मलाईदार और कम तीखा (1/3) होता है (इसमें डेयरी और काजू हैं)।",
                spokenTe = "బటర్ చికెన్ (ముర్గ్ మఖని) ప్రసిద్ధ ఉత్తర భారత వంటకం. తందూరి చికెన్ ముక్కలను టమోటా, వెన్న, ఫ్రెష్ క్రీమ్ మరియు జీడిపప్పు పేస్ట్ తో కూడిన క్రీమీ గ్రేవీలో వండుతారు. ఇది తక్కువ కారంతో (1/3), తియ్యటి రుచితో ఉంటుంది (పాల ఉత్పత్తులు మరియు జీడిపప్పు ఉంటాయి)."
            ),
            // 4. Chicken Tikka
            CulinaryDishInfo(
                nameEnglish = "Chicken Tikka",
                aliases = listOf("chicken tikka", "murgh tikka", "చికెన్ టిక్కా", "चिकन टिक्का"),
                category = "Tandoori Non-Vegetarian Starter",
                origin = "North Indian / Punjabi Tandoori",
                flavorProfile = "Smoky, charred, tender, juicy, and zesty",
                dietary = "Non-Vegetarian, Nut-Free, Contains Dairy (Hung Curd Marinade)",
                isVeg = false,
                isJain = false,
                isNutFree = true,
                hasDairy = true,
                spiceLevel = 2,
                spiceLevelStr = "Medium Spicy (2/3)",
                spokenEn = "Chicken Tikka is a classic tandoori appetizer consisting of tender boneless chicken marinated in spiced yogurt and roasted in a traditional clay oven. It has a smoky, zesty flavor (spice level 2/3, non-vegetarian, nut-free, contains yogurt dairy).",
                spokenHi = "चिकन टिक्का एक लोकप्रिय तंदूरी स्टार्टर है। इसमें बोनलेस चिकन को मसालेदार गाढ़े दही में मैरीनेट करके तंदूर में सेका जाता है। इसका स्वाद स्मोकी, चटपटा और रसीला (तीखापन 2/3, मांसाहारी, नट-फ्री) होता है।",
                spokenTe = "చికెన్ టిక్కా అనేది సంప్రదాయ తందూరి స్టార్టర్. ఎముకలు లేని చికెన్ ముక్కలను మసాలా పెరుగులో నానబెట్టి క్లే ఓవెన్ లో కాలుస్తారు. ఇది మంచి పొగ వాసనతో, జ్యూసీగా (కారం 2/3, నట్-ఫ్రీ) ఉంటుంది."
            ),
            // 5. Biryani
            CulinaryDishInfo(
                nameEnglish = "Hyderabadi Biryani",
                aliases = listOf("biryani", "chicken biryani", "mutton biryani", "hyderabadi biryani", "dum biryani", "బిర్యానీ", "చికెన్ బిర్యానీ", "మటన్ బిర్యానీ", "హైదరాబాదీ బిర్యానీ", "దమ్ బిర్యానీ", "बिरयानी", "चिकन बिरयानी", "मटन बिरयानी", "हैदराबादी बिरयानी", "दम बिरयानी"),
                category = "Royal Mughlai / Hyderabadi Main Course",
                origin = "Hyderabadi / Royal Mughlai (Kacchi Dum Cooking)",
                flavorProfile = "Layered, deeply aromatic with saffron, mint, whole spices, rich and savory",
                dietary = "Non-Vegetarian, Nut-Free, Contains Dairy (Pure Ghee / Curd)",
                isVeg = false,
                isJain = false,
                isNutFree = true,
                hasDairy = true,
                spiceLevel = 2,
                spiceLevelStr = "Medium Spicy (2/3)",
                spokenEn = "Hyderabadi Dum Biryani is a royal culinary masterpiece of aged basmati rice and marinated meat slow-cooked in a sealed pot with saffron, mint, and aromatic whole spices. It is rich, fragrant, and medium-spicy (spice level 2/3, non-vegetarian, contains ghee).",
                spokenHi = "हैदराबादी दम बिरयानी लंबे बासमती चावल और मसालों में मैरीनेट किए गए गोश्त को धीमी आंच पर दम देकर बनाई जाती है। इसमें केसर, पुदीना और खड़े मसालों की महक होती है (तीखापन 2/3, मांसाहारी, घी युक्त)।",
                spokenTe = "హైదరాబాదీ దమ్ బిర్యానీ అనేది బాస్మతి బియ్యం, సుగంధ ద్రవ్యాలు, కుంకుమపువ్వు మరియు మసాలాతో నానబెట్టిన మాంసాన్ని దమ్ పద్ధతిలో ఉడికించే ప్రసిద్ధ రాజరిక వంటకం. ఇది అద్భుతమైన సువాసనతో, స్పైసీగా (కారం 2/3) ఉంటుంది."
            ),
            // 6. Mutton Rogan Josh
            CulinaryDishInfo(
                nameEnglish = "Mutton Rogan Josh",
                aliases = listOf("rogan josh", "mutton rogan josh", "రొగన్ జోష్", "మటన్ రోగన్ జోష్", "रोगन जोश", "मटन रोगन जोश"),
                category = "Kashmiri Non-Vegetarian Curry",
                origin = "Kashmiri Royal Cuisine",
                flavorProfile = "Warm spiced, deeply aromatic with Kashmiri red chili, fennel, and ginger",
                dietary = "Non-Vegetarian, Dairy-Free / Mild Curd, Nut-Free",
                isVeg = false,
                isJain = false,
                isNutFree = true,
                hasDairy = false,
                spiceLevel = 2,
                spiceLevelStr = "Medium Spicy (2/3)",
                spokenEn = "Mutton Rogan Josh is an aromatic Kashmiri specialty of tender slow-cooked mutton in a vibrant red gravy flavored with Kashmiri chilies, fennel, and dry ginger. It has a rich, warm spiced flavor (spice level 2/3, non-vegetarian, nut-free).",
                spokenHi = "मटन रोगन जोश कश्मीरी शाही करी है जिसमें रसीले मटन को कश्मीरी लाल मिर्च, सौंफ और सोंठ की खुशबूदार लाल ग्रेवी में पकाया जाता है। इसका स्वाद लाजवाब और मध्यम तीखा (2/3, मांसाहारी, नट-फ्री) होता है।",
                spokenTe = "మటన్ రోగన్ జోష్ అనేది కాశ్మీరీ సాంప్రదాయ వంటకం. మెత్తటి మటన్ ముక్కలను కాశ్మీరీ మిర్చి, సోంపు మరియు శొంఠి మసాలాలతో కూడిన రిచ్ రెడ్ గ్రేవీలో ఉడికిస్తారు. ఇది ఘాటైన సువాసనతో (కారం 2/3, నట్-ఫ్రీ) ఉంటుంది."
            ),
            // 7. Fish Curry
            CulinaryDishInfo(
                nameEnglish = "Fish Curry",
                aliases = listOf("fish curry", "meen kulambu", "fish masala", "andhra fish curry", "చేపల పులుసు", "చేపల కూర", "ఫిష్ కర్రీ", "फिश करी", "मछली करी"),
                category = "Coastal Non-Vegetarian Seafood",
                origin = "Coastal South Indian (Andhra Chepala Pulusu / Kerala Meen Kulambu)",
                flavorProfile = "Tangy, spicy, savory with tamarind, curry leaves, and mustard",
                dietary = "Non-Vegetarian (Seafood), Dairy-Free, Nut-Free",
                isVeg = false,
                isJain = false,
                isNutFree = true,
                hasDairy = false,
                spiceLevel = 2,
                spiceLevelStr = "Spicy (2/3)",
                spokenEn = "Fish Curry is a coastal Indian delicacy prepared by simmering fresh fish in a tangy tamarind, tomato, or coconut gravy tempered with curry leaves and mustard seeds. It offers a tangy, savory, and spicy flavor (spice level 2/3, dairy-free, nut-free).",
                spokenHi = "फिश करी एक तटीय भारतीय व्यंजन है जिसमें ताजी मछली को इमली, टमाटर और कढ़ी पत्ते के तड़के वाली तीखी-खट्टी ग्रेवी में पकाया जाता है। यह डेयरी-फ्री और नट-फ्री (तीखापन 2/3) होती है।",
                spokenTe = "చేపల పులుసు అనేది చింతపండు, టమోటా, పచ్చిమిర్చి మరియు కరివేపాకు పోపుతో తాజా చేపలను ఉడికించి చేసే రుచికరమైన తీరప్రాంత వంటకం. ఇది పుల్లగా, కారంగా (కారం 2/3, డైరీ-ఫ్రీ, నట్-ఫ్రీ) ఎంతో రుచిగా ఉంటుంది."
            ),
            // 8. Prawns Fry
            CulinaryDishInfo(
                nameEnglish = "Prawns Fry",
                aliases = listOf("prawns fry", "prawn fry", "royyala vepudu", "prawn masala", "రొయ్యల వేపుడు", "ప్రాన్స్ ఫ్రై", "झींगा फ्राई", "प्रॉन्स फ्राई"),
                category = "Coastal Non-Vegetarian Seafood Starter",
                origin = "Coastal South Indian / Andhra (Royyala Vepudu)",
                flavorProfile = "Crispy, peppery, roasted, savory with caramelized onions and curry leaves",
                dietary = "Non-Vegetarian (Shellfish), Dairy-Free, Nut-Free",
                isVeg = false,
                isJain = false,
                isNutFree = true,
                hasDairy = false,
                spiceLevel = 2,
                spiceLevelStr = "Spicy (2/3)",
                spokenEn = "Prawns Fry (Royyala Vepudu) is a spicy coastal specialty of succulent prawns tossed with caramelized onions, curry leaves, ginger-garlic, and black pepper. It is crispy, spicy (spice level 2/3), dairy-free, and nut-free.",
                spokenHi = "प्रॉन्स फ्राई (झींगा फ्राई) तटीय मसालेदार डिश है जिसमें झींगों को प्याज, कढ़ी पत्ता, अदरक-लहसुन और काली मिर्च के साथ भूना जाता है। यह कुरकुरा और चटपटा (तीखापन 2/3, डेयरी-फ्री, नट-फ्री) होता है।",
                spokenTe = "రొయ్యల వేపుడు అనేది ఉల్లిపాయలు, అల్లం-వెల్లుల్లి, కరివేపాకు మరియు మిరియాల పొడితో రొయ్యలను వేయించే ప్రసిద్ధ ఆంధ్రా వంటకం. ఇది మంచి ఘాటుతో, క్రంచీగా (కారం 2/3, డైరీ-ఫ్రీ, నట్-ఫ్రీ) ఉంటుంది."
            ),
            // 9. Egg Curry
            CulinaryDishInfo(
                nameEnglish = "Egg Curry",
                aliases = listOf("egg curry", "anda curry", "kodi guddu kura", "గుడ్డు కూర", "ఎగ్ కర్రీ", "अंडा करी", "एग करी"),
                category = "Homestyle Non-Vegetarian Curry",
                origin = "All-India Homestyle Cuisine",
                flavorProfile = "Savory, comforting, balanced garam masala and tomato warmth",
                dietary = "Non-Vegetarian (Eggetarian), Dairy-Free, Nut-Free",
                isVeg = false,
                isJain = false,
                isNutFree = true,
                hasDairy = false,
                spiceLevel = 1,
                spiceLevelStr = "Medium (1/3)",
                spokenEn = "Egg Curry is a comfort dish of hard-boiled pan-seared eggs simmered in a homestyle spiced onion and tomato gravy. It has a balanced savory flavor (spice level 1/3, eggetarian, dairy-free, nut-free).",
                spokenHi = "अंडा करी एक लोकप्रिय डिश है जिसमें उबले अंडों को प्याज, टमाटर और गरम मसाले की स्वादिष्ट ग्रेवी में पकाया जाता है। यह मध्यम तीखी (1/3), डेयरी-फ्री और नट-फ्री होती है।",
                spokenTe = "గుడ్డు కూర (ఎగ్ కర్రీ) అనేది వేయించిన ఉడికించిన గుడ్లను ఉల్లిపాయ, టమోటా మరియు గరం మసాలా గ్రేవీలో వండే రుచికరమైన వంటకం. ఇది మితమైన కారంతో (1/3) డైరీ-ఫ్రీ, నట్-ఫ్రీగా ఉంటుంది."
            ),
            // 10. Tandoori Chicken
            CulinaryDishInfo(
                nameEnglish = "Tandoori Chicken",
                aliases = listOf("tandoori chicken", "తందూరి చికెన్", "तंदूरी चिकन"),
                category = "Classic Tandoori Non-Vegetarian",
                origin = "Punjabi / Peshawar (Clay Oven Tandoor)",
                flavorProfile = "Smoky, charred, juicy, mildly spicy, tangy with lemon and chaat masala",
                dietary = "Non-Vegetarian, Nut-Free, Contains Dairy (Yogurt Marinade)",
                isVeg = false,
                isJain = false,
                isNutFree = true,
                hasDairy = true,
                spiceLevel = 2,
                spiceLevelStr = "Medium Spicy (2/3)",
                spokenEn = "Tandoori Chicken is an iconic Indian dish of bone-in chicken marinated in spiced yogurt, Kashmiri chilies, and roasted in a high-temperature clay oven. It features a smoky, charred, and juicy flavor (spice level 2/3, non-veg, nut-free, contains yogurt).",
                spokenHi = "तंदूरी चिकन विश्व प्रसिद्ध उत्तर भारतीय व्यंजन है। इसमें चिकन को गाढ़े दही, कश्मीरी मिर्च और मसालों में मैरीनेट करके तंदूर में भुना जाता है। इसका स्वाद स्मोकी और रसीला (तीखापन 2/3, मांसाहारी, नट-फ्री) होता है।",
                spokenTe = "తందూరి చికెన్ అనేది చికెన్‌ను పెరుగు, కాశ్మీరీ మిర్చి మరియు మసాలాలతో మెరినేట్ చేసి క్లే తందూర్‌లో కాల్చే ప్రసిద్ధ వంటకం. ఇది స్మోకీ ఫ్లేవర్ తో, జ్యూసీగా (కారం 2/3, నట్-ఫ్రీ) ఉంటుంది."
            ),
            // 11. Masala Dosa
            CulinaryDishInfo(
                nameEnglish = "Masala Dosa",
                aliases = listOf("masala dosa", "dosa", "మసాలా దోశ", "దోశ", "మసాలా దోస", "मसाला डोसा", "डोसा"),
                category = "South Indian Vegetarian Tiffin",
                origin = "South Indian (Udupi / Karnataka / Tamil Nadu)",
                flavorProfile = "Crispy golden exterior, comforting spiced potato mash, tangy sambar accompaniment",
                dietary = "100% Vegetarian, Nut-Free, Gluten-Free, Vegan (if made with oil), NOT Jain (contains potato & onion)",
                isVeg = true,
                isJain = false,
                isNutFree = true,
                hasDairy = false,
                spiceLevel = 1,
                spiceLevelStr = "Mild (1/3)",
                spokenEn = "Masala Dosa is an iconic South Indian golden-crispy fermented rice and lentil crepe stuffed with a mildly spiced potato and onion filling. It is 100% vegetarian, nut-free, gluten-free, and served with hot sambar and coconut chutney (spice level 1/3).",
                spokenHi = "मसाला डोसा दक्षिण भारत का प्रसिद्ध व्यंजन है। यह चावल और उड़द दाल के घोल से बना कुरकुरा क्रेप है, जिसमें उबले आलू और प्याज का चटपटा मसाला भरा होता है। यह 100% शाकाहारी और नट-फ्री (तीखापन 1/3) है।",
                spokenTe = "మసాలా దోశ దక్షిణ భారతదేశపు ప్రసిద్ధ అల్పాహారం. బియ్యం మరియు మినపప్పు పిండితో వేసిన క్రిస్పీ దోశలో బంగాళాదుంప, ఉల్లిపాయ మసాలా కూరను ఉంచుతారు. ఇది 100% శాకాహార, నట్-ఫ్రీ వంటకం (కారం 1/3)."
            ),
            // 12. Paneer Butter Masala
            CulinaryDishInfo(
                nameEnglish = "Paneer Butter Masala",
                aliases = listOf("paneer butter masala", "paneer makhani", "పనీర్ బటర్ మసాలా", "పనీర్ మఖని", "पनीर बटर मसाला", "पनीर मखनी"),
                category = "North Indian Vegetarian Main Course",
                origin = "North Indian / Punjabi",
                flavorProfile = "Silky, creamy, mildly sweet, rich tomato and butter sauce",
                dietary = "100% Vegetarian, Contains Dairy (Paneer, Butter, Cream), Contains Tree Nuts (Cashews)",
                isVeg = true,
                isJain = false,
                isNutFree = false,
                hasDairy = true,
                spiceLevel = 1,
                spiceLevelStr = "Mild (1/3)",
                spokenEn = "Paneer Butter Masala is a beloved North Indian vegetarian dish featuring soft cottage cheese cubes simmered in a luscious tomato, butter, fresh cream, and cashew nut gravy. It has a rich, mildly sweet and savory flavor (spice level 1/3, contains dairy and cashews).",
                spokenHi = "पनीर बटर मसाला एक लोकप्रिय उत्तर भारतीय शाकाहारी व्यंजन है। इसमें ताजे पनीर के टुकड़ों को टमाटर, मक्खन, मलाई और काजू की मखमली ग्रेवी में पकाया जाता है (इसमें डेयरी और काजू हैं, तीखापन 1/3)।",
                spokenTe = "పనీర్ బటర్ మసాలా అనేది మెత్తని పనీర్ ముక్కలను టమోటా, వెన్న, ఫ్రెష్ క్రీమ్ మరియు జీడిపప్పు పేస్ట్‌తో కూడిన క్రీమీ గ్రేవీలో వండే రుచికరమైన శాకాహార వంటకం (పాల ఉత్పత్తులు మరియు జీడిపప్పు ఉంటాయి, కారం 1/3)."
            ),
            // 13. Dal Makhani
            CulinaryDishInfo(
                nameEnglish = "Dal Makhani",
                aliases = listOf("dal makhani", "మఖని దాల్", "దాల్ మఖని", "दाल मखनी"),
                category = "Punjabi Vegetarian Main Course",
                origin = "Punjabi (Peshawar / Delhi)",
                flavorProfile = "Creamy, earthy, slow-cooked buttery black lentils and kidney beans",
                dietary = "100% Vegetarian, Nut-Free, Contains Dairy (Butter, Cream)",
                isVeg = true,
                isJain = false,
                isNutFree = true,
                hasDairy = true,
                spiceLevel = 1,
                spiceLevelStr = "Mild (1/3)",
                spokenEn = "Dal Makhani is a signature Punjabi slow-cooked black lentil and kidney bean curry simmered overnight with pure butter, fresh cream, and aromatic spices. It has a rich, earthy, and creamy taste (spice level 1/3, vegetarian, nut-free, contains dairy).",
                spokenHi = "दाल मखनी एक प्रसिद्ध पंजाबी व्यंजन है जिसमें साबुत उड़द दाल और राजमा को रात भर धीमी आंच पर मक्खन, ताजी मलाई और मसालों के साथ पकाया जाता है (शाकाहारी, नट-फ्री, डेयरी युक्त, तीखापन 1/3)।",
                spokenTe = "దాల్ మఖని అనేది నల్ల మినుములు మరియు రాజ్మాను రాత్రంతా నెమ్మదిగా ఉడికించి, వెన్న, ఫ్రెష్ క్రీమ్ మరియు మసాలాలతో తయారు చేసే ప్రసిద్ధ పంజాబీ వంటకం (శాకాహార, నట్-ఫ్రీ, పాల ఉత్పత్తులు ఉంటాయి, కారం 1/3)."
            ),
            // 14. Chole Bhature
            CulinaryDishInfo(
                nameEnglish = "Chole Bhature",
                aliases = listOf("chole bhature", "chana bhatura", "చోలే భటూరే", "ఛోలే భటూరా", "छोले भटूरे", "छोले भटूरा"),
                category = "North Indian Vegetarian Specialty",
                origin = "North Indian / Punjabi / Delhi",
                flavorProfile = "Tangy, robust, spicy chickpea gravy paired with giant fluffy fried leavened bread",
                dietary = "100% Vegetarian, Nut-Free, Dairy-Free, Contains Wheat Gluten",
                isVeg = true,
                isJain = false,
                isNutFree = true,
                hasDairy = false,
                spiceLevel = 2,
                spiceLevelStr = "Spicy (2/3)",
                spokenEn = "Chole Bhature is a famous North Indian specialty of spicy, tangy chickpea curry paired with giant puffy deep-fried bread. It has a robust, tangy flavor (spice level 2/3, 100% vegetarian, nut-free, contains wheat gluten).",
                spokenHi = "छोले भटूरे उत्तर भारत का बेहद लोकप्रिय व्यंजन है। इसमें चटपटे मसालेदार काबुली चने और गरमा-गरम फूले हुए भटूरे परोसे जाते हैं (100% शाकाहारी, नट-फ्री, तीखापन 2/3)।",
                spokenTe = "చోలే భటూరే అనేది మసాలా కాబూలీ శనగల కూర మరియు వేడి వేడి పూరీ లాంటి పెద్ద భటూరాతో కూడిన ప్రసిద్ధ ఉత్తర భారత వంటకం. ఇది మంచి ఘాటుతో, పుల్లగా (కారం 2/3, 100% శాకాహారం, నట్-ఫ్రీ) ఉంటుంది."
            ),
            // 15. Idli Sambar
            CulinaryDishInfo(
                nameEnglish = "Idli Sambar",
                aliases = listOf("idli sambar", "idli", "ఇడ్లీ సాంబార్", "ఇడ్లీ", "इडली सांभर", "इडली"),
                category = "South Indian Vegetarian Tiffin",
                origin = "Traditional South Indian (Tamil Nadu / Karnataka)",
                flavorProfile = "Pillowy, mild, tangy lentil stew with mustard and curry leaves tempering",
                dietary = "100% Vegetarian, 100% Vegan, Jain-Adaptable, Nut-Free, Gluten-Free, Zero Oil",
                isVeg = true,
                isJain = true,
                isNutFree = true,
                hasDairy = false,
                spiceLevel = 1,
                spiceLevelStr = "Mild (1/3)",
                spokenEn = "Idli Sambar is a traditional South Indian breakfast of steamed fluffy fermented rice-and-lentil cakes served with aromatic vegetable lentil stew and coconut chutney. It is 100% vegetarian, vegan, nut-free, and easily digestible (spice level 1/3).",
                spokenHi = "इडली सांभर दक्षिण भारत का पौष्टिक और सुपाच्य नाश्ता है। भाप में पकी हुई स्पंजी इडली को गरमा-गरम सब्जियों वाले सांभर और नारियल चटनी के साथ परोसा जाता है (100% शाकाहारी, वीगन, नट-फ्री, तीखापन 1/3)।",
                spokenTe = "ఇడ్లీ సాంబార్ సంప్రదాయ దక్షిణ భారత ఆరోగ్యకరమైన అల్పాహారం. ఆవిరిపై ఉడికించిన మెత్తని ఇడ్లీలను వేడి సాంబార్ మరియు కొబ్బరి చట్నీతో తింటారు. ఇది 100% శాకాహార, వీగన్, నట్-ఫ్రీ వంటకం (కారం 1/3)."
            ),
            // 16. Hakka Noodles
            CulinaryDishInfo(
                nameEnglish = "Hakka Noodles",
                aliases = listOf("hakka noodles", "veg noodles", "noodles", "హక్కా నూడుల్స్", "నూడుల్స్", "हक्का नूडल्स", "नूडल्स"),
                category = "Indo-Chinese Vegetarian Main",
                origin = "Indo-Chinese (Kolkata Tangra Chinese community)",
                flavorProfile = "Savory, umami, crunchy vegetables with wok-tossed aroma",
                dietary = "100% Vegetarian, 100% Vegan, Dairy-Free, Nut-Free, Contains Soy & Wheat Gluten",
                isVeg = true,
                isJain = false,
                isNutFree = true,
                hasDairy = false,
                spiceLevel = 1,
                spiceLevelStr = "Mild (1/3)",
                spokenEn = "Hakka Noodles is a popular Indo-Chinese street dish of wok-tossed noodles with crunchy carrots, cabbage, capsicum, soy sauce, and white pepper. It has a savory, smoky umami flavor (spice level 1/3, 100% vegetarian, dairy-free, nut-free, contains soy and gluten).",
                spokenHi = "हक्का नूडल्स एक लोकप्रिय इंडो-चाइनीज डिश है जिसमें नूडल्स को गाजर, पत्तागोभी, शिमला मिर्च और सोया सॉस के साथ तेज आंच पर भूना जाता है। यह स्वादिष्ट और कम तीखी (1/3, शाकाहारी, डेयरी-फ्री, नट-फ्री) होती है।",
                spokenTe = "హక్కా నూడుల్స్ అనేది ఇండో-చైనీస్ శైలి వంటకం. నూడుల్స్‌ను క్యాబేజీ, క్యారెట్, క్యాప్సికం, వెల్లుల్లి మరియు సోయా సాస్‌తో అధిక మంటపై వేయిస్తారు. ఇది మంచి రుచితో (కారం 1/3) శాకాహార, డైరీ-ఫ్రీగా ఉంటుంది."
            ),
            // 17. Manchurian
            CulinaryDishInfo(
                nameEnglish = "Manchurian",
                aliases = listOf("manchurian", "gobi manchurian", "veg manchurian", "chicken manchurian", "మంచూరియా", "గోబీ మంచూరియా", "వెజ్ మంచూరియా", "మంచూరియన్", "मंचूरियन", "गोभी मंचूरियन", "वेज मंचूरियन"),
                category = "Indo-Chinese Specialty",
                origin = "Indo-Chinese (Created in Mumbai)",
                flavorProfile = "Tangy, savory, sweet-spicy, garlicky, and crunchy dumplings",
                dietary = "Nut-Free, Dairy-Free, Contains Soy & Gluten (Vegetarian or Non-Veg depending on vegetable or chicken base)",
                isVeg = true,
                isJain = false,
                isNutFree = true,
                hasDairy = false,
                spiceLevel = 2,
                spiceLevelStr = "Spicy (2/3)",
                spokenEn = "Manchurian is an Indo-Chinese favorite of crispy vegetable or chicken dumplings tossed in a savory, tangy garlic-soy sauce with spring onions. It has a tangy, spicy, and garlicky flavor (spice level 2/3, nut-free, dairy-free, contains soy).",
                spokenHi = "मंचूरियन एक लोकप्रिय इंडो-चाइनीज व्यंजन है। इसमें कुरकुरे वेज या चिकन बॉल्स को लहसुन, अदरक और सोया सॉस की चटपटी ग्रेवी में मिलाया जाता है (तीखापन 2/3, नट-फ्री, डेयरी-फ्री)।",
                spokenTe = "మంచూరియన్ అనేది ఇండో-చైనీస్ వంటకం. క్రిస్పీగా వేయించిన వెజిటబుల్ లేదా చికెన్ బాల్స్‌ను వెల్లుల్లి, అల్లం, పచ్చిమిర్చి మరియు సోయా సాస్‌తో టాస్ చేస్తారు. ఇది పుల్లగా, కారంగా (కారం 2/3, నట్-ఫ్రీ) ఉంటుంది."
            ),
            // 18. Gulab Jamun
            CulinaryDishInfo(
                nameEnglish = "Gulab Jamun",
                aliases = listOf("gulab jamun", "jamun", "గులాబ్ జామున్", "జామున్", "गुलाब जामुन", "जामुन"),
                category = "Traditional Indian Sweet / Dessert",
                origin = "Indian Subcontinent Royal Confection",
                flavorProfile = "Melt-in-the-mouth, sweet, floral with warm cardamom and rosewater syrup",
                dietary = "100% Vegetarian, Contains Dairy (Khoya / Milk Solids), Contains Wheat Gluten",
                isVeg = true,
                isJain = true,
                isNutFree = true,
                hasDairy = true,
                spiceLevel = 0,
                spiceLevelStr = "Sweet (0/3)",
                spokenEn = "Gulab Jamun is a cherished Indian dessert made of soft, golden-fried milk-solid dumplings soaked in warm rose and cardamom flavored sugar syrup. It is sweet, rich, and melts in your mouth (100% vegetarian, contains dairy and gluten).",
                spokenHi = "गुलाब जामुन भारत की सबसे लोकप्रिय मिठाई है। मावा (खोया) के बने गोलों को तलकर इलायची और गुलाब जल की चाशनी में डुबोया जाता है। यह बेहद मीठी और नर्म (100% शाकाहारी, डेयरी युक्त) होती है।",
                spokenTe = "గులాబ్ జామున్ భారతీయ సాంప్రదాయ ప్రసిద్ధ మిఠాయి. కోవాతో చేసిన మెత్తని ఉండలను నేతిలో వేయించి, యాలకుల సువాసన గల చక్కెర పాకంలో నానబెడతారు. ఇది తియ్యగా, ఎంతో మృదువుగా (100% శాకాహారం, పాల ఉత్పత్తులు ఉంటాయి) ఉంటుంది."
            ),
            // 19. Filter Coffee
            CulinaryDishInfo(
                nameEnglish = "Filter Coffee",
                aliases = listOf("filter coffee", "coffee", "kaapi", "ఫిల్టర్ కాఫీ", "కాఫీ", "फ़िल्टर कॉफ़ी", "कॉफ़ी", "कॉफी"),
                category = "South Indian Beverage",
                origin = "South Indian (Tamil Nadu / Karnataka / Andhra)",
                flavorProfile = "Strong, velvety, bittersweet, aromatic with rich froth in a davarah-tumbler",
                dietary = "100% Vegetarian Beverage, Nut-Free, Gluten-Free, Contains Dairy (Milk)",
                isVeg = true,
                isJain = true,
                isNutFree = true,
                hasDairy = true,
                spiceLevel = 0,
                spiceLevelStr = "Beverage (0/3)",
                spokenEn = "South Indian Filter Coffee is an authentic brew made by dripping dark-roasted coffee with chicory, blended with frothy hot milk and sugar in a traditional davarah-tumbler. It is rich, aromatic, and invigorating (vegetarian, contains milk dairy).",
                spokenHi = "साउथ इंडियन फ़िल्टर कॉफ़ी एक पारंपरिक पेय है जिसे ताजी पिसी कॉफ़ी के डिकॉक्शन और झागदार गरम दूध के साथ बनाया जाता है। यह बेहद खुशबूदार और ताजगी देने वाली (शाकाहारी, डेयरी युक्त) होती है।",
                spokenTe = "దక్షిణ భారత ఫిల్టర్ కాఫీ అనేది చిక్కని డికాక్షన్‌ను వేడి నురుగు పాలతో కలిపి సంప్రదాయ దబరా-టంబ్లర్‌లో అందించే అద్భుతమైన పానీయం. ఇది మంచి సువాసనతో, రుచిగా (శాకాహార, పాల ఉత్పత్తులు ఉంటాయి) ఉంటుంది."
            ),
            // 20. Lassi
            CulinaryDishInfo(
                nameEnglish = "Lassi",
                aliases = listOf("lassi", "sweet lassi", "mango lassi", "లస్సీ", "స్వీట్ లస్సీ", "మాంగో లస్సీ", "लस्सी", "मीठी लस्सी", "मैंगो लस्सी"),
                category = "Punjabi Beverage",
                origin = "Punjabi / North Indian",
                flavorProfile = "Creamy, cooling, sweet or salted, refreshing with crushed cardamom",
                dietary = "100% Vegetarian Beverage, Contains Dairy (Yogurt / Curd), Nut-Free (unless nut-garnished)",
                isVeg = true,
                isJain = true,
                isNutFree = true,
                hasDairy = true,
                spiceLevel = 0,
                spiceLevelStr = "Cooling Beverage (0/3)",
                spokenEn = "Lassi is a refreshing, traditional Punjabi yogurt-based chilled beverage churned with sugar, cardamom, or mango pulp. It is creamy, cooling, and rich (100% vegetarian, contains dairy yogurt).",
                spokenHi = "लस्सी पंजाब का मशहूर ठंडा पेय है जिसे ताजे गाढ़े दही, चीनी, इलायची या आम के रस के साथ मथकर बनाया जाता है। यह मलाईदार और ताजगी देने वाली (100% शाकाहारी, डेयरी युक्त) होती है।",
                spokenTe = "లస్సీ అనేది చల్లని చిక్కటి పెరుగు, చక్కెర మరియు యాలకుల పొడితో చిలికి తయారు చేసే ప్రసిద్ధ పంజాబీ పానీయం. ఇది శరీరాన్ని చల్లబరిచే రుచికరమైన (100% శాకాహార, పాల ఉత్పత్తులు ఉంటాయి) డ్రింక్."
            ),
            // 21. Rasam
            CulinaryDishInfo(
                nameEnglish = "Rasam",
                aliases = listOf("rasam", "charu", "saaru", "tomato rasam", "pepper rasam", "రసం", "చారు", "టమోటా రసం", "మిరియాల రసం", "रसम", "टमाटर रसम", "सांभर रसम"),
                category = "South Indian Traditional Soup",
                origin = "Traditional South Indian (Tamil Nadu / Andhra / Karnataka)",
                flavorProfile = "Tangy, peppery, soothing, light, digestive with crushed cumin, black pepper, and curry leaves",
                dietary = "100% Vegetarian, 100% Vegan, Dairy-Free, Nut-Free, Gluten-Free",
                isVeg = true,
                isJain = true,
                isNutFree = true,
                hasDairy = false,
                spiceLevel = 2,
                spiceLevelStr = "Peppery Spicy (2/3)",
                spokenEn = "Rasam is a traditional South Indian light and tangy spiced soup made with tamarind, fresh tomatoes, black pepper, cumin, and curry leaves. It is peppery, digestive, and invigorating (spice level 2/3, 100% vegan, dairy-free, nut-free).",
                spokenHi = "रसम दक्षिण भारत का पारंपरिक तीखा-खट्टा सूप है जो इमली, टमाटर, काली मिर्च, जीरा और कढ़ी पत्ते से बनता है। यह बेहद पाचक और स्वादिष्ट (तीखापन 2/3, वीगन, डेयरी-फ्री, नट-फ्री) होता है।",
                spokenTe = "రసం (చారు) అనేది చింతపండు రసం, టమోటా, మిరియాలు, జీలకర్ర మరియు కరివేపాకు పోపుతో కాచే దక్షిణ భారత సాంప్రదాయ సూప్. ఇది ఘాటైన మిరియాల రుచితో, జీర్ణక్రియకు ఎంతో మంచిది (కారం 2/3, వీగన్, డైరీ-ఫ్రీ, నట్-ఫ్రీ)."
            )
        )

        fun findCulinaryDish(query: String): CulinaryDishInfo? {
            val qLower = query.lowercase().trim()
            if (qLower.isEmpty()) return null
            for (dish in CULINARY_ENCYCLOPEDIA) {
                if (qLower.contains(dish.nameEnglish.lowercase())) return dish
                if (dish.aliases.any { alias -> qLower.contains(alias.lowercase()) || query.contains(alias) }) return dish
            }
            return null
        }

        fun isNonFoodQuery(query: String): Boolean {
            val s = query.trim()
            val lower = s.lowercase()
            if (s.isEmpty()) return false

            val words = lower.split(Regex("[^a-zA-Z0-9\u0C00-\u0C7F\u0900-\u097F]+")).filter { it.isNotBlank() }.toSet()

            // Non-food indicators: Coding, Math calculations, Politics, General trivia, Weather, Stocks
            val nonFoodPatterns = listOf(
                "python", "javascript", "kotlin", "java", "c++", "html", "css", "sql", "git", "github",
                "write a function", "write code", "algorithm", "debug", "compile", "software", "variable",
                "array", "sorting", "loop", "recursion", "class", "object-oriented", "database", "script",
                "కోడింగ్", "ప్రోగ్రామింగ్", "సాఫ్ట్‌వేర్", "పైథాన్", "కోడ్", "రాయండి",
                "कोड", "प्रोग्रामिंग", "सॉफ्टवेयर", "पायथन", "फंक्शन", "एल्गोरिदम", "लिखो",
                "square root", "derivative", "integral", "algebra", "calculus", "equation", "solve",
                "2 + 2", "physics", "quantum", "gravity", "speed of light", "formula",
                "లెక్కలు", "గణితం", "సమీకరణం", "गणित", "समीकरण",
                "president", "prime minister", "election", "politics", "political", "parliament",
                "bjp", "congress party", "democrat", "republican", "modi", "biden", "trump", "putin",
                "రాజకీయాలు", "ఎన్నికలు", "ప్రధాన మంత్రి", "రాష్ట్రపతి", "ప్రెసిడెంట్",
                "राजनीति", "चुनाव", "प्रधानमंत्री", "राष्ट्रपति", "संसद",
                "messi", "ronaldo", "virat kohli", "who won the match", "cricket score", "ipl match",
                "weather", "forecast", "crypto", "bitcoin", "ethereum", "stock market",
                "share price", "actor", "actress", "cinema", "movie ticket", "capital of", "who is the ceo",
                "వాతావరణం", "సినిమా", "క్రికెట్",
                "मौसम", "शेयर बाजार", "फिल्म", "क्रिकेट"
            )

            val matchesNonFood = nonFoodPatterns.any { pattern ->
                if (pattern.length <= 4) words.contains(pattern)
                else lower.contains(pattern) || s.contains(pattern)
            }

            val multiWordFoodTerms = listOf(
                "main course", "non-veg", "add to cart", "clear cart", "how much", "chicken 65", "butter chicken",
                "paneer butter masala", "masala dosa", "dal makhani", "chole bhature", "idli sambar", "gulab jamun",
                "filter coffee", "hakka noodles", "mutton rogan josh", "fish curry", "prawns fry", "egg curry"
            )

            val singleWordFoodTerms = setOf(
                "food", "dish", "dishes", "menu", "order", "eat", "drink", "starter", "starters",
                "dessert", "desserts", "beverage", "beverages", "bill", "split", "table", "cart", "waiter",
                "restaurant", "recipe", "ingredient", "ingredients", "tiffin", "tiffins", "breakfast", "lunch",
                "dinner", "snack", "snacks", "sweet", "sweets", "spicy", "veg", "vegetarian", "jain", "vegan",
                "nut", "nuts", "cashew", "kaju", "dairy", "butter", "paneer", "chicken", "mutton", "fish",
                "prawn", "prawns", "egg", "eggs", "biryani", "dosa", "idli", "vada", "sambar", "dal", "curry",
                "roti", "naan", "coffee", "tea", "chai", "lassi", "rice", "noodle", "noodles", "manchurian",
                "chilli", "tikka", "kebab", "rasam", "chole", "bhature", "sugar", "salt", "halwa", "soup",
                "taste", "tasty", "cook", "cooked", "serve", "served", "portion", "price", "rupees", "paise", "cost"
            )

            val indicFoodTerms = listOf(
                "ఆహారం", "వంటకం", "వంటకాలు", "మెనూ", "ఆర్డర్", "శాకాహార", "మాంసాహార", "దోశ", "ఇడ్లీ", "వడ",
                "సాంబార్", "బిర్యానీ", "చికెన్", "మటన్", "చేప", "రొయ్యలు", "గుడ్డు", "పనీర్",
                "కాఫీ", "టీ", "రసం", "గులాబ్ జామున్", "కారం", "తీపి", "ఉప్పు", "రుచి", "భోజనం", "తిండి",
                "खाना", "व्यंजन", "मेन्यू", "ऑर्डर", "बिल", "कार्ट", "वेटर", "रेस्तरां",
                "शाकाहारी", "मांसाहारी", "डोसा", "इडली", "सांभर", "बिरयानी", "चिकन", "मटन", "मछली",
                "अंडा", "पनीर", "कॉफ़ी", "चाय", "लस्सी", "गुलाब जामुन", "तीखा", "मीठा", "नमक", "स्वाद"
            )

            val hasFoodTerm = multiWordFoodTerms.any { lower.contains(it) } ||
                    words.any { it in singleWordFoodTerms } ||
                    indicFoodTerms.any { s.contains(it) }

            return matchesNonFood && !hasFoodTerm
        }

        fun generateGuardrailResponse(language: String): StructuredAiResponse {
            val lang = language.lowercase()
            val isTelugu = lang == "te"
            val isHindi = lang == "hi"

            val spoken = when {
                isTelugu -> REDIRECTION_TE
                isHindi -> REDIRECTION_HI
                else -> REDIRECTION_EN
            }

            return StructuredAiResponse(
                intent = "GENERAL_QUERY",
                headline = "Svara Restaurant Assistant",
                spoken = spoken,
                caption = spoken,
                bullets = listOf(
                    "Food & Dining Inquiries Only",
                    "Supported: Dishes, Ingredients, Recipes, Menu & Orders"
                ),
                actions = listOf(
                    UiAction(
                        id = "act_guardrail_browse",
                        label = "Browse Menu",
                        actionType = "NAVIGATE_SECTION",
                        payload = ""
                    )
                )
            )
        }
    }

    suspend fun processSpeechIntent(
        userSpeech: String,
        userLanguage: String,
        menuContext: String = ""
    ): Result<StructuredAiResponse> = withContext(Dispatchers.IO) {
        val lang = userLanguage.lowercase()

        // Guardrail check for out-of-scope queries
        if (isNonFoodQuery(userSpeech)) {
            Log.i(TAG, "Guardrail activated: Non-food query detected in speech intent")
            return@withContext Result.Success(generateGuardrailResponse(lang))
        }

        val apiKey = if (context != null) ApiKeyManager.getGroqApiKey(context) else BuildConfig.GROQ_API_KEY.trim()

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
        val lang = userLanguage.lowercase()

        // Guardrail check for out-of-scope queries
        if (isNonFoodQuery(userQuery)) {
            Log.i(TAG, "Guardrail activated: Non-food query detected in dietary QA")
            return@withContext Result.Success(generateGuardrailResponse(lang))
        }

        // Fetch live food & recipe web knowledge in parallel / context augmentation for dish inquiries
        val culinaryKnowledgeDeferred = async {
            try {
                culinaryWebSearchClient.searchFoodKnowledge(userQuery)
            } catch (e: Exception) {
                Log.w(TAG, "Culinary web search error: ${e.message}")
                null
            }
        }

        val apiKey = if (context != null) ApiKeyManager.getGroqApiKey(context) else BuildConfig.GROQ_API_KEY.trim()

        if (apiKey.isEmpty() || apiKey.startsWith("gsk_your")) {
            Log.i(TAG, "No active GROQ_API_KEY found, using intelligent local dietary reasoner")
            val webKnowledge = culinaryKnowledgeDeferred.await()
            return@withContext Result.Success(
                generateLocalDietaryResponse(
                    userQuery = userQuery,
                    language = lang,
                    restaurantName = restaurantName,
                    menuItems = menuItems,
                    sections = sections,
                    webKnowledge = webKnowledge
                )
            )
        }

        val culinaryKnowledge = culinaryKnowledgeDeferred.await()
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

        val knowledgeBlock = if (!culinaryKnowledge.isNullOrBlank()) {
            "\nLive Culinary & Recipe Web Knowledge:\n$culinaryKnowledge\n"
        } else {
            ""
        }

        val prompt = """
Language: $lang (te=Telugu, hi=Hindi, en=English)
Restaurant: $restaurantName
User Query: $userQuery
$knowledgeBlock
$menuBuilder
""".trimIndent()

        val requestPayload = GroqChatRequest(
            model = MODEL_LLAMA_70B,
            messages = listOf(
                GroqMessage(role = "system", content = CULINARY_SYSTEM_PROMPT.trimIndent()),
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
                        sections = sections,
                        webKnowledge = culinaryKnowledge
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
                    sections = sections,
                    webKnowledge = culinaryKnowledge
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

        // Local guardrail check for non-food queries
        if (isNonFoodQuery(s)) {
            return generateGuardrailResponse(if (isTelugu) "te" else if (isHindi) "hi" else language)
        }

        val isRemoveItem = lower.contains("remove") || lower.contains("delete") || lower.contains("don't want") ||
                s.contains("తీసేయండి") || s.contains("వద్దు") || s.contains("తీసివేయండి") ||
                s.contains("हटाओ") || s.contains("निकालो") || s.contains("नहीं चाहिए")

        val isClearCart = lower.contains("clear cart") || lower.contains("empty cart") || lower.contains("cancel all") ||
                s.contains("క్లియర్ చేయండి") || s.contains("మొత్తం తీసేయండి") || s.contains("ఖాళీ చేయండి") ||
                s.contains("सब हटाओ") || s.contains("खाली करो") || s.contains("रद्द करो")

        val isCustomization = (lower.contains("spic") || lower.contains("sugar") || lower.contains("less") || lower.contains("extra") ||
                s.contains("కారం") || s.contains("చక్కెర") || s.contains("తక్కువ") || s.contains("ఎక్కువ") ||
                s.contains("तीखा") || s.contains("कम") || s.contains("ज्यादा") || s.contains("चीनी")) &&
                !lower.startsWith("what is") && !lower.startsWith("what's") && !lower.startsWith("tell me") &&
                !s.contains("అంటే ఏమిటి") && !s.contains("గురించి") && !s.contains("क्या है")

        val isSplitQuery = lower.contains("split") || lower.contains("divide") ||
                s.contains("విభజించండి") || s.contains("పంచండి") || s.contains("మందికి") ||
                s.contains("బాంటో") || s.contains("బాంట") || s.contains("బాंटो") || s.contains("बांट") || s.contains("विभाजित") || s.contains("लोगों में")

        val isConfirmation = lower.contains("confirm") || lower.contains("place order") || lower.contains("that's all") || lower.contains("checkout") ||
                s.contains("ఆర్డర్ చేయండి") || s.contains("చాలు") || s.contains("ఖరారు") ||
                s.contains("ऑर्डर करो") || s.contains("कन्फर्म") || s.contains("पक्का करो")

        val isBillQuery = lower.contains("bill") || lower.contains("total") || lower.contains("check") ||
                s.contains("బిల్లు") || s.contains("బిల్") || s.contains("ఎంత") || s.contains("ఖర్చు") ||
                s.contains("बिल") || s.contains("कितना") || s.contains("रुपये")

        val words = lower.split(Regex("[^a-zA-Z0-9\u0C00-\u0C7F\u0900-\u097F]+")).filter { it.isNotBlank() }.toSet()
        val isGreeting = words.contains("hello") || words.contains("hi") || words.contains("hey") || words.contains("namaste") ||
                s.contains("నమస్తే") || s.contains("హలో") || s.contains("బాగున్నారా") ||
                s.contains("नमस्ते") || s.contains("प्रणाम")

        val isExplicitAddToCart = lower.startsWith("add ") || lower.contains("add to cart") || lower.contains("add to order") ||
                lower.startsWith("order ") || lower.contains("ఆర్డర్") || lower.contains("చేర్చండి") ||
                lower.contains("जोड़ो") || lower.contains("ऑर्डर करो")

        val hasQuantityPrefix = Regex("^(\\d+|ఒక|రెండు|మూడు|నాలుగు|एक|दो|तीन|चार|one|two|three|four)\\s+").containsMatchIn(s)

        val isCartOrSystemAction = isRemoveItem || isClearCart || isCustomization || isSplitQuery || isConfirmation || isBillQuery || isGreeting || isExplicitAddToCart || hasQuantityPrefix

        val culinaryDish = findCulinaryDish(s)
        val isDishOrDietaryInquiry = !isCartOrSystemAction && (culinaryDish != null || lower.contains("what is") || lower.contains("what's") ||
                lower.contains("tell me about") || lower.contains("explain") || lower.contains("describe") ||
                lower.contains("vegetarian") || lower.contains("jain") || lower.contains("vegan") ||
                lower.contains("nut-free") || lower.contains("dairy") ||
                s.contains("అంటే ఏమిటి") || s.contains("గురించి") || s.contains("శాకాహార") || s.contains("జైన్") ||
                s.contains("क्या है") || s.contains("के बारे में") || s.contains("शाकाहारी") || s.contains("जैन"))

        val isMenuQuery = lower.contains("menu") || lower.contains("special") || lower.contains("recommend") || lower.contains("available") ||
                s.contains("మెనూ") || s.contains("ఏమున్నాయి") || s.contains("ఏమేమి") || s.contains("ప్రత్యేకం") ||
                s.contains("मेन्यू") || s.contains("क्या है") || s.contains("खास") || s.contains("मिलेगा")

        if (isDishOrDietaryInquiry) {
            return generateLocalDietaryResponse(s, language, "Svara Restaurant", emptyList())
        }

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
                    headline = "Welcome to Svara"
                    spoken = "నమస్కారం! స్వర కి స్వాగతం. మీరు ఈరోజు ఏమి ఆర్డర్ చేయాలనుకుంటున్నారు?"
                } else if (isHindi) {
                    headline = "Welcome to Svara"
                    spoken = "नमस्ते! स्वरा में आपका स्वागत है। आप आज क्या खाना पसंद करेंगे?"
                } else {
                    headline = "Welcome to Svara"
                    spoken = "Hello and welcome to Svara. What would you like to order today?"
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
                        "Welcome to Svara. What would you like to order today?"
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
     * Intelligent local dietary and culinary reasoner evaluating comprehensive dining knowledge:
     * - Universal dish explanations: origin, culinary style, flavor & texture profile, dietary tags, spice level.
     * - Strict food & dining guardrails: polite redirection on non-food queries.
     * - Vegetarian, Jain (no root vegetables/onion/garlic), Nut-Free (cashew/kaju/peanut safety), Vegan & Dairy checks.
     * - Native spoken responses in Telugu, Hindi, and English.
     */
    fun generateLocalDietaryResponse(
        userQuery: String,
        language: String,
        restaurantName: String,
        menuItems: List<MenuItemEntity>,
        sections: List<MenuSectionEntity> = emptyList(),
        webKnowledge: String? = null
    ): StructuredAiResponse {
        val s = userQuery.trim()
        val lower = s.lowercase()

        val isTelugu = language == "te" || s.any { it in '\u0C00'..'\u0C7F' }
        val isHindi = language == "hi" || s.any { it in '\u0900'..'\u097F' }

        // 1. STRICT FOOD & DINING GUARDRAILS CHECK
        if (isNonFoodQuery(s)) {
            return generateGuardrailResponse(if (isTelugu) "te" else if (isHindi) "hi" else language)
        }

        val matchedItem = findMatchingMenuItem(s, menuItems)
        val culinaryDish = findCulinaryDish(s)

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

        // Dish explanation / culinary curiosity intent
        val isCulinaryInquiry = lower.contains("what is") || lower.contains("what's") ||
                lower.contains("tell me about") || lower.contains("explain") || lower.contains("describe") ||
                lower.contains("how is") || lower.contains("how to make") || lower.contains("recipe") ||
                lower.contains("prepared") || lower.contains("made of") || lower.contains("ingredients of") ||
                lower.contains("specialty") || lower.contains("meaning") ||
                s.contains("అంటే ఏమిటి") || s.contains("అంటే ఏంటి") || s.contains("గురించి") ||
                s.contains("ఎలా చేస్తారు") || s.contains("ఎలా తయారు చేస్తారు") || s.contains("వివరించండి") ||
                s.contains("తయారీ") ||
                s.contains("क्या है") || s.contains("के बारे में") || s.contains("कैसे बनता है") ||
                s.contains("कैसे बनाते हैं") || s.contains("की विधि") || s.contains("रेसिपी")

        // Multi-dimensional ask or dietary profile check (e.g. "Ask: is this vegetarian, Jain or nut-free?")
        val isComprehensiveDietaryAsk = matchedItem != null && !isCulinaryInquiry && (
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

            // 2. RICH LOCAL CULINARY ENCYCLOPEDIA (Dish Explanations, Origin, Profile, Preparation)
            culinaryDish != null && (isCulinaryInquiry || (matchedItem == null && !isJainQuery && !isNutQuery && !isVeganQuery)) -> {
                val dish = culinaryDish
                val headline = "${dish.nameEnglish} - ${dish.category}"
                val spoken = if (isTelugu) dish.spokenTe else if (isHindi) dish.spokenHi else dish.spokenEn

                val rows = listOf(
                    listOf("Origin & Style", dish.origin),
                    listOf("Flavor & Texture", dish.flavorProfile),
                    listOf("Dietary Classification", dish.dietary),
                    listOf("Spice Level", "${dish.spiceLevel}/3 (${dish.spiceLevelStr})")
                )

                val actions = mutableListOf<UiAction>()
                actions.add(UiAction(id = "act_culinary_ans", label = "Culinary Details", actionType = "ANSWER_DIETARY", payload = matchedItem?.itemId ?: dish.nameEnglish))
                if (matchedItem != null) {
                    actions.add(UiAction(id = "act_highlight_${matchedItem.itemId}", label = "View ${matchedItem.nameEnglish}", actionType = "HIGHLIGHT_ITEM", payload = matchedItem.itemId))
                }

                return StructuredAiResponse(
                    intent = "DIETARY_QUERY",
                    headline = headline,
                    spoken = spoken,
                    caption = spoken,
                    bullets = listOf(
                        "Dish: ${dish.nameEnglish}",
                        "Origin: ${dish.origin}",
                        "Dietary: ${dish.dietary}",
                        "Spice Level: ${dish.spiceLevelStr}"
                    ),
                    table = StructuredTable(
                        columns = listOf("Attribute", "Details"),
                        rows = rows
                    ),
                    confidence = "high",
                    needsStaffConfirmation = (!dish.isNutFree || (!dish.isJain && dish.isVeg)),
                    actions = actions
                )
            }

            // 3. COMPREHENSIVE 360-DEGREE DIETARY PROFILE FOR AN ITEM
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

            // 4. JAIN QUERIES
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

            // 8. VEGETARIAN / GENERAL FOOD QUERIES
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

            // 9. SPICE LEVEL QUERIES
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

            // 10. LIVE CULINARY WEB KNOWLEDGE OR GENERAL OVERVIEW FALLBACK
            else -> {
                if (!webKnowledge.isNullOrBlank()) {
                    val dishTerm = culinaryWebSearchClient.extractDishSearchTerm(userQuery)
                    val headline = if (dishTerm.isNotBlank()) "Culinary Info: $dishTerm" else "Culinary Knowledge"
                    val spoken = if (isTelugu) {
                        "పాకశాస్త్ర సమాచారం ప్రకారం: $webKnowledge"
                    } else if (isHindi) {
                        "रेसिपी जानकारी के अनुसार: $webKnowledge"
                    } else {
                        "According to culinary knowledge: $webKnowledge"
                    }

                    return StructuredAiResponse(
                        intent = "DIETARY_QUERY",
                        headline = headline,
                        spoken = spoken,
                        caption = spoken,
                        bullets = listOf("Query: $userQuery", "Live Culinary Web Knowledge: $webKnowledge"),
                        confidence = "high",
                        actions = listOf(
                            UiAction(id = "act_culinary_web", label = "Culinary Details", actionType = "ANSWER_DIETARY", payload = dishTerm)
                        )
                    )
                }

                val spoken = if (isTelugu) {
                    "$restaurantName లోని వంటకాలపై జైన్, శాకాహారం, రెసిపీ లేదా అలర్జీ వివరాలను తెలుసుకోవడానికి నన్ను అడగండి."
                } else if (isHindi) {
                    "$restaurantName के व्यंजनों के लिए जैन, शाकाहारी, रेसिपी या नट-एलर्जी की जानकारी के लिए मुझसे पूछें।"
                } else {
                    "Ask me about culinary origin, ingredients, recipes, vegetarian, Jain, nut-free, or allergen information for any dish at $restaurantName."
                }

                return StructuredAiResponse(
                    intent = "DIETARY_QUERY",
                    headline = "Culinary & Dietary Intelligence",
                    spoken = spoken,
                    caption = spoken,
                    bullets = listOf(
                        "Restaurant: $restaurantName",
                        "Supported Inquiries: Dish explanations, Origin, Recipes, Vegetarian, Jain (No onion/garlic), Nut-free (Kaju/Peanut), Vegan, Dairy & Spice level"
                    ),
                    actions = listOf(
                        UiAction(id = "act_dietary_general", label = "Explore Culinary Menu", actionType = "ANSWER_DIETARY", payload = "general")
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
            listOf("గులాబ్ జామున్", "गुलाब जामुन") to "gulab jamun",
            listOf("రసం", "చారు", "रसम") to "rasam",
            listOf("చిల్లీ చికెన్", "चिल्ली चिकन") to "chilli chicken"
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
            "filter coffee", "masala chai", "gulab jamun", "chilli chicken", "chicken 65",
            "butter chicken", "chicken tikka", "mutton rogan josh", "fish curry", "prawns fry",
            "egg curry", "tandoori chicken", "hakka noodles", "gobi manchurian", "manchurian", "rasam"
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
            "this", "that", "ask", "tell", "show", "is", "it", "any", "some", "how"
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
