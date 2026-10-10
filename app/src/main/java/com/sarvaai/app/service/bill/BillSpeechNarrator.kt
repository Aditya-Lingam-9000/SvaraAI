package com.sarvaai.app.service.bill

import com.sarvaai.app.adapt.verbalizer.NumberVerbalizer

object BillSpeechNarrator {

    fun narrateEqualSplit(totalRupees: Long, shareRupees: Long, count: Int, language: String): String {
        val totalWords = NumberVerbalizer.verbalizePrice(totalRupees, language)
        val shareWords = NumberVerbalizer.verbalizePrice(shareRupees, language)

        return when (language.lowercase()) {
            "hi" -> "कुल बिल $totalWords है। $count लोगों में बांटने पर, प्रत्येक व्यक्ति का हिस्सा $shareWords होगा।"
            "te" -> "మొత్తం బిల్లు $totalWords. $count మందికి సమానంగా పంచితే, ప్రతి ఒక్కరి వాటా $shareWords."
            else -> "The total bill is $totalWords. Split among $count people, each person pays $shareWords."
        }
    }

    fun narrateBillSummary(breakdown: BillBreakdown, language: String): String {
        val subtotalWords = NumberVerbalizer.verbalizePrice(breakdown.subtotalRupees, language)
        val taxWords = NumberVerbalizer.verbalizePrice(breakdown.cgstRupees + breakdown.sgstRupees, language)
        val totalWords = NumberVerbalizer.verbalizePrice(breakdown.totalRupees, language)

        return when (language.lowercase()) {
            "hi" -> "उप-योग $subtotalWords, जीएसटी कर $taxWords, और कुल देय राशि $totalWords है।"
            "te" -> "సబ్‌టోటల్ $subtotalWords, జీఎస్టీ పన్ను $taxWords, మొత్తం చెల్లించాల్సినది $totalWords."
            else -> "Subtotal is $subtotalWords, GST tax is $taxWords, and the grand total is $totalWords."
        }
    }

    fun narrateDinerShare(dinerShare: DinerShare, language: String): String {
        val shareWords = NumberVerbalizer.verbalizePrice(dinerShare.shareRupees, language)
        val personName = dinerShare.label

        return when (language.lowercase()) {
            "hi" -> "$personName का हिस्सा $shareWords है।"
            "te" -> "$personName వాటా $shareWords."
            else -> "$personName's share is $shareWords."
        }
    }
}
