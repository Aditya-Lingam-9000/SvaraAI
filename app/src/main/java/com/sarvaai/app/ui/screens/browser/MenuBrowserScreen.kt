package com.sarvaai.app.ui.screens.browser

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
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
import com.sarvaai.app.data.entities.MenuItemEntity
import com.sarvaai.app.data.entities.MenuSectionEntity
import com.sarvaai.app.llm.GroqClient
import com.sarvaai.app.menu.narrator.LayoutNarrator
import com.sarvaai.app.menu.navigation.NavigationAction
import com.sarvaai.app.menu.navigation.VoiceMenuNavigator
import com.sarvaai.app.service.wakeword.WakeWordDetector
import com.sarvaai.app.ui.components.LiveCaptionBar
import com.sarvaai.app.ui.components.neomorphicSurface
import com.sarvaai.app.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

@Composable
fun MenuBrowserScreen(
    restaurantName: String,
    sections: List<MenuSectionEntity>,
    itemsBySection: Map<String, List<MenuItemEntity>>,
    selectedLanguage: String,
    hapticEngine: HapticFeedbackEngine,
    earconEngine: EarconEngine,
    audioRecorder: AudioRecorder? = null,
    asrNmtClient: AsrNmtClient? = null,
    groqClient: GroqClient? = null,
    autoStartListeningTrigger: Int = 0,
    isAudioPlaying: Boolean = false,
    onSpeakNarration: (String) -> Unit,
    onStopNarration: () -> Unit = {},
    onAddItemToCart: (MenuItemEntity) -> Unit,
    onOpenCart: () -> Unit = {},
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val isRecording = audioRecorder?.isRecording?.collectAsState()?.value ?: false

    var selectedSectionIndex by remember { mutableStateOf(0) }
    var filterVegOnly by remember { mutableStateOf(false) }
    var filterJainOnly by remember { mutableStateOf(false) }
    var isAiProcessing by remember { mutableStateOf(false) }
    var liveCaptionText by remember { mutableStateOf<String?>(null) }
    var highlightedItemId by remember { mutableStateOf<String?>(null) }

    // Pulsing animation for active voice mic listening
    val infiniteTransition = rememberInfiniteTransition(label = "mic_pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = if (isRecording) 1.10f else 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(650, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "mic_scale"
    )

    val navigator = remember(sections, itemsBySection) {
        VoiceMenuNavigator(sections, itemsBySection)
    }

    val activeItem by navigator.activeItem.collectAsState()

    // Process captured voice for dietary questions and orders
    fun processCapturedSpeech(wavBytes: ByteArray) {
        if (wavBytes.isEmpty()) return
        isAiProcessing = true
        scope.launch(Dispatchers.IO) {
            val asrRes = asrNmtClient?.transcribeAndTranslate(wavBytes, selectedLanguage)
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

                // Build full menu context with dietary tags, prices, and descriptions
                val allItems = itemsBySection.values.flatten()
                val menuContext = allItems.joinToString("; ") { item ->
                    "${item.nameEnglish} (Price: ₹${item.priceRupees}, Veg: ${item.isVegetarian}, Jain: ${item.isJain}${if (!item.description.isNullOrBlank()) ", Desc: ${item.description}" else ""})"
                }

                // Dietary reasoning via Groq QA assistant with fallback to local reasoner
                val aiRes = groqClient?.processMenuDietaryQa(
                    userQuery = recognizedText,
                    userLanguage = selectedLanguage,
                    restaurantName = restaurantName,
                    menuItems = allItems,
                    sections = sections
                )
                withContext(Dispatchers.Main) {
                    isAiProcessing = false
                    if (aiRes is Result.Success) {
                        val resp = aiRes.data
                        liveCaptionText = resp.caption.ifBlank { resp.spoken }
                        onSpeakNarration(resp.spoken)

                        for (action in resp.actions) {
                            when (action.actionType) {
                                "FILTER_VEG" -> {
                                    filterVegOnly = true
                                    filterJainOnly = false
                                    hapticEngine.trigger(HapticPattern.CONFIRMATION)
                                }
                                "FILTER_JAIN" -> {
                                    filterJainOnly = true
                                    filterVegOnly = true
                                    hapticEngine.trigger(HapticPattern.CONFIRMATION)
                                }
                                "ANSWER_DIETARY" -> {
                                    val target = action.payload.orEmpty().trim()
                                    if (target.isNotBlank()) {
                                        val matched = allItems.firstOrNull { m ->
                                            m.nameEnglish.contains(target, ignoreCase = true) ||
                                            target.contains(m.nameEnglish, ignoreCase = true) ||
                                            m.itemId.equals(target, ignoreCase = true) ||
                                            (m.nameTelugu != null && (m.nameTelugu.contains(target, ignoreCase = true) || target.contains(m.nameTelugu, ignoreCase = true))) ||
                                            (m.nameHindi != null && (m.nameHindi.contains(target, ignoreCase = true) || target.contains(m.nameHindi, ignoreCase = true)))
                                        }
                                        if (matched != null) {
                                            highlightedItemId = matched.itemId
                                            val secIdx = sections.indexOfFirst { it.sectionId == matched.sectionId }
                                            if (secIdx >= 0) {
                                                selectedSectionIndex = secIdx
                                            }
                                        }
                                    }
                                    hapticEngine.trigger(HapticPattern.CONFIRMATION)
                                }
                                "HIGHLIGHT_ITEM" -> {
                                    val target = action.payload.orEmpty().trim()
                                    val matched = allItems.firstOrNull { m ->
                                        m.nameEnglish.contains(target, ignoreCase = true) ||
                                        target.contains(m.nameEnglish, ignoreCase = true) ||
                                        m.itemId.equals(target, ignoreCase = true) ||
                                        (m.nameTelugu != null && (m.nameTelugu.contains(target, ignoreCase = true) || target.contains(m.nameTelugu, ignoreCase = true))) ||
                                        (m.nameHindi != null && (m.nameHindi.contains(target, ignoreCase = true) || target.contains(m.nameHindi, ignoreCase = true)))
                                    }
                                    if (matched != null) {
                                        highlightedItemId = matched.itemId
                                        val secIdx = sections.indexOfFirst { it.sectionId == matched.sectionId }
                                        if (secIdx >= 0) {
                                            selectedSectionIndex = secIdx
                                        }
                                        hapticEngine.trigger(HapticPattern.CONFIRMATION)
                                    }
                                }
                                "ADD_TO_CART" -> {
                                    val payload = action.payload.orEmpty()
                                    val parts = payload.split(":")
                                    val itemName = parts.getOrNull(0)?.trim()?.takeIf { it.isNotBlank() } ?: recognizedText
                                    val matchedItem = allItems.firstOrNull { m ->
                                        m.nameEnglish.contains(itemName, ignoreCase = true) ||
                                        itemName.contains(m.nameEnglish, ignoreCase = true) ||
                                        (m.nameTelugu != null && (m.nameTelugu.contains(itemName, ignoreCase = true) || itemName.contains(m.nameTelugu, ignoreCase = true))) ||
                                        (m.nameHindi != null && (m.nameHindi.contains(itemName, ignoreCase = true) || itemName.contains(m.nameHindi, ignoreCase = true)))
                                    }
                                    if (matchedItem != null) {
                                        onAddItemToCart(matchedItem)
                                    } else {
                                        val custom = MenuItemEntity(
                                            itemId = "item_${UUID.randomUUID().toString().take(6)}",
                                            sectionId = sections.firstOrNull()?.sectionId ?: "sec_1",
                                            nameEnglish = itemName,
                                            pricePaise = 12000L,
                                            isVegetarian = true
                                        )
                                        onAddItemToCart(custom)
                                    }
                                    earconEngine.play(EarconType.ORDER_CONFIRMED)
                                    hapticEngine.trigger(HapticPattern.CONFIRMATION)
                                }
                                "NAVIGATE_SECTION" -> {
                                    val payload = action.payload.orEmpty()
                                    val matchedSectionIndex = sections.indexOfFirst { sec ->
                                        sec.titleEnglish.contains(payload, ignoreCase = true) ||
                                        payload.contains(sec.titleEnglish, ignoreCase = true) ||
                                        (sec.titleNative != null && payload.contains(sec.titleNative, ignoreCase = true))
                                    }
                                    if (matchedSectionIndex >= 0) {
                                        selectedSectionIndex = matchedSectionIndex
                                    }
                                }
                            }
                        }
                    } else {
                        val spokenFallback = when (selectedLanguage) {
                            "te" -> "క్షమించండి, మీ ప్రశ్నను అర్థం చేసుకోలేకపోయాను. దయచేసి మళ్ళీ అడగండి."
                            "hi" -> "क्षमा करें, मैं समझ नहीं सका। कृपया पुनः प्रयास करें।"
                            else -> "I could not process your query. Please ask again."
                        }
                        liveCaptionText = spokenFallback
                        onSpeakNarration(spokenFallback)
                        hapticEngine.trigger(HapticPattern.ERROR_ALERT)
                        earconEngine.play(EarconType.ERROR_ALERT)
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
        if (audioRecorder == null || asrNmtClient == null || groqClient == null) return
        onStopNarration()
        hapticEngine.trigger(HapticPattern.SPEECH_TURN_CUE)
        earconEngine.play(EarconType.LISTEN_START)
        isAiProcessing = false
        liveCaptionText = when (selectedLanguage) {
            "te" -> "మీ ఆహారపు ప్రశ్న లేదా ఆర్డర్ అడగండి..."
            "hi" -> "अपने आहार संबंधी प्रश्न या ऑर्डर पूछें..."
            else -> "Listening for dietary questions or orders..."
        }

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
            audioRecorder?.stopRecording()
        }
    }

    // Auto-Start Listening Trigger when awakened by 'Hey Svara' or Wake Word
    LaunchedEffect(autoStartListeningTrigger) {
        if (autoStartListeningTrigger > 0) {
            hapticEngine.trigger(HapticPattern.SPEECH_TURN_CUE)
            if (audioRecorder != null && !audioRecorder.isRecording.value) {
                startListening()
            }
        }
    }

    // Safe hardware/gesture back handling
    BackHandler {
        onStopNarration()
        if (audioRecorder?.isRecording?.value == true) {
            audioRecorder.stopRecording()
        }
        onClose()
    }

    DisposableEffect(Unit) {
        onDispose {
            onStopNarration()
            if (audioRecorder?.isRecording?.value == true) {
                audioRecorder.stopRecording()
            }
        }
    }

    val currentSection = sections.getOrNull(selectedSectionIndex)
    val rawSectionItems = currentSection?.let { itemsBySection[it.sectionId] } ?: emptyList()

    val filteredItems = remember(rawSectionItems, filterVegOnly, filterJainOnly) {
        rawSectionItems.filter { item ->
            if (filterJainOnly && !item.isJain) false
            else if (filterVegOnly && !item.isVegetarian) false
            else true
        }
    }

    val totalItemsCount = remember(itemsBySection) {
        itemsBySection.values.sumOf { it.size }
    }

    Surface(
        modifier = modifier.fillMaxSize(),
        color = NeoBackground
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
        ) {
            // Header Bar
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp, bottom = 6.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(42.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(NeoPrimaryContainer)
                            .border(1.dp, NeoBorder, RoundedCornerShape(10.dp))
                    ) {
                        Icon(
                            imageVector = Icons.Default.RestaurantMenu,
                            contentDescription = null,
                            tint = NeoPrimary,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = restaurantName,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = NeoTextPrimary,
                            modifier = Modifier.semantics { heading() }
                        )
                        Text(
                            text = "Navigable Menu (${sections.size} sections, $totalItemsCount items)",
                            fontSize = 12.sp,
                            color = NeoTextSecondary
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (isAudioPlaying) {
                        IconButton(
                            onClick = onStopNarration,
                            modifier = Modifier
                                .padding(end = 6.dp)
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(NonVegRedContainer)
                                .border(1.dp, NonVegRed, CircleShape)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Stop,
                                contentDescription = "Stop speech narration",
                                tint = NonVegRed,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    IconButton(
                        onClick = {
                            hapticEngine.trigger(HapticPattern.CLICK)
                            onStopNarration()
                            onClose()
                        },
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(NeoSurface)
                            .border(1.dp, NeoBorder, CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close Menu Browser",
                            tint = NeoTextPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            // Live Caption Bar for AI Dietary QA & Voice Queries
            LiveCaptionBar(
                fullCaption = liveCaptionText ?: "",
                isVisible = !liveCaptionText.isNullOrBlank(),
                modifier = Modifier.padding(bottom = 6.dp)
            )

            // Primary Action Row: Hear Menu Layout + Neomorphic Voice Assistant Mic + Voice Cart
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                // Hear Menu Layout Button
                val layoutInteraction = remember { MutableInteractionSource() }
                val isLayoutPressed by layoutInteraction.collectIsPressedAsState()
                val layoutScale by animateFloatAsState(
                    targetValue = if (isLayoutPressed) 0.96f else 1.0f,
                    animationSpec = spring(stiffness = Spring.StiffnessMedium),
                    label = "hear_layout_scale"
                )
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .weight(1f)
                        .height(46.dp)
                        .scale(layoutScale)
                        .neomorphicSurface(
                            shape = RoundedCornerShape(12.dp),
                            surfaceColor = if (isLayoutPressed) Color(0xFF1D4ED8) else NeoPrimary,
                            lightColor = Color(0x6693C5FD),
                            darkColor = Color(0x661E3A8A),
                            borderColor = Color(0xFF3B82F6),
                            elevation = 3.dp,
                            isPressed = isLayoutPressed,
                            cornerRadius = 12.dp
                        )
                        .clickable(
                            interactionSource = layoutInteraction,
                            indication = null,
                            onClick = {
                                hapticEngine.trigger(HapticPattern.CONFIRMATION)
                                earconEngine.play(EarconType.ORDER_CONFIRMED)
                                val overview = LayoutNarrator.generateLayoutSummary(restaurantName, sections, itemsBySection, selectedLanguage)
                                onSpeakNarration(overview)
                            }
                        )
                        .semantics {
                            contentDescription = "Hear Menu Layout"
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
                            tint = NeoOnPrimary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Hear Layout",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = NeoOnPrimary
                        )
                    }
                }

                // Prominent Neomorphic Voice Assistant Mic Button with Recording Animations
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(48.dp)
                        .scale(pulseScale)
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
                                else NeoPrimary.copy(alpha = 0.5f)
                            ),
                            CircleShape
                        )
                        .shadow(
                            elevation = if (isRecording) 6.dp else 2.dp,
                            shape = CircleShape,
                            spotColor = if (isRecording) NonVegRed.copy(alpha = 0.6f) else NeoDarkShadow,
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
                            contentDescription = if (isRecording) "Stop voice assistant" else "Ask Dietary AI Assistant"
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

                // Voice Cart Button
                val cartInteraction = remember { MutableInteractionSource() }
                val isCartPressed by cartInteraction.collectIsPressedAsState()
                val cartScale by animateFloatAsState(
                    targetValue = if (isCartPressed) 0.96f else 1.0f,
                    animationSpec = spring(stiffness = Spring.StiffnessMedium),
                    label = "voice_cart_scale"
                )
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .weight(1f)
                        .height(46.dp)
                        .scale(cartScale)
                        .neomorphicSurface(
                            shape = RoundedCornerShape(12.dp),
                            surfaceColor = if (isCartPressed) NeoSurfacePressed else NeoSurface,
                            elevation = 3.dp,
                            isPressed = isCartPressed,
                            cornerRadius = 12.dp
                        )
                        .clickable(
                            interactionSource = cartInteraction,
                            indication = null,
                            onClick = {
                                hapticEngine.trigger(HapticPattern.CLICK)
                                onStopNarration()
                                onOpenCart()
                            }
                        )
                        .semantics {
                            contentDescription = "Voice Cart"
                            role = Role.Button
                        }
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.ShoppingCart,
                            contentDescription = null,
                            tint = NeoTextPrimary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Voice Cart",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = NeoTextPrimary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Section Tabs (Neomorphic Pills)
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                itemsIndexed(sections) { index, section ->
                    val isSelected = index == selectedSectionIndex
                    val sectionTitle = section.titleNative ?: section.titleEnglish
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .height(42.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (isSelected) NeoPrimary else NeoSurface)
                            .border(
                                1.5.dp,
                                if (isSelected) NeoPrimary else NeoBorder,
                                RoundedCornerShape(10.dp)
                            )
                            .clickable {
                                hapticEngine.trigger(HapticPattern.CLICK)
                                selectedSectionIndex = index
                                val action = navigator.selectSection(index)
                                if (action is NavigationAction.ReadSection) {
                                    val summary = LayoutNarrator.generateSectionSummary(action.section, action.items, selectedLanguage)
                                    onSpeakNarration(summary)
                                }
                            }
                            .padding(horizontal = 16.dp)
                            .semantics {
                                contentDescription = "$sectionTitle section tab"
                            }
                    ) {
                        Text(
                            text = sectionTitle,
                            fontSize = 14.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            color = if (isSelected) NeoOnPrimary else NeoTextPrimary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Dietary Filters (Veg / Jain - Material Icons, Zero Emojis)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = filterVegOnly,
                    onClick = {
                        filterVegOnly = !filterVegOnly
                        hapticEngine.trigger(HapticPattern.CLICK)
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Eco,
                            contentDescription = null,
                            tint = if (filterVegOnly) VegGreen else NeoTextSecondary,
                            modifier = Modifier.size(16.dp)
                        )
                    },
                    label = {
                        Text(
                            text = "Pure Veg Only",
                            fontWeight = if (filterVegOnly) FontWeight.Bold else FontWeight.Normal
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        containerColor = NeoSurface,
                        labelColor = NeoTextPrimary,
                        selectedContainerColor = VegGreenContainer,
                        selectedLabelColor = VegGreenOnContainer
                    ),
                    border = FilterChipDefaults.filterChipBorder(
                        enabled = true,
                        selected = filterVegOnly,
                        borderColor = NeoBorder,
                        selectedBorderColor = VegGreen
                    )
                )

                FilterChip(
                    selected = filterJainOnly,
                    onClick = {
                        filterJainOnly = !filterJainOnly
                        hapticEngine.trigger(HapticPattern.CLICK)
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Shield,
                            contentDescription = null,
                            tint = if (filterJainOnly) JainYellow else NeoTextSecondary,
                            modifier = Modifier.size(16.dp)
                        )
                    },
                    label = {
                        Text(
                            text = "Jain Friendly",
                            fontWeight = if (filterJainOnly) FontWeight.Bold else FontWeight.Normal
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        containerColor = NeoSurface,
                        labelColor = NeoTextPrimary,
                        selectedContainerColor = JainYellowContainer,
                        selectedLabelColor = JainYellowOnContainer
                    ),
                    border = FilterChipDefaults.filterChipBorder(
                        enabled = true,
                        selected = filterJainOnly,
                        borderColor = NeoBorder,
                        selectedBorderColor = JainYellow
                    )
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Dish Items List (Neomorphic Item Cards with AI Highlighting)
            if (filteredItems.isEmpty()) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.SearchOff,
                            contentDescription = null,
                            tint = NeoTextMuted,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "No dishes found in this category",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = NeoTextSecondary
                        )
                    }
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    items(filteredItems, key = { it.itemId }) { item ->
                        val isHighlighted = (activeItem?.itemId == item.itemId) || (highlightedItemId == item.itemId)
                        val priceWords = NumberVerbalizer.verbalizePrice(item.priceRupees, selectedLanguage)

                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = if (isHighlighted) NeoPrimaryContainer.copy(alpha = 0.25f) else NeoSurface
                            ),
                            shape = RoundedCornerShape(16.dp),
                            border = BorderStroke(
                                width = if (isHighlighted) 2.5.dp else 1.5.dp,
                                color = if (isHighlighted) NeoPrimary else NeoBorder
                            ),
                            elevation = CardDefaults.cardElevation(defaultElevation = if (isHighlighted) 4.dp else 2.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .animateContentSize()
                                .semantics(mergeDescendants = true) {
                                    contentDescription = "${item.nameEnglish}, ${if (item.isVegetarian) "Vegetarian" else "Non-vegetarian"}${if (item.isJain) ", Jain friendly" else ""}, ${item.priceRupees} rupees. Double tap to hear description or add to cart."
                                }
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                text = item.nameEnglish,
                                                fontSize = 17.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = NeoTextPrimary
                                            )
                                            if (isHighlighted) {
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Box(
                                                    modifier = Modifier
                                                        .clip(RoundedCornerShape(4.dp))
                                                        .background(NeoPrimary)
                                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                                ) {
                                                    Text(
                                                        text = "MATCH",
                                                        fontSize = 10.sp,
                                                        fontWeight = FontWeight.ExtraBold,
                                                        color = NeoOnPrimary
                                                    )
                                                }
                                            }
                                        }
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = "₹${item.priceRupees} • $priceWords",
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = NeoPrimary
                                        )
                                    }

                                    // Dietary Badge Icon
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        if (item.isJain) {
                                            Box(
                                                contentAlignment = Alignment.Center,
                                                modifier = Modifier
                                                    .size(24.dp)
                                                    .border(1.5.dp, JainYellow, RoundedCornerShape(4.dp))
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Shield,
                                                    contentDescription = "Jain Friendly",
                                                    tint = JainYellow,
                                                    modifier = Modifier.size(14.dp)
                                                )
                                            }
                                        }

                                        Box(
                                            contentAlignment = Alignment.Center,
                                            modifier = Modifier
                                                .size(24.dp)
                                                .border(
                                                    1.5.dp,
                                                    if (item.isVegetarian) VegGreen else NonVegRed,
                                                    RoundedCornerShape(4.dp)
                                                )
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(10.dp)
                                                    .clip(CircleShape)
                                                    .background(if (item.isVegetarian) VegGreen else NonVegRed)
                                            )
                                        }
                                    }
                                }

                                item.description?.let { desc ->
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = desc,
                                        fontSize = 13.sp,
                                        color = NeoTextSecondary,
                                        lineHeight = 18.sp
                                    )
                                }

                                Spacer(modifier = Modifier.height(12.dp))

                                // Action Row (Read aloud & Add to cart with Neomorphic buttons)
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Box(
                                        contentAlignment = Alignment.Center,
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(42.dp)
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(NeoSurfacePressed)
                                            .border(1.dp, NeoBorder, RoundedCornerShape(10.dp))
                                            .clickable {
                                                hapticEngine.trigger(HapticPattern.CLICK)
                                                navigator.selectItem(item)
                                                highlightedItemId = item.itemId
                                                val itemNarration = LayoutNarrator.generateItemSpokenDescription(item, selectedLanguage)
                                                onSpeakNarration(itemNarration)
                                            }
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                                                contentDescription = null,
                                                tint = NeoTextPrimary,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = "Read Aloud",
                                                fontSize = 13.sp,
                                                color = NeoTextPrimary,
                                                fontWeight = FontWeight.Medium
                                            )
                                        }
                                    }

                                    Box(
                                        contentAlignment = Alignment.Center,
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(42.dp)
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(NeoPrimary)
                                            .border(1.dp, NeoPrimaryContainer, RoundedCornerShape(10.dp))
                                            .clickable {
                                                hapticEngine.trigger(HapticPattern.CONFIRMATION)
                                                earconEngine.play(EarconType.ORDER_CONFIRMED)
                                                onAddItemToCart(item)
                                                val confirmText = when (selectedLanguage) {
                                                    "te" -> "${item.nameEnglish} ఆర్డర్‌లో చేర్చబడింది."
                                                    "hi" -> "${item.nameEnglish} ऑर्डर में जोड़ दिया गया है।"
                                                    else -> "Added ${item.nameEnglish} to your order."
                                                }
                                                onSpeakNarration(confirmText)
                                            }
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                imageVector = Icons.Default.Add,
                                                contentDescription = null,
                                                tint = NeoOnPrimary,
                                                modifier = Modifier.size(18.dp)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = "Add to Order",
                                                fontSize = 13.sp,
                                                color = NeoOnPrimary,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Bottom Bar with View Cart Quick Action
            Card(
                colors = CardDefaults.cardColors(containerColor = NeoSurface),
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.5.dp, NeoBorder),
                elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        hapticEngine.trigger(HapticPattern.CLICK)
                        onStopNarration()
                        onOpenCart()
                    }
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(NeoPrimaryContainer)
                        ) {
                            Icon(
                                imageVector = Icons.Default.ShoppingCart,
                                contentDescription = null,
                                tint = NeoPrimary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Ready to Order?",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = NeoTextPrimary
                            )
                            Text(
                                text = "Tap to review items in voice cart",
                                fontSize = 12.sp,
                                color = NeoTextSecondary
                            )
                        }
                    }

                    Icon(
                        imageVector = Icons.Default.ChevronRight,
                        contentDescription = "Open Cart",
                        tint = NeoPrimary,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}
