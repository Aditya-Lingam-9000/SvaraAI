package com.sarvaai.app.ui.screens.cart

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
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
import com.sarvaai.app.service.wakeword.WakeWordDetector
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
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val cartItems by cartDao.getCartItems().collectAsState(initial = emptyList())
    val isRecording by audioRecorder.isRecording.collectAsState()

    var isAiProcessing by remember { mutableStateOf(false) }
    var liveCaptionText by remember { mutableStateOf<String?>(null) }
    var splitCount by remember { mutableStateOf(1) }
    var confirmedOrderModal by remember { mutableStateOf<OrderAuditEntity?>(null) }

    // Clean back navigation without exiting app
    BackHandler {
        onStopNarration()
        if (audioRecorder.isRecording.value) {
            audioRecorder.stopRecording()
        }
        onClose()
    }

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
    val cgstRupees = cgstPaise / 100
    val sgstRupees = sgstPaise / 100
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

                        // Match against scanned menu items
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
                if (WakeWordDetector.isCloseCommand(recognizedText)) {
                    withContext(Dispatchers.Main) {
                        isAiProcessing = false
                        onStopNarration()
                        earconEngine.play(EarconType.LISTEN_STOP)
                        hapticEngine.trigger(HapticPattern.CONFIRMATION)
                        val goodbyeMessage = WakeWordDetector.getGoodbyeMessage(selectedLanguage)
                        onSpeakNarration(goodbyeMessage)
                        val closeIntent = Intent(WakeWordDetector.ACTION_CLOSE_APP).apply {
                            setPackage(context.packageName)
                        }
                        context.sendBroadcast(closeIntent)
                        onClose()
                    }
                    return@launch
                }

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
        color = NeoBackground
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
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        // Neomorphic Circular Back Button
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(NeoSurface)
                                .border(BorderStroke(1.dp, NeoBorder), CircleShape)
                                .shadow(2.dp, CircleShape, spotColor = NeoDarkShadow, ambientColor = NeoDarkShadow)
                                .clickable {
                                    hapticEngine.trigger(HapticPattern.CLICK)
                                    onStopNarration()
                                    onClose()
                                }
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back to previous screen",
                                tint = NeoTextPrimary,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Column {
                            Text(
                                text = "Voice Cart & Checkout",
                                fontSize = 19.sp,
                                fontWeight = FontWeight.Bold,
                                color = NeoTextPrimary,
                                modifier = Modifier.semantics { heading() }
                            )
                            Text(
                                text = "$restaurantName • ${cartItems.sumOf { it.quantity }} items",
                                fontSize = 12.sp,
                                color = NeoTextSecondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (isAudioPlaying) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .background(NonVegRedContainer)
                                    .border(BorderStroke(1.dp, NonVegRed.copy(alpha = 0.3f)), CircleShape)
                                    .clickable { onStopNarration() }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Stop,
                                    contentDescription = "Stop speech playback",
                                    tint = NonVegRed,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }

                        if (cartItems.isNotEmpty()) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .background(NeoSurface)
                                    .border(BorderStroke(1.dp, NeoBorder), CircleShape)
                                    .clickable {
                                        hapticEngine.trigger(HapticPattern.CLICK)
                                        scope.launch { cartDao.clearCart() }
                                    }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.DeleteOutline,
                                    contentDescription = "Clear cart",
                                    tint = NeoTextSecondary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }

                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(NeoSurface)
                                .border(BorderStroke(1.dp, NeoBorder), CircleShape)
                                .clickable {
                                    hapticEngine.trigger(HapticPattern.CLICK)
                                    onStopNarration()
                                    onClose()
                                }
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close cart screen",
                                tint = NeoTextPrimary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
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
                        Card(
                            shape = RoundedCornerShape(20.dp),
                            colors = CardDefaults.cardColors(containerColor = NeoSurface),
                            border = BorderStroke(1.dp, NeoBorder),
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                            modifier = Modifier
                                .fillMaxWidth(0.92f)
                                .padding(16.dp)
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.padding(24.dp)
                            ) {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier
                                        .size(64.dp)
                                        .clip(CircleShape)
                                        .background(NeoSurfacePressed)
                                        .border(BorderStroke(1.dp, NeoBorder), CircleShape)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.ShoppingCart,
                                        contentDescription = null,
                                        tint = NeoTextMuted,
                                        modifier = Modifier.size(32.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    text = "Your cart is empty",
                                    fontSize = 17.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = NeoTextPrimary
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "Tap the microphone below and say e.g. \"2 Masala Dosa, 1 Filter Coffee\"",
                                    fontSize = 13.sp,
                                    color = NeoTextSecondary,
                                    textAlign = TextAlign.Center,
                                    lineHeight = 18.sp
                                )
                            }
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
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = NeoSurface),
                                border = BorderStroke(1.dp, NeoBorder),
                                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .animateContentSize()
                                    .semantics(mergeDescendants = true) {
                                        contentDescription = "${item.name}, quantity ${item.quantity}, total ${item.totalPriceRupees} rupees."
                                    }
                            ) {
                                Column(modifier = Modifier.padding(14.dp)) {
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
                                                color = NeoTextPrimary
                                            )
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Text(
                                                text = "₹${item.unitPriceRupees} each • $unitPriceWords",
                                                fontSize = 12.sp,
                                                color = NeoTextSecondary,
                                                fontWeight = FontWeight.Medium
                                            )
                                        }

                                        // Neomorphic Quantity Stepper (+ / -)
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(24.dp))
                                                .background(NeoSurfacePressed)
                                                .border(BorderStroke(1.dp, NeoBorder), RoundedCornerShape(24.dp))
                                                .padding(horizontal = 4.dp, vertical = 3.dp)
                                        ) {
                                            // Minus Button
                                            Box(
                                                contentAlignment = Alignment.Center,
                                                modifier = Modifier
                                                    .size(32.dp)
                                                    .clip(CircleShape)
                                                    .background(NeoSurface)
                                                    .border(BorderStroke(1.dp, NeoBorder), CircleShape)
                                                    .clickable {
                                                        hapticEngine.trigger(HapticPattern.CLICK)
                                                        if (item.quantity > 1) {
                                                            scope.launch { cartDao.updateQuantity(item.cartItemId, item.quantity - 1) }
                                                        } else {
                                                            scope.launch { cartDao.deleteCartItem(item.cartItemId) }
                                                        }
                                                    }
                                            ) {
                                                Icon(
                                                    imageVector = if (item.quantity > 1) Icons.Default.Remove else Icons.Default.DeleteOutline,
                                                    contentDescription = if (item.quantity > 1) "Decrease quantity" else "Remove item",
                                                    tint = if (item.quantity > 1) NeoTextPrimary else NonVegRed,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }

                                            Text(
                                                text = "${item.quantity}",
                                                fontSize = 15.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = NeoTextPrimary,
                                                modifier = Modifier.padding(horizontal = 6.dp)
                                            )

                                            // Plus Button
                                            Box(
                                                contentAlignment = Alignment.Center,
                                                modifier = Modifier
                                                    .size(32.dp)
                                                    .clip(CircleShape)
                                                    .background(NeoPrimaryContainer)
                                                    .border(BorderStroke(1.dp, NeoPrimary.copy(alpha = 0.3f)), CircleShape)
                                                    .clickable {
                                                        hapticEngine.trigger(HapticPattern.CLICK)
                                                        scope.launch { cartDao.updateQuantity(item.cartItemId, item.quantity + 1) }
                                                    }
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Add,
                                                    contentDescription = "Increase quantity",
                                                    tint = NeoPrimary,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                        }
                                    }

                                    // Special Notes (if present)
                                    item.specialNotes?.let { notes ->
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(NeoSurfacePressed)
                                                .border(BorderStroke(1.dp, NeoBorder), RoundedCornerShape(8.dp))
                                                .padding(horizontal = 8.dp, vertical = 4.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.EditNote,
                                                contentDescription = null,
                                                tint = NeoPrimary,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = notes,
                                                fontSize = 12.sp,
                                                color = NeoTextSecondary,
                                                fontWeight = FontWeight.Medium
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Financial Breakdown & Bill Summary Card
                if (cartItems.isNotEmpty()) {
                    Card(
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = NeoSurface),
                        border = BorderStroke(1.dp, NeoBorder),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.ReceiptLong,
                                        contentDescription = null,
                                        tint = NeoPrimary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Bill Summary & Taxes",
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = NeoTextPrimary
                                    )
                                }
                                Text(
                                    text = "${cartItems.sumOf { it.quantity }} items",
                                    fontSize = 12.sp,
                                    color = NeoTextSecondary
                                )
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            Row(
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(text = "Subtotal", fontSize = 13.sp, color = NeoTextSecondary)
                                Text(text = "₹$subtotalRupees", fontSize = 13.sp, color = NeoTextPrimary, fontWeight = FontWeight.SemiBold)
                            }
                            Row(
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
                            ) {
                                Text(text = "CGST (2.5%)", fontSize = 12.sp, color = NeoTextSecondary)
                                Text(text = "₹$cgstRupees", fontSize = 12.sp, color = NeoTextPrimary)
                            }
                            Row(
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth().padding(top = 2.dp)
                            ) {
                                Text(text = "SGST (2.5%)", fontSize = 12.sp, color = NeoTextSecondary)
                                Text(text = "₹$sgstRupees", fontSize = 12.sp, color = NeoTextPrimary)
                            }

                            HorizontalDivider(color = NeoBorder, modifier = Modifier.padding(vertical = 8.dp))

                            Row(
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column {
                                    Text(text = "Grand Total", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = NeoTextPrimary)
                                    Text(text = totalVerbalized, fontSize = 11.sp, color = NeoPrimary, fontWeight = FontWeight.Medium)
                                }
                                Text(
                                    text = "₹$grandTotalRupees",
                                    fontSize = 22.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = NeoPrimary
                                )
                            }

                            // Quick Split Selector
                            Spacer(modifier = Modifier.height(10.dp))
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(
                                    imageVector = Icons.Default.People,
                                    contentDescription = null,
                                    tint = NeoTextSecondary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(text = "Quick Split:", fontSize = 12.sp, color = NeoTextSecondary, fontWeight = FontWeight.Medium)

                                listOf(2, 3, 4, 5).forEach { count ->
                                    val isSelected = splitCount == count
                                    Box(
                                        contentAlignment = Alignment.Center,
                                        modifier = Modifier
                                            .height(28.dp)
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(if (isSelected) NeoPrimary else NeoSurfacePressed)
                                            .border(BorderStroke(1.dp, if (isSelected) NeoPrimary else NeoBorder), RoundedCornerShape(6.dp))
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
                                            color = if (isSelected) NeoOnPrimary else NeoTextPrimary
                                        )
                                    }
                                }

                                if (splitCount > 1) {
                                    val perPerson = grandTotalRupees / splitCount
                                    Text(
                                        text = "= ₹$perPerson / person",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = NeoPrimary,
                                        modifier = Modifier.padding(start = 4.dp)
                                    )
                                }
                            }

                            // Smart Bill Splitter Button
                            Spacer(modifier = Modifier.height(10.dp))
                            Button(
                                onClick = {
                                    hapticEngine.trigger(HapticPattern.CLICK)
                                    onStopNarration()
                                    onOpenBillSplit()
                                },
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = NeoSurfacePressed,
                                    contentColor = NeoTextPrimary
                                ),
                                border = BorderStroke(1.dp, NeoBorder),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 44.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Calculate,
                                        contentDescription = null,
                                        tint = NeoPrimary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Split Bill & Calculate Taxes",
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Bottom Controls (Neomorphic Voice Mic + Place Order Button)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // Neomorphic Voice Mic Button (Accessible 60dp tap target)
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(60.dp)
                            .clip(CircleShape)
                            .background(
                                if (isRecording) NonVegRed
                                else if (isAiProcessing) NeoSurfacePressed
                                else NeoPrimaryContainer
                            )
                            .border(
                                BorderStroke(
                                    1.5.dp,
                                    if (isRecording) NonVegRed
                                    else if (isAiProcessing) NeoBorder
                                    else NeoPrimary.copy(alpha = 0.4f)
                                ),
                                CircleShape
                            )
                            .shadow(
                                elevation = if (isRecording) 6.dp else 2.dp,
                                shape = CircleShape,
                                spotColor = if (isRecording) NonVegRed.copy(alpha = 0.5f) else NeoDarkShadow,
                                ambientColor = NeoDarkShadow
                            )
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
                            CircularProgressIndicator(
                                color = NeoPrimary,
                                modifier = Modifier.size(26.dp),
                                strokeWidth = 2.5.dp
                            )
                        } else {
                            Icon(
                                imageVector = if (isRecording) Icons.Default.Stop else Icons.Default.Mic,
                                contentDescription = null,
                                tint = if (isRecording) NeoOnPrimary else NeoPrimary,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                    }

                    // Neomorphic Place Order Button
                    Button(
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
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = NeoPrimary,
                            contentColor = NeoOnPrimary
                        ),
                        elevation = ButtonDefaults.buttonElevation(
                            defaultElevation = 3.dp,
                            pressedElevation = 1.dp
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .height(60.dp)
                            .semantics(mergeDescendants = true) {
                                role = Role.Button
                            }
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = if (cartItems.isEmpty()) Icons.Default.Mic else Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = NeoOnPrimary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (cartItems.isEmpty()) "Add Items by Voice" else "Place Order (₹$grandTotalRupees)",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
            }

            // Confirmed Order Modal / Success Dialog
            confirmedOrderModal?.let { order ->
                AlertDialog(
                    onDismissRequest = { confirmedOrderModal = null },
                    containerColor = NeoSurface,
                    shape = RoundedCornerShape(20.dp),
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = VegGreen,
                                modifier = Modifier.size(26.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Order Confirmed",
                                fontSize = 19.sp,
                                fontWeight = FontWeight.Bold,
                                color = NeoTextPrimary
                            )
                        }
                    },
                    text = {
                        Column {
                            Text(
                                text = "Order ID: #${order.orderId}",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = NeoTextPrimary
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = order.itemsSummary,
                                fontSize = 13.sp,
                                color = NeoTextSecondary
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
                                text = "Your food is being prepared in the kitchen. You can now split the bill and calculate taxes.",
                                fontSize = 12.sp,
                                color = NeoTextSecondary
                            )
                        }
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                confirmedOrderModal = null
                                onOpenBillSplit()
                            },
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = NeoPrimary,
                                contentColor = NeoOnPrimary
                            )
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Calculate,
                                    contentDescription = null,
                                    tint = NeoOnPrimary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(text = "Split Bill & Taxes", fontWeight = FontWeight.Bold)
                            }
                        }
                    },
                    dismissButton = {
                        TextButton(
                            onClick = {
                                confirmedOrderModal = null
                                onClose()
                            }
                        ) {
                            Text(text = "Back to Menu", color = NeoTextSecondary, fontWeight = FontWeight.Medium)
                        }
                    }
                )
            }
        }
    }
}
