package com.sarvaai.app.service.upi

import android.content.Intent
import android.net.Uri
import java.net.URLEncoder
import java.util.Locale

object UpiIntentGenerator {

    const val DEFAULT_RESTAURANT_VPA = "sarvaai.restaurant@okhdfcbank"
    const val DEFAULT_RESTAURANT_NAME = "Sarva Bhavan Restaurant"

    private fun urlEncode(text: String): String {
        return try {
            URLEncoder.encode(text, "UTF-8")
        } catch (e: Exception) {
            text.replace(" ", "%20")
        }
    }

    /**
     * Builds standard NPCI compliant UPI payment URI string.
     */
    fun buildUpiUriString(
        payeeVpa: String = DEFAULT_RESTAURANT_VPA,
        payeeName: String = DEFAULT_RESTAURANT_NAME,
        amountRupees: Double,
        transactionNote: String = "Restaurant Dining Bill",
        transactionRef: String = "TXN${System.currentTimeMillis().toString().takeLast(6)}"
    ): String {
        val formattedAmount = String.format(Locale.US, "%.2f", amountRupees)
        return "upi://pay?pa=$payeeVpa" +
                "&pn=${urlEncode(payeeName)}" +
                "&am=$formattedAmount" +
                "&cu=INR" +
                "&tn=${urlEncode(transactionNote)}" +
                "&tr=${urlEncode(transactionRef)}"
    }

    /**
     * Builds Android Intent to invoke UPI apps (GPay, PhonePe, Paytm, BHIM).
     */
    fun buildUpiIntent(
        payeeVpa: String = DEFAULT_RESTAURANT_VPA,
        payeeName: String = DEFAULT_RESTAURANT_NAME,
        amountRupees: Double,
        transactionNote: String = "Restaurant Dining Bill"
    ): Intent {
        val uriString = buildUpiUriString(
            payeeVpa = payeeVpa,
            payeeName = payeeName,
            amountRupees = amountRupees,
            transactionNote = transactionNote
        )
        return Intent(Intent.ACTION_VIEW, Uri.parse(uriString))
    }
}
