package com.sarvaai.app.service.waiter

data class PhraseChip(
    val id: String,
    val category: String, // "DISABILITY", "ESSENTIALS", "FOOD_PREF", "SERVICE"
    val englishText: String,
    val hindiText: String,
    val teluguText: String,
    val iconName: String,
    val phoneticSpokenHint: String? = null
) {
    fun getTextForLanguage(langCode: String): String {
        return when (langCode.lowercase()) {
            "hi" -> hindiText
            "te" -> teluguText
            else -> englishText
        }
    }
}

object PhraseBoard {
    val DINING_PHRASES = listOf(
        // Disability & Communication Notices
        PhraseChip(
            id = "deaf_notice",
            category = "DISABILITY",
            englishText = "I have hearing loss. Please point, gesture or write.",
            hindiText = "मुझे सुनने में कठिनाई है। कृपया लिखकर या दिखाकर बताएं।",
            teluguText = "నాకు వినికిడి లోపం ఉంది. దయచేసి రాసి లేదా చూపించి చెప్పండి.",
            iconName = "hearing_disabled"
        ),
        PhraseChip(
            id = "blind_notice",
            category = "DISABILITY",
            englishText = "I have visual impairment. Please read aloud clearly.",
            hindiText = "मुझे देखने में कठिनाई है। कृपया स्पष्ट आवाज़ में पढ़कर बताएं।",
            teluguText = "నాకు చూపు మందగించింది. దయచేసి వివరంగా చదివి వినిపించండి.",
            iconName = "visibility_off"
        ),

        // Urgent Dining Essentials
        PhraseChip(
            id = "water_please",
            category = "ESSENTIALS",
            englishText = "Please bring drinking water.",
            hindiText = "कृपया पीने का पानी लेकर आइए।",
            teluguText = "దయచేసి మంచి నీరు తీసుకురండి.",
            iconName = "water_drop"
        ),
        PhraseChip(
            id = "bill_please",
            category = "ESSENTIALS",
            englishText = "Please bring the bill / check.",
            hindiText = "कृपया बिल लेकर आइए।",
            teluguText = "దయచేసి బిల్లు తీసుకురండి.",
            iconName = "receipt_long"
        ),
        PhraseChip(
            id = "upi_qr_please",
            category = "ESSENTIALS",
            englishText = "Please bring the UPI QR code scanner for payment.",
            hindiText = "कृपया पेमेंट के लिए UPI QR कोड स्कैनर लाइए।",
            teluguText = "దయచేసి పేమెంట్ కొరకు UPI QR కోడ్ స్కానర్ తీసుకురండి.",
            iconName = "qr_code_scanner"
        ),

        // Food & Dietary Specifications
        PhraseChip(
            id = "less_spicy",
            category = "FOOD_PREF",
            englishText = "Please make the food less spicy.",
            hindiText = "कृपया खाना कम तीखा बनाइए।",
            teluguText = "దయచేసి కారం తక్కువగా చేయండి.",
            iconName = "local_fire_department"
        ),
        PhraseChip(
            id = "no_onion_garlic",
            category = "FOOD_PREF",
            englishText = "Please prepare without onion and garlic (Jain/Satvik).",
            hindiText = "कृपया बिना प्याज और लहसुन (जैन/सात्विक) के बनाइए।",
            teluguText = "దయచేసి ఉల్లిపాయ మరియు వెల్లుల్లి లేకుండా తయారు చేయండి.",
            iconName = "no_meals"
        ),
        PhraseChip(
            id = "extra_hot_food",
            category = "FOOD_PREF",
            englishText = "Please serve the food piping hot.",
            hindiText = "कृपया गरमा-गरम खाना परोसिए।",
            teluguText = "దయచేసి ఆహారాన్ని వేడి వేడిగా వడ్డించండి.",
            iconName = "whatshot"
        ),

        // Table & Service Assistance
        PhraseChip(
            id = "cutlery_please",
            category = "SERVICE",
            englishText = "Please bring extra spoons, forks and small bowls.",
            hindiText = "कृपया चम्मच, कांटे और छोटी कटोरियाँ लाइए।",
            teluguText = "దయచేసి చెంచాలు మరియు అదనపు ప్లేట్లు తీసుకురండి.",
            iconName = "restaurant"
        ),
        PhraseChip(
            id = "extra_sambar_chutney",
            category = "SERVICE",
            englishText = "Please bring extra hot sambar and chutney.",
            hindiText = "कृपया अतिरिक्त गर्म सांभर और चटनी लाइए।",
            teluguText = "దయచేసి అదనపు సాంబార్ మరియు చట్నీ తీసుకురండి.",
            iconName = "soup_kitchen"
        ),
        PhraseChip(
            id = "clean_table",
            category = "SERVICE",
            englishText = "Please wipe and clean our table.",
            hindiText = "कृपया हमारे टेबल को साफ कर दीजिए।",
            teluguText = "దయచేసి మా టేబుల్ శుభ్రం చేయండి.",
            iconName = "cleaning_services"
        ),
        PhraseChip(
            id = "food_delay_status",
            category = "SERVICE",
            englishText = "How much more time will our order take?",
            hindiText = "हमारे खाने में और कितना समय लगेगा?",
            teluguText = "మా ఆర్డర్ రావడానికి ఇంకా ఎంత సమయం పడుతుంది?",
            iconName = "hourglass_top"
        ),
        PhraseChip(
            id = "pack_parcel",
            category = "SERVICE",
            englishText = "Please pack the remaining food for takeaway parcel.",
            hindiText = "कृपया बचा हुआ खाना पार्सल के लिए पैक कर दीजिए।",
            teluguText = "దయచేసి మిగిలిన ఆహారాన్ని పార్శిల్ చేయండి.",
            iconName = "takeout_dining"
        ),
        PhraseChip(
            id = "washroom_location",
            category = "SERVICE",
            englishText = "Where is the washroom / hand wash area?",
            hindiText = "वॉशरूम / हाथ धोने की जगह कहाँ है?",
            teluguText = "వాష్‌రూమ్ లేదా చేతులు కడుక్కునే స్థలం ఎక్కడ ఉంది?",
            iconName = "wash"
        )
    )

    fun getPhrasesByCategory(category: String): List<PhraseChip> {
        return DINING_PHRASES.filter { it.category == category }
    }
}
