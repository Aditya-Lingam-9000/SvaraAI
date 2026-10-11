package com.sarvaai.app.ui.screens.hub

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sarvaai.app.audio.earcons.EarconEngine
import com.sarvaai.app.audio.haptics.HapticFeedbackEngine
import com.sarvaai.app.audio.haptics.HapticPattern
import com.sarvaai.app.audio.recorder.AudioRecorder
import com.sarvaai.app.bhashini.asr.AsrNmtClient
import com.sarvaai.app.data.dao.CartDao
import com.sarvaai.app.data.dao.MenuDao
import com.sarvaai.app.data.dao.OrderDao
import com.sarvaai.app.llm.GroqClient
import com.sarvaai.app.ui.components.neomorphicSurface
import com.sarvaai.app.ui.screens.bill.SmartBillSplitScreen
import com.sarvaai.app.ui.screens.cart.VoiceOrderingCartScreen
import com.sarvaai.app.ui.screens.waiter.WaiterBridgeScreen
import com.sarvaai.app.ui.theme.*

/**
 * Sub-tabs available in the Order and Service Hub.
 */
enum class HubSubTab {
    CART,
    WAITER_BRIDGE,
    BILL_SPLIT
}

/**
 * Unified screen combining Voice Ordering Cart, Multilingual Waiter Bridge,
 * and Smart Bill & Split into a single accessible Hub with Neomorphic capsule tab bar navigation.
 */
