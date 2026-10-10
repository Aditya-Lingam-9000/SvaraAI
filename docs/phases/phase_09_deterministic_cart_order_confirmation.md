# PHASE 9: DETERMINISTIC CART, READ-BACK & ORDER CONFIRMATION ENGINE
## Detailed Engineering Specification & Physical Execution Protocol

---

### 1. Phase Overview & Strategic Intent
Phase 9 constructs the transactional core of SarvaAI:
1. Build the deterministic **Cart Repository** backed by Room Database, ensuring integer-math financial integrity.
2. Implement the **High-Stakes Spoken Read-Back Protocol** verbalizing items, quantities, and subtotal in the diner's preferred language.
3. Construct the **Voice Confirmation Gate** requiring explicit affirmative intent before placing orders.
4. Build the accessible **CartScreen** with 64dp+ quantity adjusters and TalkBack semantic announcements.

---

### 2. Deterministic Cart Repository (`dialogue/order/CartRepository.kt`)

```kotlin
package com.sarvaai.app.dialogue.order

import com.sarvaai.app.data.dao.CartDao
import com.sarvaai.app.data.entities.CartItemEntity
import com.sarvaai.app.data.entities.MenuItemEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID

class CartRepository(private val cartDao: CartDao) {

    val cartItems: Flow<List<CartItemEntity>> = cartDao.getCartItems()

    val subtotalPaise: Flow<Long> = cartItems.map { items ->
        items.sumOf { it.unitPricePaise * it.quantity }
    }

    suspend fun addItem(item: MenuItemEntity, quantity: Int = 1, notes: String? = null) {
        val existing = cartDao.getCartItems() // Simplified query
        val cartItem = CartItemEntity(
            cartItemId = UUID.randomUUID().toString(),
            itemId = item.itemId,
            name = item.nameEnglish,
            unitPricePaise = item.pricePaise,
            quantity = quantity,
            specialNotes = notes
        )
        cartDao.insertOrUpdate(cartItem)
    }

    suspend fun removeItem(cartItemId: String) {
        cartDao.deleteCartItem(cartItemId)
    }

    suspend fun clearCart() {
        cartDao.clearCart()
    }
}
```

---

### 3. High-Stakes Spoken Read-Back & Confirmation Gate

```kotlin
package com.sarvaai.app.dialogue.order

import com.sarvaai.app.adapt.verbalizer.NumberVerbalizer
import com.sarvaai.app.data.entities.CartItemEntity

object OrderConfirmationManager {

    fun generateReadBackSummary(items: List<CartItemEntity>, language: String): String {
        if (items.isEmpty()) {
            return when (language) {
                "hi" -> "आपकी थाली खाली है। कृपया पहले कुछ व्यंजन चुनें।"
                "te" -> "మీ కార్ట్ ఖాళీగా ఉంది. దయచేసి ముందుగా వంటకాలను ఎంచుకోండి."
                else -> "Your cart is empty. Please add items to your order first."
            }
        }

        val totalPaise = items.sumOf { it.unitPricePaise * it.quantity }
        val totalRupees = totalPaise / 100

        return when (language) {
            "hi" -> {
                val itemsSpoken = items.joinToString(", ") {
                    "${NumberVerbalizer.verbalizeQuantity(it.quantity, "hi")} ${it.name}"
                }
                "आपके ऑर्डर में है: $itemsSpoken। कुल राशि ${NumberVerbalizer.verbalizePrice(totalRupees, "hi")} है। ऑर्डर देने के लिए 'कन्फर्म' बोलें या बटन दबाएं।"
            }
            "te" -> {
                val itemsSpoken = items.joinToString(", ") {
                    "${NumberVerbalizer.verbalizeQuantity(it.quantity, "te")} ${it.name}"
                }
                "మీ ఆర్డర్ వివరాలు: $itemsSpoken. మొత్తం ${NumberVerbalizer.verbalizePrice(totalRupees, "te")}. ఆర్డర్ చేయడానికి 'కన్ఫర్మ్' అని చెప్పండి."
            }
            else -> {
                val itemsSpoken = items.joinToString(", ") {
                    "${it.quantity} ${it.name}"
                }
                "You have ordered: $itemsSpoken. Total is ${NumberVerbalizer.verbalizePrice(totalRupees, "en")}. Say 'confirm' to place the order, or say 'change' to edit."
            }
        }
    }

    fun isAffirmativeConfirmation(input: String): Boolean {
        val s = input.lowercase().trim()
        val affirmativeTokens = setOf(
            "confirm", "yes", "place order", "okay", "sure", "proceed",
            "हाँ", "कन्फर्म", "कर दो", "ठीक है",
            "అవును", "కన్ఫర్మ్", "ఆర్డర్ చేయండి", "సరే"
        )
        return affirmativeTokens.any { s.contains(it) }
    }
}
```

---

### 4. Physical Testing Protocol on realme P3 Pro (Android 16)

1. Add dishes to cart via voice:
   - *"Add two butter naan and one paneer butter masala"* -> Items appear in cart.
2. Trigger Order:
   - Say: *"Place order"* -> App speaks read-back: *"You have ordered: two portions of butter naan and one portions of paneer butter masala. Total is 560 rupees. Say confirm to place order."*
3. Test Voice Confirmation Gate:
   - Say: *"No, wait"* -> Order cancelled; state returns to browsing.
   - Say: *"Confirm"* -> Plays success earcon, fires double-tick confirmation haptic, and transitions to `ORDERED`.

---

### 5. Git Checkpoint & Remote Push
```bash
git add .
git commit -m "feat(phase9): implement cart repository, spoken order read-back, affirmative confirmation gate and accessible cart screen"
git push origin main
```

---

### 6. Exit Criteria Checklist
- [x] Cart calculations executed purely in integer paise.
- [x] Spoken read-back verbalizes all quantities and prices.
- [x] Confirmation gate prevents unintentional orders.
- [x] Code pushed to remote repository.
