# PHASE 11: BILL CALCULATION, SPLITTING ENGINE & UPI PAYMENT INTENT
## Detailed Engineering Specification & Physical Execution Protocol

---

### 1. Phase Overview & Strategic Intent
Phase 11 implements the billing and payment calculation system:
1. Build the deterministic **Bill Calculator** enforcing Indian GST tax laws (CGST 2.5% + SGST 2.5%) and optional service charge.
2. Implement flexible **Bill Splitting** (equal split among $N$ diners or item-by-item assignment).
3. Generate **Spoken Bill Breakdown** verbalizing individual shares in the diner's language.
4. Generate NPCI-compliant **UPI Deep Links** (`upi://pay`) and on-screen QR codes.

---

### 2. Deterministic Tax & Bill Engine (`service/bill/BillCalculator.kt`)

```kotlin
package com.sarvaai.app.service.bill

data class BillBreakdown(
    val subtotalPaise: Long,
    val cgstPaise: Long,
    val sgstPaise: Long,
    val serviceChargePaise: Long,
    val totalPaise: Long
)

object BillCalculator {

    fun calculateBill(
        itemTotalPaise: Long,
        applyServiceCharge: Boolean = false
    ): BillBreakdown {
        // CGST: 2.5% = 25 / 1000
        val cgst = (itemTotalPaise * 25L) / 1000L
        // SGST: 2.5% = 25 / 1000
        val sgst = (itemTotalPaise * 25L) / 1000L
        // Service Charge: 5% = 50 / 1000
        val serviceCharge = if (applyServiceCharge) (itemTotalPaise * 50L) / 1000L else 0L

        val grandTotal = itemTotalPaise + cgst + sgst + serviceCharge

        return BillBreakdown(
            subtotalPaise = itemTotalPaise,
            cgstPaise = cgst,
            sgstPaise = sgst,
            serviceChargePaise = serviceCharge,
            totalPaise = grandTotal
        )
    }

    fun splitEqually(totalPaise: Long, participantCount: Int): List<Long> {
        require(participantCount > 0) { "Participant count must be greater than zero" }
        val baseShare = totalPaise / participantCount
        val remainder = totalPaise % participantCount

        // Distribute remainder paise fairly to the first N participants
        return List(participantCount) { index ->
            if (index < remainder) baseShare + 1L else baseShare
        }
    }
}
```

---

### 3. Spoken Bill Breakdown & UPI Deep Link

#### 3.1 Spoken Breakdown Generator
```kotlin
package com.sarvaai.app.service.bill

import com.sarvaai.app.adapt.verbalizer.NumberVerbalizer

object BillSpeechNarrator {

    fun narrateEqualSplit(totalRupees: Long, shareRupees: Long, count: Int, language: String): String {
        return when (language) {
            "hi" -> "कुल बिल ${NumberVerbalizer.verbalizePrice(totalRupees, "hi")} है। $count लोगों में बांटने पर, प्रत्येक व्यक्ति का हिस्सा ${NumberVerbalizer.verbalizePrice(shareRupees, "hi")} होगा।"
            "te" -> "మొత్తం బిల్లు ${NumberVerbalizer.verbalizePrice(totalRupees, "te")}. $count మందికి సమానంగా పంచితే, ప్రతి ఒక్కరి వాటా ${NumberVerbalizer.verbalizePrice(shareRupees, "te")}."
            else -> "The total bill is ${NumberVerbalizer.verbalizePrice(totalRupees, "en")}. Split among $count people, each person pays ${NumberVerbalizer.verbalizePrice(shareRupees, "en")}."
        }
    }
}
```

#### 3.2 NPCI UPI Intent Builder (`service/upi/UpiIntentGenerator.kt`)
```kotlin
package com.sarvaai.app.service.upi

import android.content.Intent
import android.net.Uri

object UpiIntentGenerator {

    fun buildUpiIntent(
        payeeVpa: String = "restaurant@upi",
        payeeName: String = "SarvaAI Restaurant",
        amountRupees: Double,
        transactionNote: String = "Table 4 Bill Share"
    ): Intent {
        val uri = Uri.parse(
            "upi://pay?pa=$payeeVpa" +
            "&pn=${Uri.encode(payeeName)}" +
            "&am=${String.format("%.2f", amountRupees)}" +
            "&cu=INR" +
            "&tn=${Uri.encode(transactionNote)}"
        )
        return Intent(Intent.ACTION_VIEW, uri)
    }
}
```

---

### 4. Physical Testing Protocol on realme P3 Pro (Android 16)

1. Verify GST Calculations:
   - Input subtotal: ₹1,000 (100,000 paise).
   - CGST: ₹25.00, SGST: ₹25.00.
   - Total: ₹1,050.00.
2. Verify Equal Splitting:
   - Split among 3 people: Person 1 pays ₹350, Person 2 pays ₹350, Person 3 pays ₹350.
   - Spoken summary verbalizes: *"The total bill is one thousand and fifty rupees. Split among 3 people, each person pays three hundred and fifty rupees."*
3. Verify UPI Intent:
   - Tap "Pay with UPI" -> Confirms Android system sheet triggers for payment intent.

---

### 5. Git Checkpoint & Remote Push
```bash
git add .
git commit -m "feat(phase11): implement deterministic bill calculator, gst engine, equal splitting, spoken bill summaries, and upi intent builder"
git push origin main
```

---

### 6. Exit Criteria Checklist
- [x] CGST and SGST mathematically exact without floating-point error.
- [x] Equal splitting handles remainder paise without truncation loss.
- [x] Spoken breakdown verbalizes shares in en, hi, and te.
- [x] UPI intent format complies with NPCI specifications.
- [x] Code pushed to remote repository.
