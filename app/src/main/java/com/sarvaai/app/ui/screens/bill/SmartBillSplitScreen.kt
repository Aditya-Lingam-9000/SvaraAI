package com.sarvaai.app.ui.screens.bill

import android.content.Context
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
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
import com.sarvaai.app.data.dao.CartDao
import com.sarvaai.app.data.entities.CartItemEntity
import com.sarvaai.app.service.bill.BillCalculator
import com.sarvaai.app.service.bill.BillSpeechNarrator
import com.sarvaai.app.service.bill.DinerShare
import com.sarvaai.app.service.upi.UpiIntentGenerator
import com.sarvaai.app.ui.components.AccessibleButton
import com.sarvaai.app.ui.theme.*

@Composable
fun SmartBillSplitScreen(
    cartDao: CartDao,
    hapticEngine: HapticFeedbackEngine,
    earconEngine: EarconEngine,
    selectedLanguage: String,
    restaurantName: String = "Sarva Bhavan Restaurant",
    isAudioPlaying: Boolean = false,
    onSpeakNarration: (String) -> Unit,
    onStopNarration: () -> Unit = {},
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val cartItems by cartDao.getCartItems().collectAsState(initial = emptyList())

    var participantCount by remember { mutableStateOf(2) }
    var applyServiceCharge by remember { mutableStateOf(false) }
    var splitMode by remember { mutableStateOf("EQUAL") } // "EQUAL" or "ITEM"
    var selectedPersonForAssignment by remember { mutableStateOf(1) }
    var itemAssignments by remember { mutableStateOf<Map<Int, List<String>>>(emptyMap()) }
    var showQrModal by remember { mutableStateOf<DinerShare?>(null) }
    var showFullBillQrModal by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        onDispose {
            onStopNarration()
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
            Toast.makeText(context, "No UPI app installed. Copy UPI URI or scan QR.", Toast.LENGTH_SHORT).show()
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
                            text = "$restaurantName • Indian GST & Split Engine",
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

                        Divider(color = Color(0xFF444444), modifier = Modifier.padding(vertical = 6.dp))

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

                Spacer(modifier = Modifier.height(10.dp))

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

                // ITEM-BY-ITEM ASSIGNMENT CONTROLS (If Mode == ITEM)
                if (splitMode == "ITEM") {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = CharcoalSurfaceVariant),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp)
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

                            // Items checkboxes for this person
                            cartItems.forEach { item ->
                                val currentAssigned = itemAssignments[selectedPersonForAssignment] ?: emptyList()
                                val isChecked = currentAssigned.contains(item.cartItemId)

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            hapticEngine.trigger(HapticPattern.CLICK)
                                            val updated = if (isChecked) {
                                                currentAssigned - item.cartItemId
                                            } else {
                                                currentAssigned + item.cartItemId
                                            }
                                            itemAssignments = itemAssignments + (selectedPersonForAssignment to updated)
                                        }
                                ) {
                                    Checkbox(
                                        checked = isChecked,
                                        onCheckedChange = { checked ->
                                            hapticEngine.trigger(HapticPattern.CLICK)
                                            val updated = if (checked) {
                                                currentAssigned + item.cartItemId
                                            } else {
                                                currentAssigned - item.cartItemId
                                            }
                                            itemAssignments = itemAssignments + (selectedPersonForAssignment to updated)
                                        },
                                        colors = CheckboxDefaults.colors(checkedColor = SaffronPrimary)
                                    )
                                    Text(
                                        text = "${item.name} (₹${item.totalPriceRupees})",
                                        fontSize = 13.sp,
                                        color = PureWhite
                                    )
                                }
                            }
                        }
                    }
                }

                // PARTICIPANT SHARES LIST
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
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
                                        Icon(imageVector = Icons.Default.VolumeUp, contentDescription = "Hear share", tint = PureWhite, modifier = Modifier.size(16.dp))
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

                Spacer(modifier = Modifier.height(10.dp))

                // BOTTOM ACTION BAR (Pay Full Bill & Show Table QR)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
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
                        text = "📱 Pay Full Bill (₹${billBreakdown.totalRupees})",
                        onClick = {
                            launchUpiPayment(
                                amountRupees = billBreakdown.totalRupees.toDouble(),
                                note = "Full Dining Bill at $restaurantName"
                            )
                        },
                        modifier = Modifier.weight(2f),
                        isPrimary = true
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))
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
