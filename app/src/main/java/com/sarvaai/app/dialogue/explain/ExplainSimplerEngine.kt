package com.sarvaai.app.dialogue.explain

object ExplainSimplerEngine {

    /**
     * Builds standard prompts for LLM when dynamic online generation is requested.
     */
    fun buildExplanationPrompt(dishName: String, description: String?, level: Int, language: String): String {
        val langName = when (language.lowercase()) {
            "te" -> "Telugu"
            "hi" -> "Hindi"
            else -> "English"
        }

        return when (level) {
            3 -> """
            Explain the food dish '$dishName' for an elderly person, child, or someone needing maximum simplicity in $langName.
            Rules:
            - Maximum 2 very short sentences.
            - Total words strictly under 20 words.
            - Use everyday comparisons (e.g. 'soft steamed rice cake', 'crispy golden crepe with spiced potatoes', 'sweet fried milk balls').
            - Zero culinary jargon or complex words.
            - Output clean spoken text directly in $langName.
            """.trimIndent()

            2 -> """
            Explain the dish '$dishName' in clear, simple everyday $langName.
            Rules:
            - 2 to 3 concise sentences.
            - Describe the main ingredients, taste (sweet, sour, spicy, mild), and texture.
            - Explain any unfamiliar regional terms in simple words.
            - Output clean spoken text directly in $langName.
            """.trimIndent()

            else -> """
            Provide a rich culinary and sensory description of '$dishName' in $langName including authentic spices, cooking technique, dietary profile, and traditional accompaniments.
            """.trimIndent()
        }
    }

