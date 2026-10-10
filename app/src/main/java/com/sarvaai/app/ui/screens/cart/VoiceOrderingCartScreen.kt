package com.sarvaai.app.ui.screens.cart

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sarvaai.app.adapt.verbalizer.NumberVerbalizer
import com.sarvaai.app.audio.earcons.EarconEngine
import com.sarvaai.app.audio.earcons.EarconType
import com.sarvaai.app.audio.haptics.HapticFeedbackEngine
import com.sarvaai.app.audio.haptics.HapticPattern
import com.sarvaai.app.audio.recorder.AudioRecorder
import com.sarvaai.app.bhashini.asr.AsrNmtClient
import com.sarvaai.app.core.result.Result
import com.sarvaai.app.data.dao.CartDao
import com.sarvaai.app.data.dao.MenuDao
import com.sarvaai.app.data.dao.OrderDao
import com.sarvaai.app.data.entities.CartItemEntity
import com.sarvaai.app.data.entities.OrderAuditEntity
import com.sarvaai.app.llm.GroqClient
import com.sarvaai.app.llm.models.StructuredAiResponse
import com.sarvaai.app.ui.components.AccessibleButton
import com.sarvaai.app.ui.components.LiveCaptionBar
import com.sarvaai.app.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

@Composable
fun VoiceOrderingCartScreen(
    cartDao: CartDao,
    orderDao: OrderDao,
    groqClient: GroqClient,
    audioRecorder: AudioRecorder,
    asrNmtClient: AsrNmtClient,
    hapticEngine: HapticFeedbackEngine,
    earconEngine: EarconEngine,
    selectedLanguage: String,
    menuDao: MenuDao? = null,
    restaurantName: String = "Sarva Bhavan Restaurant",
    isAudioPlaying: Boolean = false,
    onSpeakNarration: (String) -> Unit,
    onStopNarration: () -> Unit = {},
    onOpenBillSplit: () -> Unit = {},
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    val cartItems by cartDao.getCartItems().collectAsState(initial = emptyList())
    val isRecording by audioRecorder.isRecording.collectAsState()

    var isAiProcessing by remember { mutableStateOf(false) }
    var liveCaptionText by remember { mutableStateOf<String?>(null) }
    var splitCount by remember { mutableStateOf(1) }
    var confirmedOrderModal by remember { mutableStateOf<OrderAuditEntity?>(null) }

    DisposableEffect(Unit) {
        onDispose {
            onStopNarration()
            if (audioRecorder.isRecording.value) {
                audioRecorder.stopRecording()
            }
        }
    }

    // Live financial computations
    val subtotalPaise = cartItems.sumOf { it.totalPricePaise }
    val cgstPaise = (subtotalPaise * 25) / 1000 // 2.5% CGST
    val sgstPaise = (subtotalPaise * 25) / 1000 // 2.5% SGST
    val grandTotalPaise = subtotalPaise + cgstPaise + sgstPaise

    val subtotalRupees = subtotalPaise / 100
    val totalTaxRupees = (cgstPaise + sgstPaise) / 100
    val grandTotalRupees = grandTotalPaise / 100

    val totalVerbalized = NumberVerbalizer.verbalizePrice(grandTotalRupees, selectedLanguage)

    fun executeAiAction(aiResponse: StructuredAiResponse) {
        liveCaptionText = aiResponse.caption
        onSpeakNarration(aiResponse.spoken)

        for (action in aiResponse.actions) {
            val payload = action.payload.orEmpty()
            when (action.actionType) {
                "ADD_TO_CART" -> {
                    scope.launch {
                        val parts = payload.split(":")
                        val itemName = parts.getOrNull(0)?.trim()?.takeIf { it.isNotBlank() } ?: "Special Item"
                        val qty = parts.getOrNull(1)?.trim()?.toIntOrNull() ?: 1
                        val notes = parts.getOrNull(2)?.trim()?.takeIf { it.isNotBlank() }

                        // Check scanned menu items for matching price
                        val allMenuItems = try {
                            menuDao?.getAllItemsDirect() ?: emptyList()
                        } catch (e: Exception) {
                            emptyList()
                        }

                        val matchedMenu = allMenuItems.firstOrNull { m ->
                            m.nameEnglish.contains(itemName, ignoreCase = true) ||
                            itemName.contains(m.nameEnglish, ignoreCase = true) ||
                            (m.nameTelugu != null && (m.nameTelugu.contains(itemName, ignoreCase = true) || itemName.contains(m.nameTelugu, ignoreCase = true))) ||
                            (m.nameHindi != null && (m.nameHindi.contains(itemName, ignoreCase = true) || itemName.contains(m.nameHindi, ignoreCase = true)))
                        }

                        val finalName = matchedMenu?.nameEnglish ?: itemName
                        val finalPricePaise = matchedMenu?.pricePaise ?: 12000L

                        val existing = cartDao.getCartItemsDirect().firstOrNull {
                            it.name.equals(finalName, ignoreCase = true) || it.name.contains(itemName, ignoreCase = true)
                        }

                        if (existing != null) {
                            val mergedNotes = when {
                                notes != null && existing.specialNotes != null -> "${existing.specialNotes}, $notes"
                                notes != null -> notes
                                else -> existing.specialNotes
                            }
                            cartDao.updateQuantity(existing.cartItemId, existing.quantity + qty)
                            if (mergedNotes != existing.specialNotes) {
                                cartDao.insertOrUpdate(existing.copy(quantity = existing.quantity + qty, specialNotes = mergedNotes))
                            }
                        } else {
                            val newItem = CartItemEntity(
                                cartItemId = "cart_${UUID.randomUUID().toString().take(8)}",
                                itemId = matchedMenu?.itemId ?: "item_${UUID.randomUUID().toString().take(6)}",
                                name = finalName,
                                unitPricePaise = finalPricePaise,
                                quantity = qty,
                                specialNotes = notes
                            )
                            cartDao.insertOrUpdate(newItem)
                        }
                        hapticEngine.trigger(HapticPattern.CONFIRMATION)
                        earconEngine.play(EarconType.ORDER_CONFIRMED)
                    }
                }

                "REMOVE_FROM_CART" -> {
                    scope.launch {
                        val itemName = payload.trim()
                        val existing = cartDao.getCartItemsDirect().firstOrNull { it.name.contains(itemName, ignoreCase = true) }
                        if (existing != null) {
                            if (existing.quantity > 1) {
                                cartDao.updateQuantity(existing.cartItemId, existing.quantity - 1)
                            } else {
                                cartDao.deleteCartItem(existing.cartItemId)
                            }
                            hapticEngine.trigger(HapticPattern.CLICK)
                        }
                    }
                }

                "CLEAR_CART" -> {
                    scope.launch {
                        cartDao.clearCart()
                        hapticEngine.trigger(HapticPattern.CLICK)
                    }
                }

                "SPLIT_BILL" -> {
                    val count = payload.toIntOrNull() ?: 2
                    splitCount = count.coerceIn(1, 10)
                }

                "CONFIRM_ORDER" -> {
                    if (cartItems.isNotEmpty()) {
                        scope.launch {
                            val orderId = "ORD-${UUID.randomUUID().toString().take(6).uppercase()}"
                            val audit = OrderAuditEntity(
                                orderId = orderId,
                                timestamp = System.currentTimeMillis(),
                                itemsSummary = "${cartItems.size} items: ${cartItems.joinToString(", ") { "${it.name} x${it.quantity}" }}",
                                subtotalPaise = subtotalPaise,
                                cgstPaise = cgstPaise,
                                sgstPaise = sgstPaise,
                                serviceChargePaise = 0L,
                                totalPaise = grandTotalPaise,
                                status = "CONFIRMED"
                            )
                            orderDao.insertOrder(audit)
                            cartDao.clearCart()
                            hapticEngine.trigger(HapticPattern.CONFIRMATION)
                            earconEngine.play(EarconType.ORDER_CONFIRMED)
                            confirmedOrderModal = audit
                        }
                    }
                }
            }
        }
    }

    fun processCapturedSpeech(wavBytes: ByteArray) {
        if (wavBytes.isEmpty()) return
        isAiProcessing = true
        scope.launch(Dispatchers.IO) {
            val asrRes = asrNmtClient.transcribeAndTranslate(wavBytes, selectedLanguage)
            val recognizedText = if (asrRes is Result.Success) asrRes.data.originalTranscript.trim() else ""

            if (recognizedText.isNotBlank()) {
                val menuContext = cartItems.joinToString(", ") { "${it.name} (qty: ${it.quantity})" }
                val aiRes = groqClient.processSpeechIntent(recognizedText, selectedLanguage, menuContext)
                withContext(Dispatchers.Main) {
                    isAiProcessing = false
                    if (aiRes is Result.Success) {
                        executeAiAction(aiRes.data)
                    }
                }
            } else {
                withContext(Dispatchers.Main) {
                    isAiProcessing = false
                    hapticEngine.trigger(HapticPattern.ERROR_ALERT)
                    earconEngine.play(EarconType.ERROR_ALERT)
                }
            }
        }
    }

    fun startListening() {
        onStopNarration()
        hapticEngine.trigger(HapticPattern.SPEECH_TURN_CUE)
        earconEngine.play(EarconType.LISTEN_START)
        audioRecorder.startRecording(
            onUtteranceComplete = { wavBytes ->
                hapticEngine.trigger(HapticPattern.CONFIRMATION)
                earconEngine.play(EarconType.LISTEN_STOP)
                processCapturedSpeech(wavBytes)
            },
            onError = {
                isAiProcessing = false
                hapticEngine.trigger(HapticPattern.ERROR_ALERT)
                earconEngine.play(EarconType.ERROR_ALERT)
            }
        )
    }

    fun stopListeningManually() {
        if (isRecording) {
            hapticEngine.trigger(HapticPattern.CLICK)
            earconEngine.play(EarconType.LISTEN_STOP)
            audioRecorder.stopRecording()
        }
    }

    Surface(
        modifier = modifier.fillMaxSize(),
        color = CharcoalBackground
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp)
                    .systemBarsPadding()
            ) {
                // Top Header Bar
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp)
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Voice Cart & Checkout (Phase 7)",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = SaffronPrimary,
                            modifier = Modifier.semantics { heading() }
                        )
                        Text(
                            text = "$restaurantName • ${cartItems.sumOf { it.quantity }} items",
                            fontSize = 13.sp,
                            color = SubtitleText
                        )
                    }

                    if (isAudioPlaying) {
                        IconButton(
                            onClick = onStopNarration,
                            modifier = Modifier
                                .padding(end = 4.dp)
                                .size(36.dp)
                                .background(NonVegRed.copy(alpha = 0.2f), CircleShape)
                        ) {
                            Icon(imageVector = Icons.Default.Stop, contentDescription = "Stop speech", tint = NonVegRed)
                        }
                    }

                    IconButton(
                        onClick = {
                            hapticEngine.trigger(HapticPattern.CLICK)
                            onStopNarration()
                            onClose()
                        }
                    ) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "Close Cart", tint = PureWhite)
                    }
                }

                // Live Captions Bar
                LiveCaptionBar(
                    fullCaption = liveCaptionText ?: "",
                    isVisible = !liveCaptionText.isNullOrBlank(),
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                // Cart Items List
                if (cartItems.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Default.ShoppingCart,
                                contentDescription = null,
                                tint = CharcoalSurfaceVariant,
                                modifier = Modifier.size(64.dp)
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "Your order is empty",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = PureWhite
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Tap the mic below and say e.g. '2 Masala Dosa, 1 Filter Coffee'",
                                fontSize = 13.sp,
                                color = SubtitleText,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(horizontal = 24.dp)
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                    ) {
                        items(cartItems, key = { it.cartItemId }) { item ->
                            val unitPriceWords = NumberVerbalizer.verbalizePrice(item.unitPriceRupees, selectedLanguage)

                            Card(
                                colors = CardDefaults.cardColors(containerColor = CharcoalSurface),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .border(1.dp, Color(0xFF333333), RoundedCornerShape(12.dp))
                                    .semantics(mergeDescendants = true) {
                                        contentDescription = "${item.name}, quantity ${item.quantity}, total ${item.totalPriceRupees} rupees."
                                    }
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = item.name,
                                                fontSize = 16.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = PureWhite
                                            )
                                            Text(
                                                text = "₹${item.unitPriceRupees} each • $unitPriceWords",
                                                fontSize = 12.sp,
                                                color = SaffronPrimary
                                            )
                                        }

                                        // Stepper (+ / -)
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Box(
                                                contentAlignment = Alignment.Center,
                                                modifier = Modifier
                                                    .size(36.dp)
                                                    .clip(CircleShape)
                                                    .background(CharcoalSurfaceVariant)
                                                    .clickable {
                                                        hapticEngine.trigger(HapticPattern.CLICK)
                                                        if (item.quantity > 1) {
                                                            scope.launch { cartDao.updateQuantity(item.cartItemId, item.quantity - 1) }
                                                        } else {
                                                            scope.launch { cartDao.deleteCartItem(item.cartItemId) }
                                                        }
                                                    }
                                            ) {
                                                Icon(imageVector = Icons.Default.Remove, contentDescription = "Decrease quantity", tint = PureWhite, modifier = Modifier.size(18.dp))
                                            }

                                            Text(
                                                text = "${item.quantity}",
                                                fontSize = 16.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = PureWhite
                                            )

                                            Box(
                                                contentAlignment = Alignment.Center,
                                                modifier = Modifier
                                                    .size(36.dp)
                                                    .clip(CircleShape)
                                                    .background(SaffronPrimary)
                                                    .clickable {
                                                        hapticEngine.trigger(HapticPattern.CLICK)
                                                        scope.launch { cartDao.updateQuantity(item.cartItemId, item.quantity + 1) }
                                                    }
                                            ) {
                                                Icon(imageVector = Icons.Default.Add, contentDescription = "Increase quantity", tint = SaffronOnPrimary, modifier = Modifier.size(18.dp))
                                            }
                                        }
                                    }

                                    item.specialNotes?.let { notes ->
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = "📝 $notes",
                                            fontSize = 12.sp,
                                            color = HighContrastYellow,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Financial Breakdown & Bill Summary Card
                if (cartItems.isNotEmpty()) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = CharcoalSurface),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, Color(0xFF444444), RoundedCornerShape(12.dp))
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(text = "Subtotal", fontSize = 13.sp, color = SubtitleText)
                                Text(text = "₹$subtotalRupees", fontSize = 13.sp, color = PureWhite, fontWeight = FontWeight.SemiBold)
                            }
                            Row(
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
                            ) {
                                Text(text = "Taxes (5% GST)", fontSize = 13.sp, color = SubtitleText)
                                Text(text = "₹$totalTaxRupees", fontSize = 13.sp, color = PureWhite, fontWeight = FontWeight.SemiBold)
                            }

                            Divider(color = Color(0xFF444444), modifier = Modifier.padding(vertical = 8.dp))

                            Row(
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column {
                                    Text(text = "Grand Total", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = PureWhite)
                                    Text(text = totalVerbalized, fontSize = 11.sp, color = SaffronPrimary)
                                }
                                Text(
                                    text = "₹$grandTotalRupees",
                                    fontSize = 22.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = SaffronPrimary
                                )
                            }

                            // Smart Bill Splitter
                            Spacer(modifier = Modifier.height(10.dp))
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(text = "👥 Split:", fontSize = 12.sp, color = SubtitleText)
                                listOf(2, 3, 4, 5).forEach { count ->
                                    val isSelected = splitCount == count
                                    Box(
                                        contentAlignment = Alignment.Center,
                                        modifier = Modifier
                                            .height(28.dp)
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(if (isSelected) SaffronPrimary else CharcoalSurfaceVariant)
                                            .clickable {
                                                hapticEngine.trigger(HapticPattern.CLICK)
                                                splitCount = if (isSelected) 1 else count
                                                if (splitCount > 1) {
                                                    val perPerson = grandTotalRupees / splitCount
                                                    val perPersonWords = NumberVerbalizer.verbalizePrice(perPerson, selectedLanguage)
                                                    onSpeakNarration(
                                                        when (selectedLanguage) {
                                                            "te" -> "$splitCount మందికి ఒక్కొక్కరికి $perPersonWords అవుతుంది."
                                                            "hi" -> "$splitCount लोगों में प्रत्येक का हिस्सा $perPersonWords होगा।"
                                                            else -> "For $splitCount people, each person pays $perPersonWords."
                                                        }
                                                    )
                                                }
                                            }
                                            .padding(horizontal = 8.dp)
                                    ) {
                                        Text(
                                            text = "$count",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isSelected) SaffronOnPrimary else PureWhite
                                        )
                                    }
                                }

                                if (splitCount > 1) {
                                    val perPerson = grandTotalRupees / splitCount
                                    Text(
                                        text = "= ₹$perPerson / person",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = HighContrastYellow,
                                        modifier = Modifier.padding(start = 4.dp)
                                    )
                                }
                            }
                            // Smart Bill Splitter Button
                            Spacer(modifier = Modifier.height(10.dp))
                            AccessibleButton(
                                text = "💳 Split & Pay Bill via UPI (P11)",
                                onClick = {
                                    hapticEngine.trigger(HapticPattern.CLICK)
                                    onStopNarration()
                                    onOpenBillSplit()
                                },
                                modifier = Modifier.fillMaxWidth(),
                                isPrimary = false
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Bottom Controls (Voice Assistant Mic + Checkout Button)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // Voice Mic Button (Accessible 64dp+ tap target)
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(64.dp)
                            .clip(CircleShape)
                            .background(if (isRecording) NonVegRed else if (isAiProcessing) CharcoalSurfaceVariant else SaffronPrimary)
                            .clickable {
                                if (isRecording) {
                                    stopListeningManually()
                                } else {
                                    startListening()
                                }
                            }
                            .semantics {
                                contentDescription = if (isRecording) "Stop voice listening" else "Speak to add or change order"
                            }
                    ) {
                        if (isAiProcessing) {
                            CircularProgressIndicator(color = SaffronPrimary, modifier = Modifier.size(28.dp))
                        } else {
                            Icon(
                                imageVector = if (isRecording) Icons.Default.MicOff else Icons.Default.Mic,
                                contentDescription = null,
                                tint = if (isRecording) PureWhite else SaffronOnPrimary,
                                modifier = Modifier.size(32.dp)
                            )
                        }
                    }

                    // Place Order Button
                    AccessibleButton(
                        text = if (cartItems.isEmpty()) "Add Items by Voice" else "✅ Place Order (₹$grandTotalRupees)",
                        onClick = {
                            if (cartItems.isNotEmpty()) {
                                scope.launch {
                                    val orderId = "ORD-${UUID.randomUUID().toString().take(6).uppercase()}"
                                    val audit = OrderAuditEntity(
                                        orderId = orderId,
                                        timestamp = System.currentTimeMillis(),
                                        itemsSummary = "${cartItems.size} items: ${cartItems.joinToString(", ") { "${it.name} x${it.quantity}" }}",
                                        subtotalPaise = subtotalPaise,
                                        cgstPaise = cgstPaise,
                                        sgstPaise = sgstPaise,
                                        serviceChargePaise = 0L,
                                        totalPaise = grandTotalPaise,
                                        status = "CONFIRMED"
                                    )
                                    orderDao.insertOrder(audit)
                                    // Preserve cart items for bill calculation and splitting
                                    hapticEngine.trigger(HapticPattern.CONFIRMATION)
                                    earconEngine.play(EarconType.ORDER_CONFIRMED)
                                    val confirmMsg = when (selectedLanguage) {
                                        "te" -> "ఆర్డర్ $orderId విజయవంతంగా నమోదయింది. మొత్తం $totalVerbalized."
                                        "hi" -> "ऑर्डर $orderId सफलतापूर्वक दर्ज हो गया। कुल $totalVerbalized।"
                                        else -> "Order $orderId placed successfully for $totalVerbalized."
                                    }
                                    onSpeakNarration(confirmMsg)
                                    confirmedOrderModal = audit
                                }
                            } else {
                                startListening()
                            }
                        },
                        modifier = Modifier.weight(1f),
                        isPrimary = true
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))
            }

            // Confirmed Order Modal / Success Dialog
            confirmedOrderModal?.let { order ->
                AlertDialog(
                    onDismissRequest = { confirmedOrderModal = null },
                    containerColor = CharcoalSurface,
                    title = {
                        Text(
                            text = "🎉 Order Confirmed!",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = SaffronPrimary
                        )
                    },
                    text = {
                        Column {
                            Text(
                                text = "Order ID: #${order.orderId}",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = PureWhite
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = order.itemsSummary,
                                fontSize = 13.sp,
                                color = SubtitleText
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Total Bill: ₹${order.totalRupees}",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = VegGreen
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Your food is being prepared in the kitchen. You can now split the bill or pay via UPI!",
                                fontSize = 12.sp,
                                color = SubtitleText
                            )
                        }
                    },
                    confirmButton = {
                        AccessibleButton(
                            text = "💳 Split & Pay via UPI (P11)",
                            onClick = {
                                confirmedOrderModal = null
                                onOpenBillSplit()
                            },
                            isPrimary = true
                        )
                    },
                    dismissButton = {
                        TextButton(
                            onClick = {
                                confirmedOrderModal = null
                                onClose()
                            }
                        ) {
                            Text(text = "Back to Menu", color = PureWhite)
                        }
                    }
                )
            }
        }
    }
}
