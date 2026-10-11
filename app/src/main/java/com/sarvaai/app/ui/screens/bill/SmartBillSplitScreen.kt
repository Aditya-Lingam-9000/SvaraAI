package com.sarvaai.app.ui.screens.bill

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.VolumeUp
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
import com.sarvaai.app.llm.GroqClient
import com.sarvaai.app.llm.models.StructuredAiResponse
import com.sarvaai.app.service.bill.BillCalculator
import com.sarvaai.app.service.bill.BillSpeechNarrator
import com.sarvaai.app.service.bill.DinerShare
import com.sarvaai.app.service.wakeword.WakeWordDetector
import com.sarvaai.app.ui.components.LiveCaptionBar
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

    val itemTotalPaise = cartItems.sumOf { it.totalPricePaise }
    val billBreakdown = remember(itemTotalPaise, applyServiceCharge) {
        BillCalculator.calculateBill(itemTotalPaise, applyServiceCharge)
    }

    val equalShares = remember(billBreakdown.totalPaise, participantCount) {
        BillCalculator.splitEqually(billBreakdown.totalPaise, participantCount)
    }

    val itemShares = remember(cartItems, itemAssignments, participantCount, applyServiceCharge) {
        BillCalculator.splitByItemAssignment(cartItems, itemAssignments, participantCount, applyServiceCharge)
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
                    .systemBarsPadding()
            ) {
                // Header Bar
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
                                text = "Smart Bill & Tax Splitter",
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
                                    contentDescription = "Stop speech narration",
                                    tint = NonVegRed,
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
                                contentDescription = "Close Bill Screen",
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
                    modifier = Modifier.padding(bottom = 6.dp)
                )

                // BILL SUMMARY CARD
                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = NeoSurface),
                    border = BorderStroke(1.dp, NeoBorder),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .animateContentSize()
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
                                    text = "Bill Breakdown",
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

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(text = "Subtotal", fontSize = 13.sp, color = NeoTextSecondary)
                            Text(text = "₹${billBreakdown.subtotalRupees}", fontSize = 13.sp, color = NeoTextPrimary, fontWeight = FontWeight.SemiBold)
                        }

                        Row(
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth().padding(top = 3.dp)
                        ) {
                            Text(text = "CGST (2.5%)", fontSize = 12.sp, color = NeoTextSecondary)
                            Text(text = "₹${billBreakdown.cgstRupees}", fontSize = 12.sp, color = NeoTextPrimary)
                        }

                        Row(
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth().padding(top = 2.dp)
                        ) {
                            Text(text = "SGST (2.5%)", fontSize = 12.sp, color = NeoTextSecondary)
                            Text(text = "₹${billBreakdown.sgstRupees}", fontSize = 12.sp, color = NeoTextPrimary)
                        }

                        AnimatedVisibility(
                            visible = applyServiceCharge,
                            enter = fadeIn() + expandVertically(),
                            exit = fadeOut() + shrinkVertically()
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth().padding(top = 2.dp)
                            ) {
                                Text(text = "Service Charge (5%)", fontSize = 12.sp, color = NeoTextSecondary)
                                Text(text = "₹${billBreakdown.serviceChargeRupees}", fontSize = 12.sp, color = NeoTextPrimary)
                            }
                        }

                        // Service charge toggle
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 4.dp)
                                .clickable {
                                    hapticEngine.trigger(HapticPattern.CLICK)
                                    applyServiceCharge = !applyServiceCharge
                                }
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
                                )
                            )
                            Text(
                                text = "Include 5% Restaurant Service Charge",
                                fontSize = 12.sp,
                                color = NeoTextPrimary,
                                fontWeight = FontWeight.Medium
                            )
                        }

                        HorizontalDivider(color = NeoBorder, modifier = Modifier.padding(vertical = 6.dp))

                        Row(
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column {
                                Text(text = "Grand Total", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = NeoTextPrimary)
                                Text(
                                    text = NumberVerbalizer.verbalizePrice(billBreakdown.totalRupees, selectedLanguage),
                                    fontSize = 11.sp,
                                    color = NeoPrimary,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                            Text(
                                text = "₹${billBreakdown.totalRupees}",
                                fontSize = 24.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = NeoPrimary
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Spoken breakdown audio button
                        Button(
                            onClick = {
                                hapticEngine.trigger(HapticPattern.CONFIRMATION)
                                earconEngine.play(EarconType.ORDER_CONFIRMED)
                                val summary = BillSpeechNarrator.narrateBillSummary(billBreakdown, selectedLanguage)
                                onSpeakNarration(summary)
                            },
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = NeoSurfacePressed,
                                contentColor = NeoTextPrimary
                            ),
                            border = BorderStroke(1.dp, NeoBorder),
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 42.dp)
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
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // QUICK DISH SELECTOR & ADD DISH BAR
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.RestaurantMenu,
                            contentDescription = null,
                            tint = NeoTextSecondary,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Add Dishes:",
                            fontSize = 11.sp,
                            color = NeoTextSecondary,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
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
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .height(30.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(NeoSurface)
                                    .border(BorderStroke(1.dp, NeoBorder), RoundedCornerShape(8.dp))
                                    .clickable {
                                        addItemToCart(name, price, 1)
                                    }
                                    .padding(horizontal = 8.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.Add,
                                        contentDescription = null,
                                        tint = NeoPrimary,
                                        modifier = Modifier.size(12.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "$name (₹${price / 100})",
                                        fontSize = 11.sp,
                                        color = NeoTextPrimary,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        }
                    }

                    // Manual custom add button
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(NeoPrimary)
                            .shadow(2.dp, CircleShape, spotColor = NeoDarkShadow, ambientColor = NeoDarkShadow)
                            .clickable { showManualAddDialog = true }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Add custom item",
                            tint = NeoOnPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // SPLIT MODE TABS & PARTICIPANT COUNTER
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // Neomorphic Segmented Tab Switcher
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .height(42.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(NeoSurfacePressed)
                            .border(BorderStroke(1.dp, NeoBorder), RoundedCornerShape(10.dp))
                            .padding(2.dp)
                    ) {
                        // Equal Split Tab
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (splitMode == "EQUAL") NeoSurface else Color.Transparent)
                                .then(
                                    if (splitMode == "EQUAL") {
                                        Modifier.shadow(1.dp, RoundedCornerShape(8.dp), spotColor = NeoDarkShadow, ambientColor = NeoDarkShadow)
                                    } else Modifier
                                )
                                .clickable {
                                    hapticEngine.trigger(HapticPattern.CLICK)
                                    splitMode = "EQUAL"
                                }
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Groups,
                                    contentDescription = null,
                                    tint = if (splitMode == "EQUAL") NeoPrimary else NeoTextSecondary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Equal Split",
                                    fontSize = 12.sp,
                                    fontWeight = if (splitMode == "EQUAL") FontWeight.Bold else FontWeight.Medium,
                                    color = if (splitMode == "EQUAL") NeoPrimary else NeoTextSecondary
                                )
                            }
                        }

                        // By Items Tab
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (splitMode == "ITEM") NeoSurface else Color.Transparent)
                                .then(
                                    if (splitMode == "ITEM") {
                                        Modifier.shadow(1.dp, RoundedCornerShape(8.dp), spotColor = NeoDarkShadow, ambientColor = NeoDarkShadow)
                                    } else Modifier
                                )
                                .clickable {
                                    hapticEngine.trigger(HapticPattern.CLICK)
                                    splitMode = "ITEM"
                                }
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.RestaurantMenu,
                                    contentDescription = null,
                                    tint = if (splitMode == "ITEM") NeoPrimary else NeoTextSecondary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "By Items",
                                    fontSize = 12.sp,
                                    fontWeight = if (splitMode == "ITEM") FontWeight.Bold else FontWeight.Medium,
                                    color = if (splitMode == "ITEM") NeoPrimary else NeoTextSecondary
                                )
                            }
                        }
                    }

                    // Participant Stepper
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier
                            .height(42.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(NeoSurface)
                            .border(BorderStroke(1.dp, NeoBorder), RoundedCornerShape(10.dp))
                            .padding(horizontal = 6.dp)
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(NeoSurfacePressed)
                                .clickable {
                                    if (participantCount > 1) {
                                        hapticEngine.trigger(HapticPattern.CLICK)
                                        participantCount -= 1
                                    }
                                }
                        ) {
                            Icon(
                                imageVector = Icons.Default.Remove,
                                contentDescription = "Decrease Diners",
                                tint = NeoTextPrimary,
                                modifier = Modifier.size(16.dp)
                            )
                        }

                        Text(
                            text = "$participantCount Diners",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = NeoTextPrimary,
                            modifier = Modifier.padding(horizontal = 4.dp)
                        )

                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(NeoPrimaryContainer)
                                .clickable {
                                    if (participantCount < 10) {
                                        hapticEngine.trigger(HapticPattern.CLICK)
                                        participantCount += 1
                                    }
                                }
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = "Increase Diners",
                                tint = NeoPrimary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // DISHES LIST OR ITEM-BY-ITEM ASSIGNMENT
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
                                        .size(60.dp)
                                        .clip(CircleShape)
                                        .background(NeoSurfacePressed)
                                        .border(BorderStroke(1.dp, NeoBorder), CircleShape)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.ReceiptLong,
                                        contentDescription = null,
                                        tint = NeoTextMuted,
                                        modifier = Modifier.size(30.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.height(14.dp))
                                Text(
                                    text = "No items in bill yet",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = NeoTextPrimary
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Tap the microphone below or choose quick dishes above to build bill",
                                    fontSize = 12.sp,
                                    color = NeoTextSecondary,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                    ) {
                        // In Item split mode: show assignment selector
                        if (splitMode == "ITEM") {
                            item {
                                Card(
                                    shape = RoundedCornerShape(16.dp),
                                    colors = CardDefaults.cardColors(containerColor = NeoSurface),
                                    border = BorderStroke(1.dp, NeoBorder),
                                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(bottom = 4.dp)
                                        .animateContentSize()
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
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
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = NeoTextPrimary
                                            )
                                        }

                                        Spacer(modifier = Modifier.height(8.dp))

                                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                            items((1..participantCount).toList()) { pIndex ->
                                                val isSelected = selectedPersonForAssignment == pIndex
                                                Box(
                                                    contentAlignment = Alignment.Center,
                                                    modifier = Modifier
                                                        .height(32.dp)
                                                        .clip(RoundedCornerShape(8.dp))
                                                        .background(if (isSelected) NeoPrimary else NeoSurfacePressed)
                                                        .border(
                                                            BorderStroke(1.dp, if (isSelected) NeoPrimary else NeoBorder),
                                                            RoundedCornerShape(8.dp)
                                                        )
                                                        .clickable {
                                                            hapticEngine.trigger(HapticPattern.CLICK)
                                                            selectedPersonForAssignment = pIndex
                                                        }
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

                                        cartItems.forEach { item ->
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

                            Card(
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = NeoSurface),
                                border = BorderStroke(1.dp, NeoBorder),
                                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .animateContentSize()
                                    .semantics(mergeDescendants = true) {
                                        contentDescription = "${share.label}, share ${share.shareRupees} rupees ($shareWords)."
                                    }
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Box(
                                            contentAlignment = Alignment.Center,
                                            modifier = Modifier
                                                .size(38.dp)
                                                .clip(CircleShape)
                                                .background(NeoPrimaryContainer)
                                                .border(BorderStroke(1.dp, NeoPrimary.copy(alpha = 0.3f)), CircleShape)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Person,
                                                contentDescription = null,
                                                tint = NeoPrimary,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }

                                        Spacer(modifier = Modifier.width(12.dp))

                                        Column {
                                            Text(
                                                text = share.label,
                                                fontSize = 15.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = NeoTextPrimary
                                            )
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Text(
                                                text = "₹${share.shareRupees} • $shareWords",
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = NeoPrimary
                                            )
                                            if (share.assignedItems.isNotEmpty()) {
                                                Spacer(modifier = Modifier.height(2.dp))
                                                Text(
                                                    text = "Dishes: ${share.assignedItems.joinToString(", ") { it.name }}",
                                                    fontSize = 11.sp,
                                                    color = NeoTextSecondary,
                                                    maxLines = 2,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                        }
                                    }

                                    // Hear share audio button
                                    Box(
                                        contentAlignment = Alignment.Center,
                                        modifier = Modifier
                                            .size(36.dp)
                                            .clip(CircleShape)
                                            .background(NeoSurfacePressed)
                                            .border(BorderStroke(1.dp, NeoBorder), CircleShape)
                                            .clickable {
                                                hapticEngine.trigger(HapticPattern.CLICK)
                                                val text = BillSpeechNarrator.narrateDinerShare(share, selectedLanguage)
                                                onSpeakNarration(text)
                                            }
                                    ) {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                                            contentDescription = "Hear share for ${share.label}",
                                            tint = NeoTextPrimary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // BOTTOM CONTROLS: MIC BUTTON + READ BILL + DONE
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // Voice Mic Button for direct voice ordering/modification on bill screen
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(54.dp)
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
                                    audioRecorder?.stopRecording()
                                } else {
                                    startListening()
                                }
                            }
                            .semantics {
                                contentDescription = if (isRecording) "Stop listening" else "Speak to add dishes or split bill"
                            }
                    ) {
                        if (isAiProcessing) {
                            CircularProgressIndicator(color = NeoPrimary, modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(
                                imageVector = if (isRecording) Icons.Default.Stop else Icons.Default.Mic,
                                contentDescription = null,
                                tint = if (isRecording) NeoOnPrimary else NeoPrimary,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }

                    // Read Out Bill Button
                    Button(
                        onClick = {
                            hapticEngine.trigger(HapticPattern.CLICK)
                            val summary = BillSpeechNarrator.narrateBillSummary(billBreakdown, selectedLanguage)
                            onSpeakNarration(summary)
                        },
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = NeoSurfacePressed,
                            contentColor = NeoTextPrimary
                        ),
                        border = BorderStroke(1.dp, NeoBorder),
                        modifier = Modifier
                            .weight(1f)
                            .height(54.dp)
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
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    // Done Button
                    Button(
                        onClick = {
                            hapticEngine.trigger(HapticPattern.CONFIRMATION)
                            earconEngine.play(EarconType.ORDER_CONFIRMED)
                            onStopNarration()
                            onClose()
                        },
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = NeoPrimary,
                            contentColor = NeoOnPrimary
                        ),
                        elevation = ButtonDefaults.buttonElevation(defaultElevation = 3.dp),
                        modifier = Modifier
                            .weight(1.2f)
                            .height(54.dp)
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
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
            }

            // Manual Add Item Dialog
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
                                shape = RoundedCornerShape(10.dp),
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
                                shape = RoundedCornerShape(10.dp),
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
                            shape = RoundedCornerShape(10.dp),
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