    /**
     * Instant on-device explanation generator for common dishes across Telugu, Hindi, and English,
     * ensuring zero latency for accessibility.
     */
    fun getInstantExplanation(dishName: String, level: Int, language: String): String {
        val lower = dishName.lowercase().trim()
        val lang = language.lowercase()

        return when {
            lower.contains("dosa") || lower.contains("దోశ") || lower.contains("डोसा") -> {
                when (level) {
                    3 -> when (lang) {
                        "te" -> "బంగారు రంగులో కరకరలాడే బియ్యపు అట్టు. లోపల మెత్తని బంగాళాదుంప మసాలా ఉంటుంది."
                        "hi" -> "कुरकुरा सुनहरा चावल का चीला। अंदर मसालेदार आलू भरा होता है।"
                        else -> "Crispy golden savory crepe filled with soft spiced potatoes."
                    }
                    2 -> when (lang) {
                        "te" -> "మసాలా దోశ బియ్యం, మినప్పప్పు పిండితో పెనంపై కాల్చే కరకరలాడే వంటకం. దీనిని కొబ్బరి చట్నీ మరియు సాంబార్‌తో తింటారు."
                        "hi" -> "मसाला डोसा चावल और दाल के घोल से बना कुरकुरा रोल है। इसे नारियल चटनी और सांभर के साथ परोसा जाता है।"
                        else -> "A crispy fermented rice and lentil crepe stuffed with spiced potato mash, served with coconut chutney and sambar."
                    }
                    else -> when (lang) {
                        "te" -> "దక్షిణ భారతదేశ ప్రసిద్ధ అల్పాహారం. సుగంధ ద్రవ్యాలతో వేయించిన బంగాళాదుంపలు, కరకరలాడే బంగారు రంగు రోస్ట్, తాజా సాంబార్ మరియు చట్నీలతో లభిస్తుంది."
                        "hi" -> "दक्षिण भारतीय पारंपरिक स्वादिष्ट व्यंजन, जो बारीक पिसे चावल और उड़द दाल के किण्वित घोल से तवे पर सुनहरा सेका जाता है।"
                        else -> "An iconic South Indian culinary masterpiece: a golden-brown crispy fermented crepe folded around a fragrant turmeric and mustard potato curry, accompanied by piping hot sambar."
                    }
                }
            }

            lower.contains("idli") || lower.contains("ఇడ్లీ") || lower.contains("इडली") -> {
                when (level) {
                    3 -> when (lang) {
                        "te" -> "మెత్తగా దూదిలా ఉండే ఆవిరి కుడుము. కడుపుకి చాలా తేలికైనది."
                        "hi" -> "भाप में पकी रुई जैसी नरम टिकिया। पचने में बहुत हल्की।"
                        else -> "Soft, fluffy steamed rice cakes that melt in your mouth."
                    }
                    2 -> when (lang) {
                        "te" -> "ఇడ్లీ ఆవిరిపై ఉడికించిన ఆరోగ్యకరమైన వంటకం. నూనె ఉండదు, సాంబార్ మరియు పల్లి చట్నీతో చాలా రుచిగా ఉంటుంది."
                        "hi" -> "इडली चावल और दाल से भाप में पकाई गई पौष्टिक और तेल-मुक्त डिश है, जिसे सांभर के साथ खाया जाता है।"
                        else -> "Steamed, spongy savory cakes made of fermented rice and lentils, served oil-free with warm lentil soup and chutney."
                    }
                    else -> when (lang) {
                        "te" -> "సంప్రదాయ ఆవిరి వంటకం. జీర్ణవ్యవస్థకు ఎంతో మేలు చేసే ప్రోటీన్ మరియు కార్బోహైడ్రేట్ల సమతుల్య ఆహారం."
                        "hi" -> "पारंपरिक दक्षिण भारतीय भाप से पकाया गया नाश्ता, जो अत्यंत सुपाच्य, स्वास्थ्यवर्धक और पौष्टिक होता है।"
                        else -> "A naturally fermented, steamed delicacy packed with probiotic benefits, boasting a cloud-like texture and paired with slow-simmered vegetable stew."
                    }
                }
            }

            lower.contains("vada") || lower.contains("వడ") || lower.contains("वड") -> {
                when (level) {
                    3 -> when (lang) {
                        "te" -> "బయట కరకరలాడుతూ లోపల మెత్తగా ఉండే మినప గారె."
                        "hi" -> "बाहर से कुरकुरा और अंदर से नरम दाल का डोनट।"
                        else -> "Crispy on the outside, fluffy soft inside savory lentil doughnut."
                    }
                    2 -> when (lang) {
                        "te" -> "మినప్పప్పుతో నూనెలో వేయించిన రుచికరమైన గారె. మిరియాలు, అల్లం మరియు జీలకర్ర సువాసనతో ఉంటుంది."
                        "hi" -> "उड़द दाल, काली मिर्च और जीरे से बना तला हुआ कुरकुरा नाश्ता, जिसे चटनी में डुबोकर खाते हैं।"
                        else -> "Deep-fried crispy lentil fritter spiced with black pepper, ginger, and cumin, served hot with sambar."
                    }
                    else -> when (lang) {
                        "te" -> "మినప పప్పుతో తయారుచేసి నూనెలో బంగారు రంగు వచ్చే వరకు వేయించిన సంప్రదాయ రుచికరమైన గారె."
                        "hi" -> "उड़द दाल के पेस्ट से बना सुनहरे रंग का क्रिस्पी वड़ा, जो अंदर से अत्यधिक मुलायम और खुशबूदार होता है।"
                        else -> "A golden-fried savory lentil doughnut tempered with cracked peppercorns, curry leaves, and fresh ginger, presenting a contrasting crispy crust and airy center."
                    }
                }
            }

            lower.contains("paneer") || lower.contains("పనీర్") || lower.contains("पनीर") -> {
                when (level) {
                    3 -> when (lang) {
                        "te" -> "వెన్నలాంటి క్రీమీ గ్రేవీలో మెత్తని స్వచ్ఛమైన పాల చీజ్ ముక్కలు."
                        "hi" -> "मलाईदार मीठी-मसालेदार ग्रेवी में नरम दूध के पनीर टुकड़े।"
                        else -> "Soft, tender fresh cottage cheese cubes in a rich, buttery gravy."
                    }
                    2 -> when (lang) {
                        "te" -> "పనీర్ కూర టమోటా, వెన్న మరియు క్రీమ్‌తో చేసిన తియ్యటి-కారపు చిక్కటి గ్రేవీతో వస్తుంది. రోటీ లేదా నాన్‌తో బాగుంటుంది."
                        "hi" -> "पनीर बटर मसाला टमाटर, मक्खन और काजू की मखमली ग्रेवी में बना लोकप्रिय शाकाहारी मुख्य व्यंजन है।"
                        else -> "Cubes of fresh Indian cottage cheese cooked in a silky, mildly spiced tomato and butter gravy."
                    }
                    else -> when (lang) {
                        "te" -> "ఉత్తర భారత రాజసం ఉట్టిపడే వంటకం. పచ్చి పాల పనీర్ ముక్కలను వెన్న, జీడిపప్పు పేస్ట్, కసూరి మేథి మరియు తాజా క్రీమ్‌తో నెమ్మదిగా ఉడికిస్తారు."
                        "hi" -> "शाही उत्तर भारतीय व्यंजन जिसमें ताज़ा पनीर को टमाटर, काजू और मक्खन की गाढ़ी ग्रेवी में कसूरी मेथी के साथ पकाया जाता है।"
                        else -> "A sumptuous North Indian classic featuring succulent cubes of paneer simmered in a velvety reduction of vine-ripened tomatoes, cashews, aromatic spices, and fenugreek leaves."
                    }
                }
            }

            lower.contains("coffee") || lower.contains("కాఫీ") || lower.contains("कॉफ़ी") -> {
                when (level) {
                    3 -> when (lang) {
                        "te" -> "నురుగుతో కూడిన వేడి తియ్యటి సువాసనగల ఫిల్టర్ కాఫీ."
                        "hi" -> "झागदार, मीठी और गरमा-गरम ताज़ा फ़िल्टर कॉफ़ी।"
                        else -> "Frothy, rich and sweet traditional hot milk coffee."
                    }
                    2 -> when (lang) {
                        "te" -> "చిక్కటి తాజా పాలు, డికాక్షన్ మరియు చక్కెరతో సాంప్రదాయ స్టీల్ గ్లాస్‌లో నురుగుతో ఇచ్చే కాఫీ."
                        "hi" -> "ताज़ा उबले गाढ़े दूध और डिकॉक्शन से बनी पारंपरिक झाग वाली दक्षिण भारतीय कॉफ़ी।"
                        else -> "South Indian filter coffee made with freshly brewed chicory decoction, boiled frothy milk, and sugar."
                    }
                    else -> when (lang) {
                        "te" -> "సాంప్రదాయ దక్షిణ భారత ఫిల్టర్ కాఫీ. నాణ్యమైన కాఫీ గింజల డికాక్షన్‌ను చిక్కటి పాలతో డబరా సెట్‌లో నురుగు వచ్చేలా పోసి అందిస్తారు."
                        "hi" -> "ब्रास फ़िल्टर में छानी गई तेज़ डिकॉक्शन और झागदार मलाईदार दूध का उत्तम मिश्रण, पारंपरिक डबरा-लोटा में परोसा जाता है।"
                        else -> "Authentic South Indian degree filter coffee, brewed from dark roast Arabica and chicory beans, poured vigorously between dabara and tumbler to achieve a thick frothy crown."
                    }
                }
            }

            lower.contains("jamun") || lower.contains("జామున్") || lower.contains("जामुन") -> {
                when (level) {
                    3 -> when (lang) {
                        "te" -> "చక్కెర పాకంలో నానిన వేడి తీపి పాల ఉండలు."
                        "hi" -> "गुलाब की चाशनी में डूबी मीठी रसीली तली हुई गोलियां।"
                        else -> "Warm, juicy fried milk dough balls soaked in sweet rose syrup."
                    }
                    2 -> when (lang) {
                        "te" -> "గులాబ్ జామున్ కోవాతో చేసి నెయ్యిలో వేయించి, ఏలకుల సువాసన గల చక్కెర పాకంలో నానబెట్టిన ప్రసిద్ధ స్వీట్."
                        "hi" -> "मावा/खोया से बनी तली हुई गोलियां जिन्हें इलायची और केसर की मीठी चाशनी में भिगोया जाता है।"
                        else -> "Deep-fried milk-solid dumplings soaked in aromatic cardamom and saffron flavored sugar syrup."
                    }
                    else -> when (lang) {
                        "te" -> "భారతీయ పండుగల ప్రసిద్ధ మిఠాయి. స్వచ్ఛమైన కోవా మరియు పన్నీరుతో చేసి, నెయ్యిలో వేయించి, గులాబీ రేకులు మరియు ఏలకుల పాకంలో ముంచుతారు."
                        "hi" -> "शाही भारतीय मिठाई, जिसमें ताज़ा खोया की गोलियों को घी में तलकर इलायची युक्त चाशनी में तरबतर किया जाता है।"
                        else -> "Decadent Indian confection made of reduced milk solids (khoya) gently fried in clarified butter and steeped in a floral rosewater and cardamom syrup."
                    }
                }
            }

            else -> {
                // Generalized dynamic fallback for any dish
                when (level) {
                    3 -> when (lang) {
                        "te" -> "$dishName ఒక రుచికరమైన భారతీయ వంటకం. తాజాగా వండి వడ్డిస్తారు."
                        "hi" -> "$dishName एक स्वादिष्ट भारतीय व्यंजन है, जो ताज़ा बनाया जाता है।"
                        else -> "$dishName is a freshly prepared, flavorful Indian dish."
                    }
                    2 -> when (lang) {
                        "te" -> "$dishName సంప్రదాయ సుగంధ ద్రవ్యాలతో తయారు చేసిన ఆహార పదార్థం. దీనిని తాజా పదార్థాలతో చేస్తారు."
                        "hi" -> "$dishName पारंपरिक मसालों और ताज़ा सामग्री से तैयार किया जाने वाला स्वादिष्ट व्यंजन है।"
                        else -> "$dishName is a traditional dish cooked with aromatic spices and fresh ingredients."
                    }
                    else -> when (lang) {
                        "te" -> "$dishName రెస్టారెంట్ యొక్క ప్రత్యేక వంటకం. ప్రామాణికమైన పద్ధతిలో భారతీయ మసాలాలతో సువాసనభరితంగా వండబడుతుంది."
                        "hi" -> "$dishName रेस्तरां की विशेष डिश है, जिसे प्रामाणिक भारतीय मसालों के साथ पकाया जाता है।"
                        else -> "$dishName is an authentic Indian specialty prepared with select regional spices, balanced flavors, and traditional culinary techniques."
                    }
                }
            }
        }
    }
}
