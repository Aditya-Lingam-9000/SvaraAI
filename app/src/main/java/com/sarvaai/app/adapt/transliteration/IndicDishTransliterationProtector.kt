package com.sarvaai.app.adapt.transliteration

object IndicDishTransliterationProtector {
    private val DISH_TRANSLITERATION_MAP = mapOf(
        "Butter Naan" to Pair("बटर नान", "బటర్ నాన్"),
        "Garlic Naan" to Pair("गार्लिक नान", "గార్లిక్ నాన్"),
        "Paneer Butter Masala" to Pair("पनीर बटर मसाला", "పనీర్ బటర్ మసాలా"),
        "Kadai Paneer" to Pair("कड़ाही पनीर", "కడాయి పనీర్"),
        "Dal Makhani" to Pair("दाल मखनी", "దాల్ మఖని"),
        "Masala Dosa" to Pair("मसाला डोसा", "మసాలా దోశ"),
        "Plain Dosa" to Pair("सादा डोसा", "ప్లెయిన్ దోశ"),
        "Idli Sambar" to Pair("इडली सांभर", "ఇడ్లీ సాంబార్"),
        "Filter Coffee" to Pair("फ़िल्टर कॉफ़ी", "ఫిల్టర్ కాఫీ"),
        "Chicken Biryani" to Pair("चिकन बिरयानी", "చికెన్ బిర్యానీ"),
        "Mutton Biryani" to Pair("मटन बिरयानी", "మటన్ బిర్యానీ"),
        "Veg Biryani" to Pair("वेज बिरयानी", "వెజ్ బిర్యానీ"),
        "Gulab Jamun" to Pair("गुलाब जामुन", "గులాబ్ జామున్")
    )

    fun getNativeDishName(englishName: String, targetLanguage: String): String {
        val pair = DISH_TRANSLITERATION_MAP[englishName] ?: return englishName
        return when (targetLanguage.lowercase()) {
            "hi" -> pair.first
            "te" -> pair.second
            else -> englishName
        }
    }
}
