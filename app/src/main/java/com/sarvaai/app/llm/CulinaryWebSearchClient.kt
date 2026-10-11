package com.sarvaai.app.llm

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit

@Serializable
private data class DuckDuckGoResponse(
    @SerialName("Heading") val heading: String = "",
    @SerialName("Abstract") val abstractContent: String = "",
    @SerialName("AbstractText") val abstractText: String = "",
    @SerialName("AbstractSource") val abstractSource: String = "",
    @SerialName("RelatedTopics") val relatedTopics: List<DuckTopic> = emptyList()
)

@Serializable
private data class DuckTopic(
    @SerialName("Text") val text: String = "",
    @SerialName("FirstURL") val firstUrl: String = ""
)

@Serializable
private data class WikipediaSummaryResponse(
    @SerialName("title") val title: String = "",
    @SerialName("description") val description: String? = null,
    @SerialName("extract") val extract: String = ""
)

@Serializable
private data class WikipediaSearchResponse(
    @SerialName("query") val query: WikipediaSearchQuery? = null
)

@Serializable
private data class WikipediaSearchQuery(
    @SerialName("search") val search: List<WikipediaSearchHit> = emptyList()
)

@Serializable
private data class WikipediaSearchHit(
    @SerialName("title") val title: String = "",
    @SerialName("snippet") val snippet: String = ""
)

/**
 * Free Live Food & Recipe Web Knowledge Retriever for SarvaAI.
 * Uses DuckDuckGo Instant Answer API and Wikipedia REST Summary / Search APIs
 * with an ultra-fast 2500ms timeout to retrieve dish definitions, origins, and ingredient summaries.
 */
