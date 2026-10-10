package com.sarvaai.app.dialogue.dietary

import com.sarvaai.app.data.entities.MenuItemEntity

enum class DietaryCompliance {
    COMPLIANT,
    VIOLATION,
    UNKNOWN_NEEDS_STAFF_CONFIRMATION
}

data class DietaryCheckResult(
    val compliance: DietaryCompliance,
    val explanation: String,
    val identifiedIngredients: List<String> = emptyList(),
    val needsStaffConfirmation: Boolean = false
)

object DietaryRuleEngine {

    private val ROOT_VEGETABLES = listOf(
        "onion", "pyaz", "ullipayalu", "garlic", "lasun", "vellulli", "potato", "aloo", "bangaladumpa",
        "carrot", "gajar", "radish", "mooli", "ginger", "adrak", "allam", "beetroot", "turnip"
    )

    private val DAIRY_ITEMS = listOf(
        "paneer", "ghee", "butter", "makhan", "curd", "dahi", "perugu",
        "cream", "malai", "cheese", "milk", "paalu", "khoya", "mawa"
    )

    private val NUT_ITEMS = listOf(
        "cashew", "kaju", "jeedipappu", "almond", "badam", "peanut", "moongphali", "pallilu",
        "walnut", "akhrot", "pistachio", "pista", "hazelnut", "pine nut"
    )

    private val NUT_GRAVY_KEYWORDS = listOf("shahi", "korma", "kurma", "makhani", "pasanda", "mughlai", "rich gravy")

    fun checkJainCompliance(item: MenuItemEntity, language: String = "en"): DietaryCheckResult {
        if (!item.isVegetarian) {
            val expl = when (language.lowercase()) {
                "te" -> "${item.nameEnglish} నాన్-వెజ్ వంటకం, జైన ఆహార నిబంధనలకు తగినది కాదు."
                "hi" -> "${item.nameEnglish} मांसाहारी व्यंजन है और जैन आहार के लिए उपयुक्त नहीं है।"
                else -> "${item.nameEnglish} is non-vegetarian and not suitable for Jain diets."
            }
            return DietaryCheckResult(
                compliance = DietaryCompliance.VIOLATION,
                explanation = expl
            )
        }

        val descLower = (item.description ?: "").lowercase()
        val nameLower = "${item.nameEnglish} ${item.nameTelugu ?: ""} ${item.nameHindi ?: ""}".lowercase()
        val combinedText = "$nameLower $descLower"

        val foundRootVeg = ROOT_VEGETABLES.filter { combinedText.contains(it) }
        if (foundRootVeg.isNotEmpty()) {
            val expl = when (language.lowercase()) {
                "te" -> "హెచ్చరిక: ${item.nameEnglish} లో భూమి అడుగున పండే దుంపలు/కూరగాయలు (${foundRootVeg.joinToString()}) ఉన్నాయి. ఇది జైనులకు సరిపడదు."
                "hi" -> "चेतावनी: ${item.nameEnglish} में कंदमूल (${foundRootVeg.joinToString()}) शामिल हैं। यह जैन आहार के अनुकूल नहीं है।"
                else -> "Warning: ${item.nameEnglish} contains root vegetables (${foundRootVeg.joinToString()}) and violates Jain diet."
            }
            return DietaryCheckResult(
                compliance = DietaryCompliance.VIOLATION,
                explanation = expl,
                identifiedIngredients = foundRootVeg
            )
        }

        if (item.isJain) {
            val expl = when (language.lowercase()) {
                "te" -> "${item.nameEnglish} స్పష్టంగా జైన్ డైట్‌కు అనుకూలమైనదిగా గుర్తించబడింది."
                "hi" -> "${item.nameEnglish} विशेष रूप से जैन आहार के अनुकूल चिह्नित है।"
                else -> "${item.nameEnglish} is explicitly marked as Jain suitable."
            }
            return DietaryCheckResult(
                compliance = DietaryCompliance.COMPLIANT,
                explanation = expl
            )
        }

        // Check if it's a standard restaurant curry that typically uses onion/garlic base
        val isCurryOrGravy = combinedText.contains("curry") || combinedText.contains("masala") ||
                combinedText.contains("gravy") || combinedText.contains("paneer") || combinedText.contains("dal")

        if (isCurryOrGravy) {
            val expl = when (language.lowercase()) {
                "te" -> "రెస్టారెంట్ గ్రేవీలలో సాధారణంగా ఉల్లిపాయ లేదా వెల్లుల్లి ఉపయోగిస్తారు. దయచేసి వెయిటర్‌ను అడిగి నిర్ధారించుకోండి."
                "hi" -> "होटल की ग्रेवी में आमतौर पर प्याज या लहसुन होता है। कृपया वेटर से जैन तैयारी की पुष्टि करें।"
                else -> "Restaurant gravies typically contain onion or garlic. Please confirm with staff if ${item.nameEnglish} can be prepared Jain."
            }
            return DietaryCheckResult(
                compliance = DietaryCompliance.UNKNOWN_NEEDS_STAFF_CONFIRMATION,
                explanation = expl,
                needsStaffConfirmation = true
            )
        }

        val expl = when (language.lowercase()) {
            "te" -> "${item.nameEnglish} సాధారణంగా జైన్ డైట్‌కు సురక్షితమైనది."
            "hi" -> "${item.nameEnglish} आमतौर पर जैन आहार के लिए सुरक्षित है।"
            else -> "${item.nameEnglish} appears suitable for Jain diet based on listed ingredients."
        }
        return DietaryCheckResult(
            compliance = DietaryCompliance.COMPLIANT,
            explanation = expl
        )
    }

