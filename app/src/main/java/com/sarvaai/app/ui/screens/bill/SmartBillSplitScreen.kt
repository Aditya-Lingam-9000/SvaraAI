package com.sarvaai.app.ui.screens.bill

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
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
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
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
import com.sarvaai.app.llm.GroqClient
import com.sarvaai.app.llm.models.StructuredAiResponse
import com.sarvaai.app.service.bill.BillCalculator
import com.sarvaai.app.service.bill.BillSpeechNarrator
import com.sarvaai.app.service.bill.DinerShare
import com.sarvaai.app.service.upi.UpiIntentGenerator
import com.sarvaai.app.ui.components.AccessibleButton
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
    var showQrModal by remember { mutableStateOf<DinerShare?>(null) }
    var showFullBillQrModal by remember { mutableStateOf(false) }
    var showManualAddDialog by remember { mutableStateOf(false) }

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

    fun launchUpiPayment(amountRupees: Double, note: String) {
        hapticEngine.trigger(HapticPattern.CONFIRMATION)
        earconEngine.play(EarconType.ORDER_CONFIRMED)
        try {
            val intent = UpiIntentGenerator.buildUpiIntent(
                amountRupees = amountRupees,
                transactionNote = note
            )
            context.startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(context, "No UPI app found. You can copy the UPI URI.", Toast.LENGTH_SHORT).show()
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
                // Header Bar
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 10.dp)
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Smart Bill & UPI Split (Phase 11)",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = SaffronPrimary,
                            modifier = Modifier.semantics { heading() }
                        )
                        Text(
                            text = "$restaurantName • ${cartItems.sumOf { it.quantity }} items",
                            fontSize = 12.sp,
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
                            Icon(imageVector = Icons.Default.Stop, contentDescription = "Stop speech narration", tint = NonVegRed)
                        }
                    }

                    IconButton(
                        onClick = {
                            hapticEngine.trigger(HapticPattern.CLICK)
                            onStopNarration()
                            onClose()
                        }
                    ) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "Close Bill Screen", tint = PureWhite)
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
                    colors = CardDefaults.cardColors(containerColor = CharcoalSurface),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.5.dp, SaffronPrimary, RoundedCornerShape(12.dp))
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(text = "Subtotal (${cartItems.sumOf { it.quantity }} items)", fontSize = 13.sp, color = SubtitleText)
                            Text(text = "₹${billBreakdown.subtotalRupees}", fontSize = 13.sp, color = PureWhite, fontWeight = FontWeight.SemiBold)
                        }

                        Row(
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth().padding(top = 3.dp)
                        ) {
                            Text(text = "CGST (2.5%)", fontSize = 12.sp, color = SubtitleText)
                            Text(text = "₹${billBreakdown.cgstRupees}", fontSize = 12.sp, color = PureWhite)
                        }

                        Row(
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth().padding(top = 2.dp)
                        ) {
                            Text(text = "SGST (2.5%)", fontSize = 12.sp, color = SubtitleText)
                            Text(text = "₹${billBreakdown.sgstRupees}", fontSize = 12.sp, color = PureWhite)
                        }

                        if (applyServiceCharge) {
                            Row(
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth().padding(top = 2.dp)
                            ) {
                                Text(text = "Service Charge (5%)", fontSize = 12.sp, color = SubtitleText)
                                Text(text = "₹${billBreakdown.serviceChargeRupees}", fontSize = 12.sp, color = PureWhite)
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
                                colors = CheckboxDefaults.colors(checkedColor = SaffronPrimary)
                            )
                            Text(text = "Include 5% Restaurant Service Charge", fontSize = 11.sp, color = PureWhite)
                        }

                        HorizontalDivider(color = Color(0xFF444444), modifier = Modifier.padding(vertical = 6.dp))

                        Row(
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column {
                                Text(text = "Grand Total", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = PureWhite)
                                Text(
                                    text = NumberVerbalizer.verbalizePrice(billBreakdown.totalRupees, selectedLanguage),
                                    fontSize = 11.sp,
                                    color = SaffronPrimary
                                )
                            }
                            Text(
                                text = "₹${billBreakdown.totalRupees}",
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Bold,
                                color = SaffronPrimary
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Spoken breakdown audio button
                        AccessibleButton(
                            text = "🎧 Hear Spoken Bill Breakdown",
                            onClick = {
                                hapticEngine.trigger(HapticPattern.CONFIRMATION)
                                earconEngine.play(EarconType.ORDER_CONFIRMED)
                                val summary = BillSpeechNarrator.narrateBillSummary(billBreakdown, selectedLanguage)
                                onSpeakNarration(summary)
                            },
                            modifier = Modifier.fillMaxWidth(),
                            isPrimary = false
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // QUICK DISH SELECTOR & ADD DISH BAR
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(text = "⚡ Add Dishes:", fontSize = 11.sp, color = SubtitleText, fontWeight = FontWeight.Bold)

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
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(CharcoalSurfaceVariant)
                                    .border(1.dp, Color(0xFF555555), RoundedCornerShape(6.dp))
                                    .clickable {
                                        addItemToCart(name, price, 1)
                                    }
                                    .padding(horizontal = 8.dp)
                            ) {
                                Text(text = "+ $name (₹${price / 100})", fontSize = 11.sp, color = PureWhite, fontWeight = FontWeight.Medium)
                            }
                        }
                    }

                    // Manual custom add button
                    IconButton(
                        onClick = { showManualAddDialog = true },
                        modifier = Modifier
                            .size(30.dp)
                            .background(SaffronPrimary, CircleShape)
                    ) {
                        Icon(imageVector = Icons.Default.Add, contentDescription = "Add custom item", tint = SaffronOnPrimary, modifier = Modifier.size(16.dp))
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // SPLIT MODE TABS & PARTICIPANT COUNTER
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // Mode Switcher
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .height(40.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(CharcoalSurface)
                            .border(1.dp, Color(0xFF444444), RoundedCornerShape(8.dp))
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .background(if (splitMode == "EQUAL") SaffronPrimary else Color.Transparent)
                                .clickable {
                                    hapticEngine.trigger(HapticPattern.CLICK)
                                    splitMode = "EQUAL"
                                }
                        ) {
                            Text(
                                text = "👥 Equal Split",
                                fontSize = 11.sp,
                                fontWeight = if (splitMode == "EQUAL") FontWeight.Bold else FontWeight.Medium,
                                color = if (splitMode == "EQUAL") SaffronOnPrimary else PureWhite
                            )
                        }

                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .background(if (splitMode == "ITEM") SaffronPrimary else Color.Transparent)
                                .clickable {
                                    hapticEngine.trigger(HapticPattern.CLICK)
                                    splitMode = "ITEM"
                                }
                        ) {
                            Text(
                                text = "🍽️ By Items",
                                fontSize = 11.sp,
                                fontWeight = if (splitMode == "ITEM") FontWeight.Bold else FontWeight.Medium,
                                color = if (splitMode == "ITEM") SaffronOnPrimary else PureWhite
                            )
                        }
                    }

                    // Participant Stepper
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier
                            .height(40.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(CharcoalSurface)
                            .border(1.dp, Color(0xFF444444), RoundedCornerShape(8.dp))
                            .padding(horizontal = 6.dp)
                    ) {
                        IconButton(
                            onClick = {
                                if (participantCount > 1) {
                                    hapticEngine.trigger(HapticPattern.CLICK)
                                    participantCount -= 1
                                }
                            },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(imageVector = Icons.Default.Remove, contentDescription = "Decrease Diners", tint = PureWhite, modifier = Modifier.size(16.dp))
                        }

                        Text(
                            text = "$participantCount Diners",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = HighContrastYellow
                        )

                        IconButton(
                            onClick = {
                                if (participantCount < 10) {
                                    hapticEngine.trigger(HapticPattern.CLICK)
                                    participantCount += 1
                                }
                            },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(imageVector = Icons.Default.Add, contentDescription = "Increase Diners", tint = PureWhite, modifier = Modifier.size(16.dp))
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
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(imageVector = Icons.Default.ReceiptLong, contentDescription = null, tint = CharcoalSurfaceVariant, modifier = Modifier.size(54.dp))
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(text = "No items in bill yet", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = PureWhite)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(text = "Tap the mic below or choose quick dishes above to build bill", fontSize = 12.sp, color = SubtitleText, textAlign = TextAlign.Center)
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
                                    colors = CardDefaults.cardColors(containerColor = CharcoalSurfaceVariant),
                                    modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp)
                                ) {
                                    Column(modifier = Modifier.padding(10.dp)) {
                                        Text(text = "Assign dishes to diner:", fontSize = 12.sp, color = SubtitleText)
                                        Spacer(modifier = Modifier.height(4.dp))
                                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                            items((1..participantCount).toList()) { pIndex ->
                                                val isSelected = selectedPersonForAssignment == pIndex
                                                Box(
                                                    contentAlignment = Alignment.Center,
                                                    modifier = Modifier
                                                        .height(30.dp)
                                                        .clip(RoundedCornerShape(6.dp))
                                                        .background(if (isSelected) SaffronPrimary else CharcoalSurface)
                                                        .clickable {
                                                            hapticEngine.trigger(HapticPattern.CLICK)
                                                            selectedPersonForAssignment = pIndex
                                                        }
                                                        .padding(horizontal = 10.dp)
                                                ) {
                                                    Text(
                                                        text = "Person $pIndex",
                                                        fontSize = 12.sp,
                                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                                        color = if (isSelected) SaffronOnPrimary else PureWhite
                                                    )
                                                }
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(8.dp))

                                        cartItems.forEach { item ->
                                            val currentAssigned = itemAssignments[selectedPersonForAssignment] ?: emptyList()
                                            val isChecked = currentAssigned.contains(item.cartItemId)

                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clickable {
                                                        hapticEngine.trigger(HapticPattern.CLICK)
                                                        val updated = if (isChecked) currentAssigned - item.cartItemId else currentAssigned + item.cartItemId
                                                        itemAssignments = itemAssignments + (selectedPersonForAssignment to updated)
                                                    }
                                            ) {
                                                Checkbox(
                                                    checked = isChecked,
                                                    onCheckedChange = { checked ->
                                                        hapticEngine.trigger(HapticPattern.CLICK)
                                                        val updated = if (checked) currentAssigned + item.cartItemId else currentAssigned - item.cartItemId
                                                        itemAssignments = itemAssignments + (selectedPersonForAssignment to updated)
                                                    },
                                                    colors = CheckboxDefaults.colors(checkedColor = SaffronPrimary)
                                                )
                                                Text(text = "${item.name} (₹${item.totalPriceRupees})", fontSize = 13.sp, color = PureWhite)
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
                                colors = CardDefaults.cardColors(containerColor = CharcoalSurface),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .border(1.dp, Color(0xFF333333), RoundedCornerShape(10.dp))
                                    .semantics(mergeDescendants = true) {
                                        contentDescription = "${share.label}, share ${share.shareRupees} rupees ($shareWords)."
                                    }
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = share.label,
                                            fontSize = 15.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = PureWhite
                                        )
                                        Text(
                                            text = "₹${share.shareRupees} • $shareWords",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = SaffronPrimary
                                        )
                                        if (share.assignedItems.isNotEmpty()) {
                                            Text(
                                                text = "Dishes: ${share.assignedItems.joinToString(", ") { it.name }}",
                                                fontSize = 11.sp,
                                                color = SubtitleText
                                            )
                                        }
                                    }

                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        // Hear share audio
                                        IconButton(
                                            onClick = {
                                                hapticEngine.trigger(HapticPattern.CLICK)
                                                val text = BillSpeechNarrator.narrateDinerShare(share, selectedLanguage)
                                                onSpeakNarration(text)
                                            },
                                            modifier = Modifier
                                                .size(34.dp)
                                                .background(CharcoalSurfaceVariant, CircleShape)
                                        ) {
                                            Icon(imageVector = Icons.AutoMirrored.Filled.VolumeUp, contentDescription = "Hear share", tint = PureWhite, modifier = Modifier.size(16.dp))
                                        }

                                        // Show QR / Table Pay
                                        IconButton(
                                            onClick = {
                                                hapticEngine.trigger(HapticPattern.CLICK)
                                                showQrModal = share
                                            },
                                            modifier = Modifier
                                                .size(34.dp)
                                                .background(CharcoalSurfaceVariant, CircleShape)
                                        ) {
                                            Icon(imageVector = Icons.Default.QrCode, contentDescription = "Show QR code", tint = HighContrastYellow, modifier = Modifier.size(16.dp))
                                        }

                                        // Direct UPI App Payment
                                        Box(
                                            contentAlignment = Alignment.Center,
                                            modifier = Modifier
                                                .height(34.dp)
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(SaffronPrimary)
                                                .clickable {
                                                    launchUpiPayment(
                                                        amountRupees = share.shareRupees.toDouble(),
                                                        note = "${share.label} Bill Share"
                                                    )
                                                }
                                                .padding(horizontal = 10.dp)
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(imageVector = Icons.Default.Payment, contentDescription = null, tint = SaffronOnPrimary, modifier = Modifier.size(14.dp))
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text(text = "Pay UPI", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = SaffronOnPrimary)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // BOTTOM CONTROLS: MIC BUTTON + TABLE QR + PAY FULL BILL
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // Voice Mic Button for direct voice ordering/modification on bill screen
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(52.dp)
                            .clip(CircleShape)
                            .background(if (isRecording) NonVegRed else if (isAiProcessing) CharcoalSurfaceVariant else SaffronPrimary)
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
                            CircularProgressIndicator(color = SaffronPrimary, modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(
                                imageVector = if (isRecording) Icons.Default.MicOff else Icons.Default.Mic,
                                contentDescription = null,
                                tint = if (isRecording) PureWhite else SaffronOnPrimary,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }

                    AccessibleButton(
                        text = "🔲 Table QR",
                        onClick = {
                            hapticEngine.trigger(HapticPattern.CLICK)
                            showFullBillQrModal = true
                        },
                        modifier = Modifier.weight(1f),
                        isPrimary = false
                    )

                    AccessibleButton(
                        text = "📱 Pay Full (₹${billBreakdown.totalRupees})",
                        onClick = {
                            launchUpiPayment(
                                amountRupees = billBreakdown.totalRupees.toDouble(),
                                note = "Full Dining Bill at $restaurantName"
                            )
                        },
                        modifier = Modifier.weight(1.5f),
                        isPrimary = true
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))
            }

            // Manual Add Item Dialog
            if (showManualAddDialog) {
                var manualName by remember { mutableStateOf("") }
                var manualPrice by remember { mutableStateOf("") }

                AlertDialog(
                    onDismissRequest = { showManualAddDialog = false },
                    containerColor = CharcoalSurface,
                    title = {
                        Text(text = "➕ Add Item to Bill", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = SaffronPrimary)
                    },
                    text = {
                        Column {
                            OutlinedTextField(
                                value = manualName,
                                onValueChange = { manualName = it },
                                label = { Text("Dish Name (e.g. Masala Dosa)", color = SubtitleText) },
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = PureWhite,
                                    unfocusedTextColor = PureWhite,
                                    focusedBorderColor = SaffronPrimary,
                                    unfocusedBorderColor = Color(0xFF555555)
                                ),
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedTextField(
                                value = manualPrice,
                                onValueChange = { manualPrice = it },
                                label = { Text("Price in Rupees (e.g. 120)", color = SubtitleText) },
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = PureWhite,
                                    unfocusedTextColor = PureWhite,
                                    focusedBorderColor = SaffronPrimary,
                                    unfocusedBorderColor = Color(0xFF555555)
                                ),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    },
                    confirmButton = {
                        AccessibleButton(
                            text = "Add Item",
                            onClick = {
                                val priceNum = manualPrice.trim().toLongOrNull() ?: 100L
                                if (manualName.isNotBlank()) {
                                    addItemToCart(manualName.trim(), priceNum * 100L, 1)
                                }
                                showManualAddDialog = false
                            },
                            isPrimary = true
                        )
                    },
                    dismissButton = {
                        TextButton(onClick = { showManualAddDialog = false }) {
                            Text(text = "Cancel", color = PureWhite)
                        }
                    }
                )
            }

            // QR Modal for Individual Diner Share
            showQrModal?.let { share ->
                val upiUri = UpiIntentGenerator.buildUpiUriString(
                    amountRupees = share.shareRupees.toDouble(),
                    transactionNote = "${share.label} Share"
                )
                AlertDialog(
                    onDismissRequest = { showQrModal = null },
                    containerColor = CharcoalSurface,
                    title = {
                        Text(text = "📱 ${share.label} UPI Payment", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = SaffronPrimary)
                    },
                    text = {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(text = "Exact Share: ₹${share.shareRupees}", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = VegGreen)
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(text = "VPA: ${UpiIntentGenerator.DEFAULT_RESTAURANT_VPA}", fontSize = 12.sp, color = SubtitleText)
                            Spacer(modifier = Modifier.height(10.dp))
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color.Black, RoundedCornerShape(8.dp))
                                    .padding(10.dp)
                            ) {
                                Text(
                                    text = upiUri,
                                    fontSize = 11.sp,
                                    color = HighContrastYellow,
                                    lineHeight = 14.sp
                                )
                            }
                        }
                    },
                    confirmButton = {
                        AccessibleButton(
                            text = "Open UPI App",
                            onClick = {
                                launchUpiPayment(share.shareRupees.toDouble(), "${share.label} Share")
                                showQrModal = null
                            },
                            isPrimary = true
                        )
                    },
                    dismissButton = {
                        TextButton(onClick = { showQrModal = null }) {
                            Text(text = "Close", color = PureWhite)
                        }
                    }
                )
            }

            // QR Modal for Full Table Bill
            if (showFullBillQrModal) {
                val fullUpiUri = UpiIntentGenerator.buildUpiUriString(
                    amountRupees = billBreakdown.totalRupees.toDouble(),
                    transactionNote = "Table Bill at $restaurantName"
                )
                AlertDialog(
                    onDismissRequest = { showFullBillQrModal = false },
                    containerColor = CharcoalSurface,
                    title = {
                        Text(text = "🧾 Table UPI Bill Payment", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = SaffronPrimary)
                    },
                    text = {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(text = "Grand Total: ₹${billBreakdown.totalRupees}", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = SaffronPrimary)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(text = "Restaurant: $restaurantName", fontSize = 13.sp, color = PureWhite)
                            Text(text = "UPI VPA: ${UpiIntentGenerator.DEFAULT_RESTAURANT_VPA}", fontSize = 12.sp, color = SubtitleText)
                            Spacer(modifier = Modifier.height(10.dp))
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color.Black, RoundedCornerShape(8.dp))
                                    .padding(10.dp)
                            ) {
                                Text(
                                    text = fullUpiUri,
                                    fontSize = 11.sp,
                                    color = HighContrastYellow,
                                    lineHeight = 14.sp
                                )
                            }
                        }
                    },
                    confirmButton = {
                        AccessibleButton(
                            text = "Launch UPI App",
                            onClick = {
                                launchUpiPayment(billBreakdown.totalRupees.toDouble(), "Table Bill")
                                showFullBillQrModal = false
                            },
                            isPrimary = true
                        )
                    },
                    dismissButton = {
                        TextButton(onClick = { showFullBillQrModal = false }) {
                            Text(text = "Close", color = PureWhite)
                        }
                    }
                )
            }
        }
    }
}
