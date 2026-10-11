package com.sarvaai.app.ui.screens.bill

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
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
import com.sarvaai.app.llm.GroqClient
import com.sarvaai.app.llm.models.StructuredAiResponse
import com.sarvaai.app.service.bill.BillCalculator
import com.sarvaai.app.service.bill.BillSpeechNarrator
import com.sarvaai.app.service.bill.DinerShare
import com.sarvaai.app.service.wakeword.WakeWordDetector
import com.sarvaai.app.ui.components.LiveCaptionBar
import com.sarvaai.app.ui.components.neomorphicSurface
import com.sarvaai.app.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

@Composable
fun SmartBillSplitScreen(
    cartDao: CartDao,
    hapticEngine: HapticFeedbackEngine,
    earconEngine: EarconEngine,
    selectedLanguage: String,
    menuDao: MenuDao? = null,
    orderDao: OrderDao? = null,
    groqClient: GroqClient? = null,
    audioRecorder: AudioRecorder? = null,
    asrNmtClient: AsrNmtClient? = null,
    restaurantName: String = "Sarva Bhavan Restaurant",
    isAudioPlaying: Boolean = false,
    onSpeakNarration: (String) -> Unit,
    onStopNarration: () -> Unit = {},
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val cartItems by cartDao.getCartItems().collectAsState(initial = emptyList())
    val isRecording = audioRecorder?.isRecording?.collectAsState()?.value ?: false

    var isAiProcessing by remember { mutableStateOf(false) }
    var liveCaptionText by remember { mutableStateOf<String?>(null) }
    var participantCount by remember { mutableStateOf(2) }
    var applyServiceCharge by remember { mutableStateOf(false) }
    var splitMode by remember { mutableStateOf("EQUAL") } // "EQUAL" or "ITEM"
    var selectedPersonForAssignment by remember { mutableStateOf(1) }
    var itemAssignments by remember { mutableStateOf<Map<Int, List<String>>>(emptyMap()) }
    var showManualAddDialog by remember { mutableStateOf(false) }

    // Safe BackHandler
    BackHandler {
        onStopNarration()
        if (isRecording) {
            audioRecorder?.stopRecording()
        }
        onClose()
    }

    DisposableEffect(Unit) {
        onDispose {
            onStopNarration()
            if (isRecording) {
                audioRecorder?.stopRecording()
            }
        }
    }

    val defaultSampleCartItems = remember {
        listOf(
            CartItemEntity(cartItemId = "sample_1", itemId = "item_1", name = "Masala Dosa", unitPricePaise = 9000L, quantity = 2),
            CartItemEntity(cartItemId = "sample_2", itemId = "item_2", name = "Paneer Butter Masala", unitPricePaise = 22000L, quantity = 1),
            CartItemEntity(cartItemId = "sample_3", itemId = "item_3", name = "Filter Coffee", unitPricePaise = 4000L, quantity = 2)
        )
    }
    val activeBillItems = if (cartItems.isNotEmpty()) cartItems else defaultSampleCartItems
    val isUsingSampleBill = cartItems.isEmpty()

    val itemTotalPaise = activeBillItems.sumOf { it.totalPricePaise }
    val billBreakdown = remember(itemTotalPaise, applyServiceCharge) {
        BillCalculator.calculateBill(itemTotalPaise, applyServiceCharge)
    }

    val equalShares = remember(billBreakdown.totalPaise, participantCount) {
        BillCalculator.splitEqually(billBreakdown.totalPaise, participantCount)
    }

    val itemShares = remember(activeBillItems, itemAssignments, participantCount, applyServiceCharge) {
        BillCalculator.splitByItemAssignment(activeBillItems, itemAssignments, participantCount, applyServiceCharge)
    }

    val displayedShares = if (splitMode == "EQUAL") equalShares else itemShares

    fun addItemToCart(name: String, pricePaise: Long, qty: Int = 1) {
        scope.launch {
            val existing = cartDao.getCartItemsDirect().firstOrNull { it.name.equals(name, ignoreCase = true) }
            if (existing != null) {
                cartDao.updateQuantity(existing.cartItemId, existing.quantity + qty)
            } else {
                val newItem = CartItemEntity(
                    cartItemId = "cart_${UUID.randomUUID().toString().take(8)}",
                    itemId = "item_${UUID.randomUUID().toString().take(6)}",
                    name = name,
                    unitPricePaise = pricePaise,
                    quantity = qty
                )
                cartDao.insertOrUpdate(newItem)
            }
            hapticEngine.trigger(HapticPattern.CONFIRMATION)
            earconEngine.play(EarconType.ORDER_CONFIRMED)
        }
    }

    fun executeAiAction(aiResponse: StructuredAiResponse) {
        liveCaptionText = aiResponse.caption
        onSpeakNarration(aiResponse.spoken)

        for (action in aiResponse.actions) {
            val payload = action.payload.orEmpty()
            when (action.actionType) {
                "ADD_TO_CART" -> {
                    val parts = payload.split(":")
                    val itemName = parts.getOrNull(0)?.trim()?.takeIf { it.isNotBlank() } ?: "Special Dish"
                    val qty = parts.getOrNull(1)?.trim()?.toIntOrNull() ?: 1

                    // Match scanned menu items for price
                    scope.launch {
                        val allMenuItems = try { menuDao?.getAllItemsDirect() ?: emptyList() } catch (e: Exception) { emptyList() }
                        val matched = allMenuItems.firstOrNull { it.nameEnglish.contains(itemName, ignoreCase = true) || itemName.contains(it.nameEnglish, ignoreCase = true) }
                        val price = matched?.pricePaise ?: 12000L
                        addItemToCart(matched?.nameEnglish ?: itemName, price, qty)
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
                    participantCount = count.coerceIn(1, 10)
                }
            }
        }
    }

    fun startListening() {
        if (audioRecorder == null || asrNmtClient == null || groqClient == null) return
        onStopNarration()
        hapticEngine.trigger(HapticPattern.SPEECH_TURN_CUE)
        earconEngine.play(EarconType.LISTEN_START)
        audioRecorder.startRecording(
            onUtteranceComplete = { wavBytes ->
                hapticEngine.trigger(HapticPattern.CONFIRMATION)
                earconEngine.play(EarconType.LISTEN_STOP)
                if (wavBytes.isNotEmpty()) {
                    isAiProcessing = true
                    scope.launch(Dispatchers.IO) {
                        val asrRes = asrNmtClient.transcribeAndTranslate(wavBytes, selectedLanguage)
                        val text = if (asrRes is Result.Success) asrRes.data.originalTranscript.trim() else ""
                        if (text.isNotBlank()) {
                            if (WakeWordDetector.isCloseCommand(text)) {
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

                            val contextSummary = cartItems.joinToString(", ") { "${it.name} x${it.quantity}" }
                            val aiRes = groqClient.processSpeechIntent(text, selectedLanguage, contextSummary)
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
                            }
                        }
                    }
                }
            },
            onError = {
                isAiProcessing = false
                hapticEngine.trigger(HapticPattern.ERROR_ALERT)
            }
        )
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
            ) {
                // ============================================================
                // 1. TOP HEADER BAR
                // ============================================================
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
                        val backInteraction = remember { MutableInteractionSource() }
                        val isBackPressed by backInteraction.collectIsPressedAsState()
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(42.dp)
                                .neomorphicSurface(
                                    shape = CircleShape,
                                    surfaceColor = if (isBackPressed) NeoSurfacePressed else NeoSurface,
                                    elevation = 4.dp,
                                    isPressed = isBackPressed,
                                    cornerRadius = 21.dp
                                )
                                .clickable(
                                    interactionSource = backInteraction,
                                    indication = null,
                                    onClick = {
                                        hapticEngine.trigger(HapticPattern.CLICK)
                                        onStopNarration()
                                        onClose()
                                    }
                                )
                                .semantics {
                                    contentDescription = "Back to previous screen"
                                    role = Role.Button
                                }
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = null,
                                tint = NeoTextPrimary,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Column {
                            Text(
                                text = "Bill & Split",
                                fontSize = 19.sp,
                                fontWeight = FontWeight.Bold,
                                color = NeoTextPrimary,
                                modifier = Modifier.semantics { heading() }
                            )
                            Text(
                                text = "${cartItems.sumOf { it.quantity }} items",
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
                            val stopInteraction = remember { MutableInteractionSource() }
                            val isStopPressed by stopInteraction.collectIsPressedAsState()
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .size(40.dp)
                                    .neomorphicSurface(
                                        shape = CircleShape,
                                        surfaceColor = if (isStopPressed) NonVegRedContainer.copy(alpha = 0.8f) else NonVegRedContainer,
                                        borderColor = NonVegRed.copy(alpha = 0.4f),
                                        elevation = 3.dp,
                                        isPressed = isStopPressed,
                                        cornerRadius = 20.dp
                                    )
                                    .clickable(
                                        interactionSource = stopInteraction,
                                        indication = null,
                                        onClick = { onStopNarration() }
                                    )
                                    .semantics {
                                        contentDescription = "Stop speech narration"
                                        role = Role.Button
                                    }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Stop,
                                    contentDescription = null,
                                    tint = NonVegRed,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }

                        val closeInteraction = remember { MutableInteractionSource() }
                        val isClosePressed by closeInteraction.collectIsPressedAsState()
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(40.dp)
                                .neomorphicSurface(
                                    shape = CircleShape,
                                    surfaceColor = if (isClosePressed) NeoSurfacePressed else NeoSurface,
                                    elevation = 4.dp,
                                    isPressed = isClosePressed,
                                    cornerRadius = 20.dp
                                )
                                .clickable(
                                    interactionSource = closeInteraction,
                                    indication = null,
                                    onClick = {
                                        hapticEngine.trigger(HapticPattern.CLICK)
                                        onStopNarration()
                                        onClose()
                                    }
                                )
                                .semantics {
                                    contentDescription = "Close Bill Screen"
                                    role = Role.Button
                                }
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = null,
                                tint = NeoTextPrimary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }

                // ============================================================
                // 2. LIVE CAPTION BAR
                // ============================================================
                LiveCaptionBar(
                    fullCaption = liveCaptionText ?: "",
                    isVisible = !liveCaptionText.isNullOrBlank(),
                    modifier = Modifier.padding(bottom = 6.dp)
                )

                // ============================================================
                // 3. BILL BREAKDOWN CARD
                // ============================================================
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .neomorphicSurface(
                            shape = RoundedCornerShape(20.dp),
                            surfaceColor = NeoSurface,
                            elevation = 5.dp,
                            cornerRadius = 20.dp
                        )
                        .padding(15.dp)
                        .animateContentSize()
                ) {
                    Column {
                        // Title row
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier
                                        .size(32.dp)
                                        .clip(CircleShape)
                                        .background(NeoPrimaryContainer)
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.ReceiptLong,
                                        contentDescription = null,
                                        tint = NeoPrimary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Bill Breakdown",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = NeoTextPrimary
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(NeoSurfacePressed)
                                    .padding(horizontal = 8.dp, vertical = 3.dp)
                            ) {
                                Text(
                                    text = "${cartItems.sumOf { it.quantity }} items",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = NeoTextSecondary
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Subtotal line
                        Row(
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(text = "Subtotal", fontSize = 13.sp, color = NeoTextSecondary)
                            Text(
                                text = "₹${billBreakdown.subtotalRupees}",
                                fontSize = 14.sp,
                                color = NeoTextPrimary,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        Spacer(modifier = Modifier.height(5.dp))

                        // CGST (2.5%) line
                        Row(
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(text = "CGST (2.5%)", fontSize = 12.sp, color = NeoTextSecondary)
                            Text(
                                text = "₹${billBreakdown.cgstRupees}",
                                fontSize = 13.sp,
                                color = NeoTextPrimary,
                                fontWeight = FontWeight.Medium
                            )
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        // SGST (2.5%) line
                        Row(
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(text = "SGST (2.5%)", fontSize = 12.sp, color = NeoTextSecondary)
                            Text(
                                text = "₹${billBreakdown.sgstRupees}",
                                fontSize = 13.sp,
                                color = NeoTextPrimary,
                                fontWeight = FontWeight.Medium
                            )
                        }

                        // Service Charge (5%) animated line
                        AnimatedVisibility(
                            visible = applyServiceCharge,
                            enter = fadeIn() + expandVertically(),
                            exit = fadeOut() + shrinkVertically()
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 4.dp)
                            ) {
                                Text(text = "Service Charge (5%)", fontSize = 12.sp, color = NeoTextSecondary)
                                Text(
                                    text = "₹${billBreakdown.serviceChargeRupees}",
                                    fontSize = 13.sp,
                                    color = NeoTextPrimary,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }

                        // Service charge toggle
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable {
                                    hapticEngine.trigger(HapticPattern.CLICK)
                                    applyServiceCharge = !applyServiceCharge
                                }
                                .padding(vertical = 2.dp)
                        ) {
                            Checkbox(
                                checked = applyServiceCharge,
                                onCheckedChange = {
                                    hapticEngine.trigger(HapticPattern.CLICK)
                                    applyServiceCharge = it
                                },
                                colors = CheckboxDefaults.colors(
                                    checkedColor = NeoPrimary,
                                    checkmarkColor = NeoOnPrimary,
                                    uncheckedColor = NeoTextSecondary
                                ),
                                modifier = Modifier.size(32.dp).padding(end = 4.dp)
                            )
                            Text(
                                text = "Include 5% Restaurant Service Charge",
                                fontSize = 12.sp,
                                color = NeoTextPrimary,
                                fontWeight = FontWeight.Medium
                            )
                        }

                        HorizontalDivider(
                            color = NeoBorder,
                            thickness = 1.dp,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )

                        // Grand Total Row with bold NeoPrimary and native price verbalization
                        Row(
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column {
                                Text(
                                    text = "Grand Total",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = NeoTextPrimary
                                )
                                Text(
                                    text = NumberVerbalizer.verbalizePrice(billBreakdown.totalRupees, selectedLanguage),
                                    fontSize = 12.sp,
                                    color = NeoPrimary,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                            Text(
                                text = "₹${billBreakdown.totalRupees}",
                                fontSize = 26.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = NeoPrimary
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // ============================================================
                        // PROMINENT SPLIT BILL HIGHLIGHT BOX
                        // ============================================================
                        val perPersonShareRupees = remember(billBreakdown.totalRupees, participantCount) {
                            (billBreakdown.totalRupees + participantCount - 1) / participantCount.coerceAtLeast(1)
                        }

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(NeoPrimaryContainer)
                                .border(1.5.dp, NeoPrimary.copy(alpha = 0.5f), RoundedCornerShape(14.dp))
                                .padding(12.dp)
                        ) {
                            Column {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.Default.Groups,
                                            contentDescription = null,
                                            tint = NeoPrimary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = if (splitMode == "EQUAL") "SPLIT BILL ($participantCount DINERS)" else "ITEMIZED SPLIT ($participantCount DINERS)",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = NeoPrimary,
                                            letterSpacing = 0.5.sp
                                        )
                                    }
                                    if (isUsingSampleBill) {
                                        Text(
                                            text = "Sample Bill",
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = NeoTextSecondary,
                                            modifier = Modifier
                                                .background(NeoSurface, RoundedCornerShape(8.dp))
                                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(6.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        if (splitMode == "EQUAL") {
                                            Text(
                                                text = "₹$perPersonShareRupees / person",
                                                fontSize = 24.sp,
                                                fontWeight = FontWeight.ExtraBold,
                                                color = NeoTextPrimary
                                            )
                                            Text(
                                                text = "Total ₹${billBreakdown.totalRupees} ÷ $participantCount diners = ₹$perPersonShareRupees each",
                                                fontSize = 11.sp,
                                                color = NeoTextSecondary,
                                                fontWeight = FontWeight.Medium
                                            )
                                        } else {
                                            Text(
                                                text = "Custom per Diner",
                                                fontSize = 20.sp,
                                                fontWeight = FontWeight.ExtraBold,
                                                color = NeoTextPrimary
                                            )
                                            Text(
                                                text = "Assigned by individual dish consumption below",
                                                fontSize = 11.sp,
                                                color = NeoTextSecondary,
                                                fontWeight = FontWeight.Medium
                                            )
                                        }
                                    }

                                    // Spoken Split Button
                                    IconButton(
                                        onClick = {
                                            hapticEngine.trigger(HapticPattern.CONFIRMATION)
                                            earconEngine.play(EarconType.ORDER_CONFIRMED)
                                            val splitNarration = if (splitMode == "EQUAL") {
                                                val wordShare = NumberVerbalizer.verbalizePrice(perPersonShareRupees, selectedLanguage)
                                                "For $participantCount diners, each person pays $perPersonShareRupees rupees. Total bill is ${billBreakdown.totalRupees} rupees. $wordShare"
                                            } else {
                                                "Itemized bill split across $participantCount diners. Total bill is ${billBreakdown.totalRupees} rupees."
                                            }
                                            onSpeakNarration(splitNarration)
                                        },
                                        modifier = Modifier
                                            .size(38.dp)
                                            .clip(CircleShape)
                                            .background(NeoSurface)
                                            .border(1.dp, NeoPrimary.copy(alpha = 0.3f), CircleShape)
                                    ) {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                                            contentDescription = "Hear split bill",
                                            tint = NeoPrimary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Spoken breakdown audio button styled with neomorphic bevels
                        val hearInteractionSource = remember { MutableInteractionSource() }
                        val isHearPressed by hearInteractionSource.collectIsPressedAsState()
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(44.dp)
                                .neomorphicSurface(
                                    shape = RoundedCornerShape(14.dp),
                                    surfaceColor = if (isHearPressed) NeoSurfacePressed else NeoSurface,
                                    elevation = 3.dp,
                                    isPressed = isHearPressed,
                                    cornerRadius = 14.dp
                                )
                                .clickable(
                                    interactionSource = hearInteractionSource,
                                    indication = null,
                                    onClick = {
                                        hapticEngine.trigger(HapticPattern.CONFIRMATION)
                                        earconEngine.play(EarconType.ORDER_CONFIRMED)
                                        val summary = BillSpeechNarrator.narrateBillSummary(billBreakdown, selectedLanguage)
                                        onSpeakNarration(summary)
                                    }
                                )
                                .semantics {
                                    contentDescription = "Hear spoken bill breakdown"
                                    role = Role.Button
                                }
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                                    contentDescription = null,
                                    tint = NeoPrimary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Hear Spoken Bill Breakdown",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = NeoTextPrimary
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // ============================================================
                // 4. QUICK DISH ADDITION CHIPS (Compact pills with + and price)
                // ============================================================
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 2.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(end = 2.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.RestaurantMenu,
                            contentDescription = null,
                            tint = NeoTextSecondary,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Quick Add:",
                            fontSize = 11.sp,
                            color = NeoTextSecondary,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        val sampleDishes = listOf(
                            "Masala Dosa" to 8000L,
                            "Idli Sambar" to 6000L,
                            "Filter Coffee" to 4000L,
                            "Paneer Butter Masala" to 24000L,
                            "Veg Biryani" to 18000L
                        )
                        items(sampleDishes) { (name, price) ->
                            val chipInteraction = remember { MutableInteractionSource() }
                            val isChipPressed by chipInteraction.collectIsPressedAsState()
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .height(32.dp)
                                    .neomorphicSurface(
                                        shape = RoundedCornerShape(16.dp),
                                        surfaceColor = if (isChipPressed) NeoSurfacePressed else NeoSurface,
                                        elevation = 3.dp,
                                        isPressed = isChipPressed,
                                        cornerRadius = 16.dp
                                    )
                                    .clickable(
                                        interactionSource = chipInteraction,
                                        indication = null,
                                        onClick = {
                                            addItemToCart(name, price, 1)
                                        }
                                    )
                                    .padding(horizontal = 10.dp)
                                    .semantics {
                                        contentDescription = "Add $name for ₹${price / 100}"
                                        role = Role.Button
                                    }
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Add,
                                        contentDescription = null,
                                        tint = NeoPrimary,
                                        modifier = Modifier.size(13.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "$name (₹${price / 100})",
                                        fontSize = 11.sp,
                                        color = NeoTextPrimary,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        }
                    }

                    // Manual custom add button
                    val customAddInteraction = remember { MutableInteractionSource() }
                    val isAddPressed by customAddInteraction.collectIsPressedAsState()
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(34.dp)
                            .neomorphicSurface(
                                shape = CircleShape,
                                surfaceColor = if (isAddPressed) Color(0xFF1D4ED8) else NeoPrimary,
                                lightColor = Color(0x6693C5FD),
                                darkColor = Color(0x661E3A8A),
                                borderColor = Color(0xFF3B82F6),
                                elevation = 4.dp,
                                isPressed = isAddPressed,
                                cornerRadius = 17.dp
                            )
                            .clickable(
                                interactionSource = customAddInteraction,
                                indication = null,
                                onClick = {
                                    hapticEngine.trigger(HapticPattern.CLICK)
                                    showManualAddDialog = true
                                }
                            )
                            .semantics {
                                contentDescription = "Add custom item to bill"
                                role = Role.Button
                            }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = null,
                            tint = NeoOnPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // ============================================================
                // 5. SEGMENTED SPLIT MODE SELECTOR & DINERS STEPPER
                // ============================================================
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // Segmented Split Mode Selector (Rounded capsule pills)
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp)
                            .neomorphicSurface(
                                shape = RoundedCornerShape(20.dp),
                                surfaceColor = NeoSurfacePressed,
                                borderColor = NeoBorder,
                                elevation = 2.dp,
                                isPressed = true,
                                cornerRadius = 20.dp
                            )
                            .padding(3.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Equal Split Tab
                        val isEqualSelected = splitMode == "EQUAL"
                        val equalInteraction = remember { MutableInteractionSource() }
                        val isEqualPressed by equalInteraction.collectIsPressedAsState()
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .neomorphicSurface(
                                    shape = RoundedCornerShape(18.dp),
                                    surfaceColor = if (isEqualSelected) NeoPrimary else NeoSurface,
                                    lightColor = if (isEqualSelected) Color(0x6693C5FD) else NeoLightShadow,
                                    darkColor = if (isEqualSelected) Color(0x661E3A8A) else NeoDarkShadow,
                                    borderColor = if (isEqualSelected) NeoPrimary else NeoBorder,
                                    elevation = if (isEqualSelected) 2.dp else 1.dp,
                                    isPressed = isEqualPressed,
                                    cornerRadius = 18.dp
                                )
                                .clickable(
                                    interactionSource = equalInteraction,
                                    indication = null,
                                    onClick = {
                                        hapticEngine.trigger(HapticPattern.CLICK)
                                        splitMode = "EQUAL"
                                    }
                                )
                                .semantics {
                                    contentDescription = "Equal Split mode"
                                    role = Role.Tab
                                }
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Groups,
                                    contentDescription = null,
                                    tint = if (isEqualSelected) NeoOnPrimary else NeoTextSecondary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Equal Split",
                                    fontSize = 12.sp,
                                    fontWeight = if (isEqualSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isEqualSelected) NeoOnPrimary else NeoTextSecondary
                                )
                            }
                        }

                        // By Items Tab
                        val isItemSelected = splitMode == "ITEM"
                        val itemInteraction = remember { MutableInteractionSource() }
                        val isItemPressed by itemInteraction.collectIsPressedAsState()
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .neomorphicSurface(
                                    shape = RoundedCornerShape(18.dp),
                                    surfaceColor = if (isItemSelected) NeoPrimary else NeoSurface,
                                    lightColor = if (isItemSelected) Color(0x6693C5FD) else NeoLightShadow,
                                    darkColor = if (isItemSelected) Color(0x661E3A8A) else NeoDarkShadow,
                                    borderColor = if (isItemSelected) NeoPrimary else NeoBorder,
                                    elevation = if (isItemSelected) 2.dp else 1.dp,
                                    isPressed = isItemPressed,
                                    cornerRadius = 18.dp
                                )
                                .clickable(
                                    interactionSource = itemInteraction,
                                    indication = null,
                                    onClick = {
                                        hapticEngine.trigger(HapticPattern.CLICK)
                                        splitMode = "ITEM"
                                    }
                                )
                                .semantics {
                                    contentDescription = "By Items Split mode"
                                    role = Role.Tab
                                }
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.RestaurantMenu,
                                    contentDescription = null,
                                    tint = if (isItemSelected) NeoOnPrimary else NeoTextSecondary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "By Items",
                                    fontSize = 12.sp,
                                    fontWeight = if (isItemSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isItemSelected) NeoOnPrimary else NeoTextSecondary
                                )
                            }
                        }
                    }

                    // Diners Stepper
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier
                            .height(44.dp)
                            .neomorphicSurface(
                                shape = RoundedCornerShape(18.dp),
                                surfaceColor = NeoSurface,
                                elevation = 3.dp,
                                cornerRadius = 18.dp
                            )
                            .padding(horizontal = 6.dp)
                    ) {
                        // Circular '-' button
                        val decInteraction = remember { MutableInteractionSource() }
                        val isDecPressed by decInteraction.collectIsPressedAsState()
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(30.dp)
                                .neomorphicSurface(
                                    shape = CircleShape,
                                    surfaceColor = if (isDecPressed) NeoSurfacePressed else NeoSurface,
                                    elevation = 2.dp,
                                    isPressed = isDecPressed || participantCount <= 1,
                                    cornerRadius = 15.dp
                                )
                                .clickable(
                                    enabled = participantCount > 1,
                                    interactionSource = decInteraction,
                                    indication = null,
                                    onClick = {
                                        if (participantCount > 1) {
                                            hapticEngine.trigger(HapticPattern.CLICK)
                                            participantCount -= 1
                                        }
                                    }
                                )
                                .semantics {
                                    contentDescription = "Decrease Diners count"
                                    role = Role.Button
                                }
                        ) {
                            Icon(
                                imageVector = Icons.Default.Remove,
                                contentDescription = null,
                                tint = if (participantCount > 1) NeoTextPrimary else NeoTextMuted,
                                modifier = Modifier.size(16.dp)
                            )
                        }

                        // Clean Diner count badge
                        Text(
                            text = "$participantCount Diners",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = NeoTextPrimary,
                            modifier = Modifier.padding(horizontal = 4.dp)
                        )

                        // Circular '+' button
                        val incInteraction = remember { MutableInteractionSource() }
                        val isIncPressed by incInteraction.collectIsPressedAsState()
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(30.dp)
                                .neomorphicSurface(
                                    shape = CircleShape,
                                    surfaceColor = if (isIncPressed) Color(0xFF1D4ED8) else NeoPrimary,
                                    lightColor = Color(0x6693C5FD),
                                    darkColor = Color(0x661E3A8A),
                                    borderColor = Color(0xFF3B82F6),
                                    elevation = 3.dp,
                                    isPressed = isIncPressed || participantCount >= 10,
                                    cornerRadius = 15.dp
                                )
                                .clickable(
                                    enabled = participantCount < 10,
                                    interactionSource = incInteraction,
                                    indication = null,
                                    onClick = {
                                        if (participantCount < 10) {
                                            hapticEngine.trigger(HapticPattern.CLICK)
                                            participantCount += 1
                                        }
                                    }
                                )
                                .semantics {
                                    contentDescription = "Increase Diners count"
                                    role = Role.Button
                                }
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = null,
                                tint = NeoOnPrimary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // ============================================================
                // 6. DISHES LIST OR ITEM-BY-ITEM ASSIGNMENT & DINER SHARES
                // ============================================================
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    if (isUsingSampleBill) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(NeoSurfacePressed)
                                    .padding(horizontal = 12.dp, vertical = 8.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Info,
                                        contentDescription = null,
                                        tint = NeoPrimary,
                                        modifier = Modifier.size(15.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Sample Bill Loaded: Masala Dosa (x2), Paneer Butter Masala, Filter Coffee (x2). Add dishes anytime.",
                                        fontSize = 11.sp,
                                        color = NeoTextSecondary,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        }
                    }

                    // In Item split mode: show assignment selector
                    if (splitMode == "ITEM") {
                            item {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .neomorphicSurface(
                                            shape = RoundedCornerShape(18.dp),
                                            surfaceColor = NeoSurface,
                                            elevation = 4.dp,
                                            cornerRadius = 18.dp
                                        )
                                        .padding(14.dp)
                                        .animateContentSize()
                                ) {
                                    Column {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                imageVector = Icons.Default.Person,
                                                contentDescription = null,
                                                tint = NeoPrimary,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = "Assign dishes to diner:",
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = NeoTextPrimary
                                            )
                                        }

                                        Spacer(modifier = Modifier.height(8.dp))

                                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                            items((1..participantCount).toList()) { pIndex ->
                                                val isSelected = selectedPersonForAssignment == pIndex
                                                val personInteraction = remember { MutableInteractionSource() }
                                                val isPersonPressed by personInteraction.collectIsPressedAsState()
                                                Box(
                                                    contentAlignment = Alignment.Center,
                                                    modifier = Modifier
                                                        .height(34.dp)
                                                        .neomorphicSurface(
                                                            shape = RoundedCornerShape(12.dp),
                                                            surfaceColor = if (isSelected) NeoPrimary else if (isPersonPressed) NeoSurfacePressed else NeoSurface,
                                                            lightColor = if (isSelected) Color(0x6693C5FD) else NeoLightShadow,
                                                            darkColor = if (isSelected) Color(0x661E3A8A) else NeoDarkShadow,
                                                            borderColor = if (isSelected) Color(0xFF3B82F6) else NeoBorder,
                                                            elevation = if (isSelected) 3.dp else 2.dp,
                                                            isPressed = isSelected || isPersonPressed,
                                                            cornerRadius = 12.dp
                                                        )
                                                        .clickable(
                                                            interactionSource = personInteraction,
                                                            indication = null,
                                                            onClick = {
                                                                hapticEngine.trigger(HapticPattern.CLICK)
                                                                selectedPersonForAssignment = pIndex
                                                            }
                                                        )
                                                        .padding(horizontal = 12.dp)
                                                ) {
                                                    Text(
                                                        text = "Person $pIndex",
                                                        fontSize = 12.sp,
                                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                                        color = if (isSelected) NeoOnPrimary else NeoTextPrimary
                                                    )
                                                }
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(10.dp))

                                        activeBillItems.forEach { item ->
                                            val currentAssigned = itemAssignments[selectedPersonForAssignment] ?: emptyList()
                                            val isChecked = currentAssigned.contains(item.cartItemId)

                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clip(RoundedCornerShape(8.dp))
                                                    .clickable {
                                                        hapticEngine.trigger(HapticPattern.CLICK)
                                                        val updated = if (isChecked) currentAssigned - item.cartItemId else currentAssigned + item.cartItemId
                                                        itemAssignments = itemAssignments + (selectedPersonForAssignment to updated)
                                                    }
                                                    .padding(vertical = 4.dp, horizontal = 4.dp)
                                            ) {
                                                Checkbox(
                                                    checked = isChecked,
                                                    onCheckedChange = { checked ->
                                                        hapticEngine.trigger(HapticPattern.CLICK)
                                                        val updated = if (checked) currentAssigned + item.cartItemId else currentAssigned - item.cartItemId
                                                        itemAssignments = itemAssignments + (selectedPersonForAssignment to updated)
                                                    },
                                                    colors = CheckboxDefaults.colors(
                                                        checkedColor = NeoPrimary,
                                                        checkmarkColor = NeoOnPrimary,
                                                        uncheckedColor = NeoTextSecondary
                                                    )
                                                )
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text(
                                                    text = "${item.name} (₹${item.totalPriceRupees})",
                                                    fontSize = 13.sp,
                                                    color = NeoTextPrimary,
                                                    fontWeight = FontWeight.Medium
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // Participant Shares Cards
                        items(displayedShares, key = { it.participantIndex }) { share ->
                            val shareWords = NumberVerbalizer.verbalizePrice(share.shareRupees, selectedLanguage)

                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .neomorphicSurface(
                                        shape = RoundedCornerShape(18.dp),
                                        surfaceColor = NeoSurface,
                                        elevation = 4.dp,
                                        cornerRadius = 18.dp
                                    )
                                    .padding(14.dp)
                                    .animateContentSize()
                                    .semantics(mergeDescendants = true) {
                                        contentDescription = "${share.label}, share ${share.shareRupees} rupees ($shareWords)."
                                    }
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        // Person avatar circle
                                        Box(
                                            contentAlignment = Alignment.Center,
                                            modifier = Modifier
                                                .size(42.dp)
                                                .neomorphicSurface(
                                                    shape = CircleShape,
                                                    surfaceColor = NeoPrimaryContainer,
                                                    borderColor = NeoPrimary.copy(alpha = 0.3f),
                                                    elevation = 2.dp,
                                                    cornerRadius = 21.dp
                                                )
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Person,
                                                contentDescription = null,
                                                tint = NeoPrimary,
                                                modifier = Modifier.size(22.dp)
                                            )
                                        }

                                        Spacer(modifier = Modifier.width(12.dp))

                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = share.label,
                                                fontSize = 15.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = NeoTextPrimary
                                            )
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Text(
                                                text = "₹${share.shareRupees}",
                                                fontSize = 16.sp,
                                                fontWeight = FontWeight.ExtraBold,
                                                color = NeoPrimary
                                            )
                                            Text(
                                                text = shareWords,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Medium,
                                                color = NeoTextSecondary,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            if (share.assignedItems.isNotEmpty()) {
                                                Spacer(modifier = Modifier.height(2.dp))
                                                Text(
                                                    text = "Dishes: ${share.assignedItems.joinToString(", ") { it.name }}",
                                                    fontSize = 11.sp,
                                                    color = NeoTextMuted,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                        }
                                    }

                                    Spacer(modifier = Modifier.width(8.dp))

                                    // Speaker button with neomorphic bevel
                                    val speakerInteraction = remember { MutableInteractionSource() }
                                    val isSpeakerPressed by speakerInteraction.collectIsPressedAsState()
                                    Box(
                                        contentAlignment = Alignment.Center,
                                        modifier = Modifier
                                            .size(38.dp)
                                            .neomorphicSurface(
                                                shape = CircleShape,
                                                surfaceColor = if (isSpeakerPressed) NeoSurfacePressed else NeoSurface,
                                                elevation = 3.dp,
                                                isPressed = isSpeakerPressed,
                                                cornerRadius = 19.dp
                                            )
                                            .clickable(
                                                interactionSource = speakerInteraction,
                                                indication = null,
                                                onClick = {
                                                    hapticEngine.trigger(HapticPattern.CLICK)
                                                    val text = if (splitMode == "EQUAL") {
                                                        BillSpeechNarrator.narrateEqualSplit(billBreakdown.totalRupees, share.shareRupees, participantCount, selectedLanguage)
                                                    } else {
                                                        BillSpeechNarrator.narrateDinerShare(share, selectedLanguage)
                                                    }
                                                    onSpeakNarration(text)
                                                }
                                            )
                                            .semantics {
                                                contentDescription = "Hear share narration for ${share.label}"
                                                role = Role.Button
                                            }
                                    ) {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                                            contentDescription = null,
                                            tint = NeoPrimary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }

                Spacer(modifier = Modifier.height(8.dp))

                // ============================================================
                // 7. BOTTOM ACTION BAR: MIC + READ BILL + DONE CONFIRMATION
                // ============================================================
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp)
                ) {
                    // Circular Mic Button with recording state animation
                    val micInteraction = remember { MutableInteractionSource() }
                    val isMicPressed by micInteraction.collectIsPressedAsState()

                    val infiniteTransition = rememberInfiniteTransition(label = "mic_bottom_pulse")
                    val pulseScale by infiniteTransition.animateFloat(
                        initialValue = 1.0f,
                        targetValue = 1.25f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(durationMillis = 1000, easing = LinearOutSlowInEasing),
                            repeatMode = RepeatMode.Reverse
                        ),
                        label = "pulse_scale"
                    )

                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.size(54.dp)
                    ) {
                        if (isRecording) {
                            Box(
                                modifier = Modifier
                                    .size(54.dp)
                                    .scale(pulseScale)
                                    .clip(CircleShape)
                                    .background(NonVegRed.copy(alpha = 0.25f))
                            )
                        }

                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(52.dp)
                                .neomorphicSurface(
                                    shape = CircleShape,
                                    surfaceColor = when {
                                        isRecording -> NonVegRed
                                        isAiProcessing -> NeoSurfacePressed
                                        else -> NeoSurface
                                    },
                                    lightColor = if (isRecording) Color(0x66FFFFFF) else NeoLightShadow,
                                    darkColor = if (isRecording) Color(0x4D000000) else NeoDarkShadow,
                                    borderColor = if (isRecording) NonVegRed else NeoBorder,
                                    elevation = if (isRecording) 6.dp else 4.dp,
                                    isPressed = isMicPressed || isRecording,
                                    cornerRadius = 26.dp
                                )
                                .clickable(
                                    interactionSource = micInteraction,
                                    indication = null,
                                    onClick = {
                                        if (isRecording) {
                                            audioRecorder?.stopRecording()
                                        } else {
                                            startListening()
                                        }
                                    }
                                )
                                .semantics {
                                    contentDescription = if (isRecording) "Stop listening" else "Speak to add dishes or split bill"
                                    role = Role.Button
                                }
                        ) {
                            if (isAiProcessing) {
                                CircularProgressIndicator(
                                    color = NeoPrimary,
                                    modifier = Modifier.size(22.dp),
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Icon(
                                    imageVector = if (isRecording) Icons.Default.Stop else Icons.Default.Mic,
                                    contentDescription = null,
                                    tint = if (isRecording) NeoOnPrimary else NeoPrimary,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                    }

                    // Read Bill Button
                    val readInteraction = remember { MutableInteractionSource() }
                    val isReadPressed by readInteraction.collectIsPressedAsState()
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .weight(1f)
                            .height(52.dp)
                            .neomorphicSurface(
                                shape = RoundedCornerShape(16.dp),
                                surfaceColor = if (isReadPressed) NeoSurfacePressed else NeoSurface,
                                elevation = 4.dp,
                                isPressed = isReadPressed,
                                cornerRadius = 16.dp
                            )
                            .clickable(
                                interactionSource = readInteraction,
                                indication = null,
                                onClick = {
                                    hapticEngine.trigger(HapticPattern.CLICK)
                                    val summary = BillSpeechNarrator.narrateBillSummary(billBreakdown, selectedLanguage)
                                    onSpeakNarration(summary)
                                }
                            )
                            .semantics {
                                contentDescription = "Read bill breakdown out loud"
                                role = Role.Button
                            }
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                                contentDescription = null,
                                tint = NeoPrimary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Read Bill",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = NeoTextPrimary
                            )
                        }
                    }

                    // Done Confirmation Button
                    val doneInteraction = remember { MutableInteractionSource() }
                    val isDonePressed by doneInteraction.collectIsPressedAsState()
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .weight(1.3f)
                            .height(52.dp)
                            .neomorphicSurface(
                                shape = RoundedCornerShape(16.dp),
                                surfaceColor = if (isDonePressed) Color(0xFF1D4ED8) else NeoPrimary,
                                lightColor = Color(0x6693C5FD),
                                darkColor = Color(0x661E3A8A),
                                borderColor = Color(0xFF3B82F6),
                                elevation = 5.dp,
                                isPressed = isDonePressed,
                                cornerRadius = 16.dp
                            )
                            .clickable(
                                interactionSource = doneInteraction,
                                indication = null,
                                onClick = {
                                    hapticEngine.trigger(HapticPattern.CONFIRMATION)
                                    earconEngine.play(EarconType.ORDER_CONFIRMED)
                                    onStopNarration()
                                    onClose()
                                }
                            )
                            .semantics {
                                contentDescription = "Done bill split. Total is ₹${billBreakdown.totalRupees}"
                                role = Role.Button
                            }
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = NeoOnPrimary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Done (₹${billBreakdown.totalRupees})",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = NeoOnPrimary
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))
            }

            // ============================================================
            // 8. MANUAL ADD ITEM DIALOG
            // ============================================================
            if (showManualAddDialog) {
                var manualName by remember { mutableStateOf("") }
                var manualPrice by remember { mutableStateOf("") }

                AlertDialog(
                    onDismissRequest = { showManualAddDialog = false },
                    containerColor = NeoSurface,
                    shape = RoundedCornerShape(20.dp),
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.AddCircle,
                                contentDescription = null,
                                tint = NeoPrimary,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Add Item to Bill",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = NeoTextPrimary
                            )
                        }
                    },
                    text = {
                        Column {
                            OutlinedTextField(
                                value = manualName,
                                onValueChange = { manualName = it },
                                label = { Text("Dish Name (e.g. Masala Dosa)", color = NeoTextSecondary) },
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = NeoTextPrimary,
                                    unfocusedTextColor = NeoTextPrimary,
                                    focusedBorderColor = NeoPrimary,
                                    unfocusedBorderColor = NeoBorder,
                                    focusedLabelColor = NeoPrimary,
                                    unfocusedLabelColor = NeoTextSecondary
                                ),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            OutlinedTextField(
                                value = manualPrice,
                                onValueChange = { manualPrice = it },
                                label = { Text("Price in Rupees (e.g. 120)", color = NeoTextSecondary) },
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = NeoTextPrimary,
                                    unfocusedTextColor = NeoTextPrimary,
                                    focusedBorderColor = NeoPrimary,
                                    unfocusedBorderColor = NeoBorder,
                                    focusedLabelColor = NeoPrimary,
                                    unfocusedLabelColor = NeoTextSecondary
                                ),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                val priceNum = manualPrice.trim().toLongOrNull() ?: 100L
                                if (manualName.isNotBlank()) {
                                    addItemToCart(manualName.trim(), priceNum * 100L, 1)
                                }
                                showManualAddDialog = false
                            },
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = NeoPrimary,
                                contentColor = NeoOnPrimary
                            )
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Add,
                                    contentDescription = null,
                                    tint = NeoOnPrimary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(text = "Add Item", fontWeight = FontWeight.Bold)
                            }
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showManualAddDialog = false }) {
                            Text(text = "Cancel", color = NeoTextSecondary, fontWeight = FontWeight.Medium)
                        }
                    }
                )
            }
        }
    }
}