    fun checkNutAllergy(item: MenuItemEntity, language: String = "en"): DietaryCheckResult {
        val descLower = (item.description ?: "").lowercase()
        val nameLower = "${item.nameEnglish} ${item.nameTelugu ?: ""} ${item.nameHindi ?: ""}".lowercase()
        val combinedText = "$nameLower $descLower"

        val foundNuts = NUT_ITEMS.filter { combinedText.contains(it) }
        if (foundNuts.isNotEmpty() || !item.isNutFree) {
            val nutList = if (foundNuts.isNotEmpty()) foundNuts.joinToString() else "nuts/dry fruits"
            val expl = when (language.lowercase()) {
                "te" -> "తీవ్ర హెచ్చరిక: ${item.nameEnglish} లో నట్స్ ($nutList) ఉన్నాయి! నట్ ఎలర్జీ ఉన్నవారు తినకూడదు."
                "hi" -> "गंभीर चेतावनी: ${item.nameEnglish} में मेवे/नट्स ($nutList) हैं! नट एलर्जी वाले लोग न खाएं।"
                else -> "High Alert: ${item.nameEnglish} contains nuts ($nutList)! Do not consume if you have nut allergies."
            }
            return DietaryCheckResult(
                compliance = DietaryCompliance.VIOLATION,
                explanation = expl,
                identifiedIngredients = foundNuts
            )
        }

        val foundGravyKeywords = NUT_GRAVY_KEYWORDS.filter { combinedText.contains(it) }
        if (foundGravyKeywords.isNotEmpty()) {
            val expl = when (language.lowercase()) {
                "te" -> "${item.nameEnglish} రిచ్ గ్రేవీ (${foundGravyKeywords.joinToString()}) లో జీడిపప్పు పేస్ట్ కలిసే అవకాశం ఉంది. దయచేసి సిబ్బందిని అడిగి తెలుసుకోండి."
                "hi" -> "${item.nameEnglish} की शाही ग्रेवी (${foundGravyKeywords.joinToString()}) में काजू पेस्ट होने की संभावना है। कृपया स्टाफ से पुष्टि करें।"
                else -> "${item.nameEnglish} has a rich gravy (${foundGravyKeywords.joinToString()}) which frequently uses cashew paste. Please check with kitchen staff."
            }
            return DietaryCheckResult(
                compliance = DietaryCompliance.UNKNOWN_NEEDS_STAFF_CONFIRMATION,
                explanation = expl,
                needsStaffConfirmation = true
            )
        }

        val expl = when (language.lowercase()) {
            "te" -> "జాబితాలో నట్స్ లేవు, అయితే తీవ్రమైన ఎలర్జీ ఉంటే కిచెన్‌లో క్రాస్-కాంటామినేషన్ గురించి అడగండి."
            "hi" -> "मेनू में कोई मेवा नहीं है, फिर भी गंभीर एलर्जी के लिए स्टाफ से क्रॉस-संदूषण की जांच करें।"
            else -> "No nuts listed in menu description. Always verify with kitchen if you have severe anaphylaxis."
        }
        return DietaryCheckResult(
            compliance = DietaryCompliance.COMPLIANT,
            explanation = expl
        )
    }