class CulinaryWebSearchClient(
    private val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(2500, TimeUnit.MILLISECONDS)
        .readTimeout(2500, TimeUnit.MILLISECONDS)
        .writeTimeout(2500, TimeUnit.MILLISECONDS)
        .retryOnConnectionFailure(true)
        .build(),
    private val json: Json = Json { ignoreUnknownKeys = true; isLenient = true; encodeDefaults = true }
) {
    companion object {
        private const val TAG = "CulinaryWebSearchClient"
        private const val USER_AGENT = "SarvaAI-CulinaryBot/1.0 (culinary-assistant@sarvaai.app; Android)"
        private const val DDG_API_URL = "https://api.duckduckgo.com/?q=%s&format=json&no_html=1&skip_disambig=1"
        private const val WIKI_SUMMARY_URL = "https://en.wikipedia.org/api/rest_v1/page/summary/%s"
        private const val WIKI_SEARCH_URL = "https://en.wikipedia.org/w/api.php?action=query&list=search&srsearch=%s&format=json&utf8=1&srlimit=1"

        // Food keywords filter to only query food, dish, and cooking terms
        private val FOOD_KEYWORDS = listOf(
            // Dishes, foods & courses
            "curry", "gravy", "masala", "tikka", "tandoori", "kebab", "kabab", "kofta", "korma",
            "dosa", "idli", "vada", "sambar", "rasam", "dal", "daal", "biryani", "pulao", "rice",
            "roti", "naan", "paratha", "kulcha", "poori", "puri", "bhature", "chole", "pav bhaji",
            "paneer", "tofu", "chicken", "mutton", "lamb", "goat", "fish", "prawn", "shrimp", "crab",
            "egg", "omelette", "soup", "salad", "starter", "appetizer", "snack", "dessert", "sweet",
            "halwa", "gulab jamun", "kheer", "payasam", "lassi", "chai", "tea", "coffee", "beverage",
            "sauce", "chutney", "dip", "pachadi", "pickle", "avakaya", "gongura", "pesarattu",
            "upma", "poha", "uttapam", "khichdi", "thali", "manchurian", "chilli chicken", "noodles",
            "fried rice", "pasta", "pizza", "burger", "sandwich", "roll", "wrap", "momo", "momos",
            "chaat", "samosa", "pakora", "bhaji", "bhajiya", "vada pav", "bonda", "bajji",
            // Culinary & dietary descriptors
            "vegan", "vegetarian", "non-veg", "jain", "dairy", "cashew", "kaju", "nut", "spicy",
            "spice", "recipe", "dish", "ingredient", "ingredients", "cuisine", "food", "cook", "cooking",
            "taste", "flavor", "sweet", "sour", "crispy", "fried", "roasted", "baked", "grilled",
            // Indic terms (Telugu & Hindi)
            "పనీర్", "దోశ", "దోస", "ఇడ్లీ", "వడ", "సాంబార్", "రసం", "పప్పు", "బిర్యానీ", "పులావ్", "చికెన్",
            "మటన్", "చేప", "రొయ్య", "గుడ్డు", "కూర", "వేపుడు", "రోటీ", "చట్నీ", "పచ్చడి", "పెసరట్టు",
            "గొంగూర", "ఆవకాయ", "కారం", "తీపి", "ఆహారం", "వంటకం",
            "पनीर", "डोसा", "इडली", "वड़ा", "सांभर", "दाल", "बिरयानी", "पुलाव", "चिकन", "मटन", "मछली",
            "अंडा", "करी", "रोटी", "नान", "पराठा", "चटनी", "कबाब", "टिक्का", "मिठाई", "चाय", "कॉफ़ी",
            "भोजन", "व्यंजन", "खाना"
        )

        private val NON_FOOD_PATTERNS = listOf(
            "weather", "president", "minister", "election", "politics", "code", "python", "javascript",
            "java ", "kotlin", "script", "software", "quantum", "calculus", "algebra", "cricket",
            "capital of", "who won", "solve ", "match"
        )
    }

    /**
     * Checks if a query is related to food, cooking, or dining.
     */
    fun isFoodQuery(query: String): Boolean {
        val lower = query.lowercase().trim()
        if (lower.isEmpty()) return false

        // Check non-food guardrails first
        if (NON_FOOD_PATTERNS.any { lower.contains(it) } && !FOOD_KEYWORDS.any { lower.contains(it) || query.contains(it) }) {
            return false
        }

        // Check against known culinary keywords
        return FOOD_KEYWORDS.any { lower.contains(it) || query.contains(it) }
    }

    /**
     * Extracts clean dish search term from query (e.g., "What is chilli chicken?" -> "chilli chicken").
     */
    fun extractDishSearchTerm(query: String): String {
        var term = query.trim()

        // Strip prefixes
        val prefixRegex = Regex(
            "^(?:what(?:'s| is a| is an| is the| is| are|'s in)|tell me about|tell me what is|can you explain|explain|describe|how is|how do you make|ingredients in|ingredients of|recipe for|is|what does|how does)\\s+",
            RegexOption.IGNORE_CASE
        )
        term = term.replace(prefixRegex, "").trim()

        // Strip suffixes / question indicators in Telugu & Hindi
        term = term.replace(Regex("(?:అంటే ఏమిటి|అంటే ఏంటి|గురించి చెప్పండి|గురించి|ఎలా తయారు చేస్తారు|ఏమిటి|ఏంటి|क्या है|क्या होता है|के बारे में बताओ|कैसे बनता है|क्या सामग्री है)$"), "").trim()

        // Strip punctuation and special characters
        term = term.replace(Regex("[?!.,\"'`:;]"), "").trim()

        // Strip taste/dish suffix words if query was "what is chilli chicken dish" or "How is Dal Makhani made"
        term = term.replace(Regex("\\s+(?:made|prepared|dish|recipe|food|item)$", RegexOption.IGNORE_CASE), "").trim()

        return term.ifBlank { query.trim() }
    }

    /**
     * Fetches live dish definitions, origins, and ingredient summaries for foods using
     * DuckDuckGo Instant Answers and Wikipedia REST API with a strict 2500ms timeout.
     *
     * @param query The user's input query or dish name.
     * @return Live culinary knowledge summary or null if not found / non-food / timeout.
     */
    suspend fun searchFoodKnowledge(query: String): String? = withContext(Dispatchers.IO) {
        if (!isFoodQuery(query)) {
            Log.d(TAG, "Query '$query' is not food-related; skipping web search")
            return@withContext null
        }

        val dishTerm = extractDishSearchTerm(query)
        if (dishTerm.isBlank()) {
            return@withContext null
        }

        Log.d(TAG, "Executing culinary web search for dish: '$dishTerm'")

        // 1. Try DuckDuckGo Instant Answer API first
        try {
            val ddgResult = fetchDuckDuckGoKnowledge(dishTerm)
            if (!ddgResult.isNullOrBlank()) {
                Log.i(TAG, "DuckDuckGo knowledge found for '$dishTerm': $ddgResult")
                return@withContext ddgResult
            }
        } catch (e: Exception) {
            Log.w(TAG, "DuckDuckGo search error for '$dishTerm': ${e.message}")
        }

        // 2. Try Wikipedia Page Summary REST API
        try {
            val wikiSummary = fetchWikipediaSummary(dishTerm)
            if (!wikiSummary.isNullOrBlank()) {
                Log.i(TAG, "Wikipedia summary found for '$dishTerm': $wikiSummary")
                return@withContext wikiSummary
            }
        } catch (e: Exception) {
            Log.w(TAG, "Wikipedia summary error for '$dishTerm': ${e.message}")
        }

        // 3. Fallback: Search Wikipedia API to resolve alternative title or culinary disambiguation
        try {
            val searchHitTitle = searchWikipediaTopHit(dishTerm)
            if (!searchHitTitle.isNullOrBlank() && !searchHitTitle.equals(dishTerm, ignoreCase = true)) {
                val wikiSummary = fetchWikipediaSummary(searchHitTitle)
                if (!wikiSummary.isNullOrBlank()) {
                    Log.i(TAG, "Wikipedia search-hit summary found for '$dishTerm' (via '$searchHitTitle'): $wikiSummary")
                    return@withContext wikiSummary
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Wikipedia search fallback error for '$dishTerm': ${e.message}")
        }

        null
    }

    private fun fetchDuckDuckGoKnowledge(dishTerm: String): String? {
        val encoded = URLEncoder.encode(dishTerm, StandardCharsets.UTF_8.name())
        val url = String.format(DDG_API_URL, encoded)

        val request = Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .build()

        okHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return null
            val body = response.body?.string() ?: return null

            val ddg = json.decodeFromString<DuckDuckGoResponse>(body)
            if (ddg.abstractText.isNotBlank()) {
                val heading = ddg.heading.ifBlank { dishTerm }
                return "$heading: ${ddg.abstractText}"
            }

            // Check related topics if abstract text is empty
            for (topic in ddg.relatedTopics) {
                if (topic.text.isNotBlank() && (topic.text.contains(dishTerm, ignoreCase = true) || topic.text.contains("dish", ignoreCase = true) || topic.text.contains("cuisine", ignoreCase = true))) {
                    return topic.text
                }
            }
        }
        return null
    }

    private fun fetchWikipediaSummary(dishTerm: String): String? {
        val sanitized = dishTerm.replace(" ", "_")
        val encoded = URLEncoder.encode(sanitized, StandardCharsets.UTF_8.name())
        val url = String.format(WIKI_SUMMARY_URL, encoded)

        val request = Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .build()

        okHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return null
            val body = response.body?.string() ?: return null

            val summary = json.decodeFromString<WikipediaSummaryResponse>(body)
            if (summary.extract.isNotBlank()) {
                val desc = if (!summary.description.isNullOrBlank()) " (${summary.description})" else ""
                return "${summary.title}$desc: ${summary.extract}"
            }
        }
        return null
    }

    private fun searchWikipediaTopHit(dishTerm: String): String? {
        val query = "$dishTerm food dish"
        val encoded = URLEncoder.encode(query, StandardCharsets.UTF_8.name())
        val url = String.format(WIKI_SEARCH_URL, encoded)

        val request = Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .build()

        okHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return null
            val body = response.body?.string() ?: return null

            val searchRes = json.decodeFromString<WikipediaSearchResponse>(body)
            return searchRes.query?.search?.firstOrNull()?.title
        }
    }
}