@Composable
fun OrderAndServiceHubScreen(
    cartDao: CartDao,
    orderDao: OrderDao,
    groqClient: GroqClient,
    audioRecorder: AudioRecorder,
    asrNmtClient: AsrNmtClient,
    hapticEngine: HapticFeedbackEngine,
    earconEngine: EarconEngine,
    selectedLanguage: String = "en",
    menuDao: MenuDao? = null,
    restaurantName: String = "Sarva Bhavan Restaurant",
    isAudioPlaying: Boolean = false,
    initialTab: HubSubTab = HubSubTab.CART,
    onSpeakAudio: ((text: String, lang: String) -> Unit)? = null,
    onSpeakNarration: ((String) -> Unit)? = null,
    onStopAudio: () -> Unit = {},
    onStopNarration: () -> Unit = onStopAudio,
    onNavigateToPlayground: () -> Unit,
    onClose: () -> Unit = onNavigateToPlayground,
    modifier: Modifier = Modifier
) {
    var selectedSubTab by remember { mutableStateOf(initialTab) }

    LaunchedEffect(initialTab) {
        selectedSubTab = initialTab
    }

    val handleSpeakAudio: (text: String, lang: String) -> Unit = { text, lang ->
        if (onSpeakAudio != null) {
            onSpeakAudio.invoke(text, lang)
        } else {
            onSpeakNarration?.invoke(text)
        }
    }

    val handleSpeakNarration: (String) -> Unit = { text ->
        if (onSpeakNarration != null) {
            onSpeakNarration.invoke(text)
        } else if (onSpeakAudio != null) {
            onSpeakAudio.invoke(text, selectedLanguage)
        }
    }

    val handleStop: () -> Unit = {
        onStopAudio()
        onStopNarration()
    }

    val navigateToPlayground = {
        handleStop()
        if (audioRecorder.isRecording.value) {
            audioRecorder.stopRecording()
        }
        onNavigateToPlayground()
        if (onClose != onNavigateToPlayground) {
            onClose()
        }
    }

    // Intercept back navigation to return directly to PLAYGROUND
    BackHandler {
        navigateToPlayground()
    }

    Surface(
        modifier = modifier.fillMaxSize(),
        color = NeoBackground
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // ================================================================
            // Top Neomorphic Segmented Tab Bar (Capsule Pills)
            // ================================================================
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 0.dp)
                    .height(48.dp)
                    .neomorphicSurface(
                        shape = RoundedCornerShape(24.dp),
                        surfaceColor = NeoSurfacePressed,
                        borderColor = NeoBorder,
                        elevation = 2.dp,
                        isPressed = true,
                        cornerRadius = 24.dp
                    )
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                HubTabCapsule(
                    title = "Cart",
                    icon = Icons.Default.ShoppingCart,
                    isSelected = selectedSubTab == HubSubTab.CART,
                    onClick = {
                        if (selectedSubTab != HubSubTab.CART) {
                            hapticEngine.trigger(HapticPattern.CLICK)
                            handleStop()
                            if (audioRecorder.isRecording.value) {
                                audioRecorder.stopRecording()
                            }
                            selectedSubTab = HubSubTab.CART
                        }
                    },
                    modifier = Modifier.weight(1f)
                )

                HubTabCapsule(
                    title = "Waiter Bridge",
                    icon = Icons.Default.Translate,
                    isSelected = selectedSubTab == HubSubTab.WAITER_BRIDGE,
                    onClick = {
                        if (selectedSubTab != HubSubTab.WAITER_BRIDGE) {
                            hapticEngine.trigger(HapticPattern.CLICK)
                            handleStop()
                            if (audioRecorder.isRecording.value) {
                                audioRecorder.stopRecording()
                            }
                            selectedSubTab = HubSubTab.WAITER_BRIDGE
                        }
                    },
                    modifier = Modifier.weight(1f)
                )

                HubTabCapsule(
                    title = "Bill & Split",
                    icon = Icons.AutoMirrored.Filled.ReceiptLong,
                    isSelected = selectedSubTab == HubSubTab.BILL_SPLIT,
                    onClick = {
                        if (selectedSubTab != HubSubTab.BILL_SPLIT) {
                            hapticEngine.trigger(HapticPattern.CLICK)
                            handleStop()
                            if (audioRecorder.isRecording.value) {
                                audioRecorder.stopRecording()
                            }
                            selectedSubTab = HubSubTab.BILL_SPLIT
                        }
                    },
                    modifier = Modifier.weight(1f)
                )
            }

            // ================================================================
            // Content Area (Sub-screens embedded without redundant Scaffolds)
            // ================================================================
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                when (selectedSubTab) {
                    HubSubTab.CART -> {
                        VoiceOrderingCartScreen(
                            cartDao = cartDao,
                            orderDao = orderDao,
                            groqClient = groqClient,
                            audioRecorder = audioRecorder,
                            asrNmtClient = asrNmtClient,
                            hapticEngine = hapticEngine,
                            earconEngine = earconEngine,
                            selectedLanguage = selectedLanguage,
                            menuDao = menuDao,
                            restaurantName = restaurantName,
                            isAudioPlaying = isAudioPlaying,
                            onSpeakNarration = handleSpeakNarration,
                            onStopNarration = handleStop,
                            onOpenBillSplit = {
                                hapticEngine.trigger(HapticPattern.CLICK)
                                selectedSubTab = HubSubTab.BILL_SPLIT
                            },
                            onClose = navigateToPlayground
                        )
                    }

                    HubSubTab.WAITER_BRIDGE -> {
                        WaiterBridgeScreen(
                            asrNmtClient = asrNmtClient,
                            groqClient = groqClient,
                            audioRecorder = audioRecorder,
                            hapticEngine = hapticEngine,
                            earconEngine = earconEngine,
                            initialDinerLanguage = selectedLanguage,
                            initialStaffLanguage = if (selectedLanguage == "hi") "te" else "hi",
                            isAudioPlaying = isAudioPlaying,
                            onSpeakAudio = handleSpeakAudio,
                            onStopAudio = handleStop,
                            onClose = navigateToPlayground
                        )
                    }

                    HubSubTab.BILL_SPLIT -> {
                        SmartBillSplitScreen(
                            cartDao = cartDao,
                            orderDao = orderDao,
                            menuDao = menuDao,
                            groqClient = groqClient,
                            audioRecorder = audioRecorder,
                            asrNmtClient = asrNmtClient,
                            hapticEngine = hapticEngine,
                            earconEngine = earconEngine,
                            selectedLanguage = selectedLanguage,
                            restaurantName = restaurantName,
                            isAudioPlaying = isAudioPlaying,
                            onSpeakNarration = handleSpeakNarration,
                            onStopNarration = handleStop,
                            onClose = navigateToPlayground
                        )
                    }
                }
            }
        }
    }
}

/**
 * Individual Neomorphic capsule tab button for Hub navigation.
 */
@Composable
private fun HubTabCapsule(
    title: String,
    icon: ImageVector,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .fillMaxHeight()
            .neomorphicSurface(
                shape = RoundedCornerShape(20.dp),
                surfaceColor = if (isSelected) NeoPrimary else NeoSurface,
                lightColor = if (isSelected) Color(0x6693C5FD) else NeoLightShadow,
                darkColor = if (isSelected) Color(0x661E3A8A) else NeoDarkShadow,
                borderColor = if (isSelected) NeoPrimary else NeoBorder,
                elevation = if (isSelected) 2.dp else 1.dp,
                isPressed = isPressed,
                cornerRadius = 20.dp
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .semantics {
                contentDescription = "$title Tab"
                role = Role.Tab
            }
            .padding(horizontal = 6.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (isSelected) NeoOnPrimary else NeoTextSecondary,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = title,
                fontSize = 11.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                color = if (isSelected) NeoOnPrimary else NeoTextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