    fun checkVeganCompliance(item: MenuItemEntity, language: String = "en"): DietaryCheckResult {
        if (!item.isVegetarian) {
            return DietaryCheckResult(
                compliance = DietaryCompliance.VIOLATION,
                explanation = "${item.nameEnglish} is non-vegetarian and not vegan."
            )
        }

        val descLower = (item.description ?: "").lowercase()
        val nameLower = "${item.nameEnglish} ${item.nameTelugu ?: ""} ${item.nameHindi ?: ""}".lowercase()
        val combinedText = "$nameLower $descLower"

        val foundDairy = DAIRY_ITEMS.filter { combinedText.contains(it) }
        if (foundDairy.isNotEmpty()) {
            val expl = when (language.lowercase()) {
                "te" -> "${item.nameEnglish} లో పాల ఉత్పత్తులు (${foundDairy.joinToString()}) ఉన్నాయి, కాబట్టి ఇది వీగన్ కాదు."
                "hi" -> "${item.nameEnglish} में डेयरी उत्पाद (${foundDairy.joinToString()}) शामिल हैं, इसलिए यह वीगन नहीं है।"
                else -> "${item.nameEnglish} contains dairy (${foundDairy.joinToString()}) and is not vegan."
            }
            return DietaryCheckResult(
                compliance = DietaryCompliance.VIOLATION,
                explanation = expl,
                identifiedIngredients = foundDairy
            )
        }

        if (item.isVegan) {
            return DietaryCheckResult(
                compliance = DietaryCompliance.COMPLIANT,
                explanation = "${item.nameEnglish} is 100% plant-based and vegan friendly."
            )
        }

        // Ghee / Butter query check for Indian breads or sweets
        val isPotentiallyButtered = combinedText.contains("roti") || combinedText.contains("naan") ||
                combinedText.contains("dosa") || combinedText.contains("sweet")

        if (isPotentiallyButtered) {
            val expl = when (language.lowercase()) {
                "te" -> "${item.nameEnglish} పై నెయ్యి లేదా వెన్న రాసే అవకాశం ఉంది. నెయ్యి లేకుండా చేయమని అడగండి."
                "hi" -> "${item.nameEnglish} पर घी या मक्खन लगाया जा सकता है। कृपया बिना घी का अनुरोध करें।"
                else -> "${item.nameEnglish} may be brushed with ghee or butter. Request preparation without dairy/ghee."
            }
            return DietaryCheckResult(
                compliance = DietaryCompliance.UNKNOWN_NEEDS_STAFF_CONFIRMATION,
                explanation = expl,
                needsStaffConfirmation = true
            )
        }

        return DietaryCheckResult(
            compliance = DietaryCompliance.COMPLIANT,
            explanation = "${item.nameEnglish} appears vegan suitable."
        )
    }
}
