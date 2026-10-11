package com.sarvaai.app.ui.screens.waiter

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
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
import androidx.compose.material.icons.automirrored.filled.Accessible
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sarvaai.app.audio.earcons.EarconEngine
import com.sarvaai.app.audio.earcons.EarconType
import com.sarvaai.app.audio.haptics.HapticFeedbackEngine
import com.sarvaai.app.audio.haptics.HapticPattern
import com.sarvaai.app.audio.recorder.AudioRecorder
import com.sarvaai.app.bhashini.asr.AsrNmtClient
import com.sarvaai.app.llm.GroqClient
import com.sarvaai.app.service.waiter.ComplaintFeedbackBoard
import com.sarvaai.app.service.waiter.ComplaintFeedbackItem
import com.sarvaai.app.service.waiter.PhraseBoard
import com.sarvaai.app.service.waiter.PhraseChip
import com.sarvaai.app.service.waiter.WaiterBridgeController
import com.sarvaai.app.service.wakeword.WakeWordDetector
import com.sarvaai.app.ui.theme.*
import kotlinx.coroutines.launch

data class RaisedComplaintData(
    val dinerText: String,
    val staffText: String,
    val categoryTitle: String,
    val isCompliment: Boolean = false,
    val timestamp: Long = System.currentTimeMillis()
)

@Composable
fun WaiterBridgeScreen(
    asrNmtClient: AsrNmtClient,
    groqClient: GroqClient,
    audioRecorder: AudioRecorder,
    hapticEngine: HapticFeedbackEngine,
    earconEngine: EarconEngine,
    initialDinerLanguage: String = "te",
    initialStaffLanguage: String = "hi",
    isAudioPlaying: Boolean = false,
    onSpeakAudio: (text: String, lang: String) -> Unit,
    onStopAudio: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val controller = remember { WaiterBridgeController(asrNmtClient, groqClient) }
    val state by controller.state.collectAsState()
    val isRecording by audioRecorder.isRecording.collectAsState()

    var selectedCategory by remember { mutableStateOf("DISABILITY") }
    var recordingTarget by remember { mutableStateOf<String?>(null) } // "DINER", "STAFF", "FEEDBACK"
    var activeRaisedComplaint by remember { mutableStateOf<RaisedComplaintData?>(null) }
    var showComplaintConfirmation by remember { mutableStateOf(false) }
    var dinerDropdownOpen by remember { mutableStateOf(false) }
    var staffDropdownOpen by remember { mutableStateOf(false) }
    val bridgeLanguages = remember {
        listOf(
            Pair("te", "తెలుగు"),
            Pair("hi", "हिंदी"),
            Pair("ta", "தமிழ்"),
            Pair("kn", "ಕನ್ನಡ"),
            Pair("ml", "മലയാളം"),
            Pair("en", "English")
        )
    }

    // Wave animations for listening state
    val infiniteTransition = rememberInfiniteTransition(label = "micPulse")
    val micPulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.35f,
        animationSpec = infiniteRepeatable(
            animation = tween(650, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )
    val micPulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.6f,
        targetValue = 0.1f,
        animationSpec = infiniteRepeatable(
            animation = tween(650, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    // BackHandler to safely return to home
    BackHandler {
        onStopAudio()
        onClose()
    }

    fun handleCloseCommand(lang: String) {
        onStopAudio()
        earconEngine.play(EarconType.LISTEN_STOP)
        hapticEngine.trigger(HapticPattern.CONFIRMATION)
        val goodbyeMessage = WakeWordDetector.getGoodbyeMessage(lang)
        onSpeakAudio(goodbyeMessage, lang)
        val closeIntent = Intent(WakeWordDetector.ACTION_CLOSE_APP).apply {
            setPackage(context.packageName)
        }
        context.sendBroadcast(closeIntent)
        onClose()
    }

    LaunchedEffect(initialDinerLanguage, initialStaffLanguage) {
        controller.setDinerLanguage(initialDinerLanguage)
        controller.setStaffLanguage(initialStaffLanguage)
    }

    DisposableEffect(Unit) {
        onDispose {
            onStopAudio()
            if (audioRecorder.isRecording.value) {
                audioRecorder.stopRecording()
            }
        }
    }

    // 1. DINER MIC HANDLER ("Speak to Waiter")
    fun handleDinerMicClick() {
        if (isRecording) {
            hapticEngine.trigger(HapticPattern.CLICK)
            earconEngine.play(EarconType.LISTEN_STOP)
            audioRecorder.stopRecording()
            recordingTarget = null
        } else {
            onStopAudio()
            recordingTarget = "DINER"
            hapticEngine.trigger(HapticPattern.SPEECH_TURN_CUE)
            earconEngine.play(EarconType.LISTEN_START)
            audioRecorder.startRecording(
                onUtteranceComplete = { wavBytes ->
                    hapticEngine.trigger(HapticPattern.CONFIRMATION)
                    earconEngine.play(EarconType.LISTEN_STOP)
                    scope.launch {
                        controller.processDinerSpeech(
                            wavBytes = wavBytes,
                            onCloseCommand = {
                                handleCloseCommand(state.dinerLanguage)
                            },
                            onSpeakToStaff = { staffText, staffLang ->
                                onSpeakAudio(staffText, staffLang)
                            }
                        )
                    }
                    recordingTarget = null
                },
                onError = {
                    recordingTarget = null
                    hapticEngine.trigger(HapticPattern.ERROR_ALERT)
                    earconEngine.play(EarconType.ERROR_ALERT)
                }
            )
        }
    }

    // 2. STAFF MIC HANDLER ("Waiter Response")
    fun handleStaffMicClick() {
        if (isRecording) {
            hapticEngine.trigger(HapticPattern.CLICK)
            earconEngine.play(EarconType.LISTEN_STOP)
            audioRecorder.stopRecording()
            recordingTarget = null
        } else {
            onStopAudio()
            recordingTarget = "STAFF"
            hapticEngine.trigger(HapticPattern.SPEECH_TURN_CUE)
            earconEngine.play(EarconType.LISTEN_START)
            audioRecorder.startRecording(
                onUtteranceComplete = { wavBytes ->
                    hapticEngine.trigger(HapticPattern.CONFIRMATION)
                    earconEngine.play(EarconType.LISTEN_STOP)
                    scope.launch {
                        controller.processStaffSpeech(
                            wavBytes = wavBytes,
                            onCloseCommand = {
                                handleCloseCommand(state.staffLanguage)
                            },
                            onSpeakToDiner = { dinerText, dinerLang ->
                                onSpeakAudio(dinerText, dinerLang)
                            }
                        )
                    }
                    recordingTarget = null
                },
                onError = {
                    recordingTarget = null
                    hapticEngine.trigger(HapticPattern.ERROR_ALERT)
                    earconEngine.play(EarconType.ERROR_ALERT)
                }
            )
        }
    }

    // 3. FEEDBACK MIC HANDLER ("Raise Complaint or Feedback")
    fun handleFeedbackMicClick() {
        if (isRecording) {
            hapticEngine.trigger(HapticPattern.CLICK)
            earconEngine.play(EarconType.LISTEN_STOP)
            audioRecorder.stopRecording()
            recordingTarget = null
        } else {
            onStopAudio()
            recordingTarget = "FEEDBACK"
            hapticEngine.trigger(HapticPattern.SPEECH_TURN_CUE)
            earconEngine.play(EarconType.LISTEN_START)
            audioRecorder.startRecording(
                onUtteranceComplete = { wavBytes ->
                    hapticEngine.trigger(HapticPattern.CONFIRMATION)
                    earconEngine.play(EarconType.LISTEN_STOP)
                    scope.launch {
                        controller.processFeedbackSpeech(
                            wavBytes = wavBytes,
                            onSuccess = { dinerText, staffText ->
                                val isCompliment = dinerText.contains("delicious", ignoreCase = true) ||
                                        dinerText.contains("good", ignoreCase = true) ||
                                        dinerText.contains("బాగుంది", ignoreCase = true) ||
                                        dinerText.contains("రుచి", ignoreCase = true) ||
                                        dinerText.contains("स्वादिष्ट", ignoreCase = true) ||
                                        dinerText.contains("बधाई", ignoreCase = true)
                                activeRaisedComplaint = RaisedComplaintData(
                                    dinerText = dinerText,
                                    staffText = staffText,
                                    categoryTitle = if (isCompliment) "Compliment" else "Spoken Feedback",
                                    isCompliment = isCompliment
                                )
                                showComplaintConfirmation = true
                            },
                            onSpeakToStaff = { staffText, staffLang ->
                                onSpeakAudio(staffText, staffLang)
                            }
                        )
                    }
                    recordingTarget = null
                },
                onError = {
                    recordingTarget = null
                    hapticEngine.trigger(HapticPattern.ERROR_ALERT)
                    earconEngine.play(EarconType.ERROR_ALERT)
                }
            )
        }
    }

    // 4. ONE-TAP FEEDBACK CHIP HANDLER
    fun handleFeedbackChipClick(item: ComplaintFeedbackItem) {
        hapticEngine.trigger(HapticPattern.CONFIRMATION)
        earconEngine.play(EarconType.ORDER_CONFIRMED)
        val dinerText = item.getTextForLanguage(state.dinerLanguage)
        val staffText = item.getTextForLanguage(state.staffLanguage)

        controller.setStaffCardMessage(staffText, dinerText)
        controller.setDinerCaption("📢 Feedback: $dinerText")

        activeRaisedComplaint = RaisedComplaintData(
            dinerText = dinerText,
            staffText = staffText,
            categoryTitle = item.title,
            isCompliment = item.isCompliment
        )
        showComplaintConfirmation = true

        onSpeakAudio(staffText, state.staffLanguage)
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
            // 1. COMPACT SMALL TITLE HEADER (Clean 16sp bold title, zero extra top spacing)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 0.dp, bottom = 6.dp)
            ) {
                Text(
                    text = "Waiter Bridge",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = NeoTextPrimary,
                    modifier = Modifier
                        .weight(1f)
                        .semantics { heading() }
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // Flip Card Button
                    IconButton(
                        onClick = {
                            hapticEngine.trigger(HapticPattern.CLICK)
                            controller.toggleFlipForStaff()
                        },
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(if (state.isFlippedForStaff) NeoPrimary else NeoSurface)
                            .border(1.dp, NeoBorder, CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Flip,
                            contentDescription = "Flip staff card for waiter viewing",
                            tint = if (state.isFlippedForStaff) NeoOnPrimary else NeoTextPrimary,
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    if (isAudioPlaying) {
                        IconButton(
                            onClick = onStopAudio,
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(NonVegRedContainer)
                                .border(1.dp, NonVegRed, CircleShape)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Stop,
                                contentDescription = "Stop speech playback",
                                tint = NonVegRed,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }

                    IconButton(
                        onClick = {
                            hapticEngine.trigger(HapticPattern.CLICK)
                            onStopAudio()
                            onClose()
                        },
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(NeoSurface)
                            .border(1.dp, NeoBorder, CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close Waiter Bridge",
                            tint = NeoTextPrimary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            // 2. LANGUAGE SELECTOR ROW (Diner Lang <-> Staff Lang)
            Card(
                colors = CardDefaults.cardColors(containerColor = NeoSurface),
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.5.dp, NeoBorder),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Diner Lang Selector
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.RecordVoiceOver,
                                contentDescription = null,
                                tint = NeoPrimary,
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "You Speak / Read",
                                fontSize = 11.sp,
                                color = NeoTextSecondary,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        Spacer(modifier = Modifier.height(5.dp))
                        Box(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(34.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(NeoSurfacePressed)
                                    .border(1.dp, NeoPrimary, RoundedCornerShape(8.dp))
                                    .clickable { dinerDropdownOpen = true }
                                    .padding(horizontal = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = bridgeLanguages.find { it.first == state.dinerLanguage }?.second ?: "తెలుగు",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = NeoPrimary
                                )
                                Icon(
                                    imageVector = Icons.Default.ArrowDropDown,
                                    contentDescription = null,
                                    tint = NeoPrimary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            DropdownMenu(
                                expanded = dinerDropdownOpen,
                                onDismissRequest = { dinerDropdownOpen = false }
                            ) {
                                bridgeLanguages.forEach { (code, label) ->
                                    val isSelected = state.dinerLanguage == code
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                text = label,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                                color = if (isSelected) NeoPrimary else NeoTextPrimary
                                            )
                                        },
                                        onClick = {
                                            dinerDropdownOpen = false
                                            hapticEngine.trigger(HapticPattern.CLICK)
                                            controller.setDinerLanguage(code)
                                        }
                                    )
                                }
                            }
                        }
                    }

                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = "to",
                        tint = NeoPrimary,
                        modifier = Modifier
                            .padding(horizontal = 6.dp)
                            .size(16.dp)
                    )

                    // Staff Lang Selector
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Badge,
                                contentDescription = null,
                                tint = Color(0xFF0D9488),
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Staff Reads / Hears",
                                fontSize = 11.sp,
                                color = NeoTextSecondary,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        Spacer(modifier = Modifier.height(5.dp))
                        Box(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(34.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(NeoSurfacePressed)
                                    .border(1.dp, Color(0xFF0D9488), RoundedCornerShape(8.dp))
                                    .clickable { staffDropdownOpen = true }
                                    .padding(horizontal = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = bridgeLanguages.find { it.first == state.staffLanguage }?.second ?: "हिंदी",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF0D9488)
                                )
                                Icon(
                                    imageVector = Icons.Default.ArrowDropDown,
                                    contentDescription = null,
                                    tint = Color(0xFF0D9488),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            DropdownMenu(
                                expanded = staffDropdownOpen,
                                onDismissRequest = { staffDropdownOpen = false }
                            ) {
                                bridgeLanguages.forEach { (code, label) ->
                                    val isSelected = state.staffLanguage == code
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                text = label,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                                color = if (isSelected) Color(0xFF0D9488) else NeoTextPrimary
                                            )
                                        },
                                        onClick = {
                                            staffDropdownOpen = false
                                            hapticEngine.trigger(HapticPattern.CLICK)
                                            controller.setStaffLanguage(code)
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // 3. PROMINENT STICKY MIC ACTION BAR (Always Accessible & Never Scrolled Off-Screen)
            val isDinerRecording = isRecording && recordingTarget == "DINER"
            val isStaffRecording = isRecording && recordingTarget == "STAFF"

            Card(
                colors = CardDefaults.cardColors(containerColor = NeoSurface),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(
                    1.5.dp,
                    if (isDinerRecording) NonVegRed else if (isStaffRecording) Color(0xFF0D9488) else NeoBorder
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { contentDescription = "Sticky voice communication bar" }
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 3A. PROMINENT DINER MIC ("Speak to Waiter")
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .weight(1.15f)
                            .height(56.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (isDinerRecording) NonVegRed else NeoPrimary)
                            .border(
                                1.5.dp,
                                if (isDinerRecording) NonVegRedContainer else NeoPrimaryContainer,
                                RoundedCornerShape(12.dp)
                            )
                            .clickable { handleDinerMicClick() }
                            .semantics {
                                contentDescription = if (isDinerRecording) "Stop speaking to waiter" else "Speak to waiter in ${state.dinerLanguage.uppercase()}"
                            }
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center,
                            modifier = Modifier.padding(horizontal = 8.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(34.dp)) {
                                if (isDinerRecording) {
                                    Box(
                                        modifier = Modifier
                                            .size(34.dp)
                                            .scale(micPulseScale)
                                            .background(Color.White.copy(alpha = micPulseAlpha), CircleShape)
                                    )
                                }
                                Icon(
                                    imageVector = if (isDinerRecording) Icons.Default.GraphicEq else Icons.Default.Mic,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(6.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = if (isDinerRecording) "Listening..." else "Speak to Waiter",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                                Text(
                                    text = if (isDinerRecording) "Tap to Send" else "To Staff (${state.staffLanguage.uppercase()})",
                                    fontSize = 10.sp,
                                    color = Color.White.copy(alpha = 0.85f)
                                )
                            }
                            if (isDinerRecording) {
                                AudioWaveBars(isActive = true, tint = Color.White)
                            }
                        }
                    }

                    // 3B. STAFF MIC ("Waiter Response")
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .weight(1f)
                            .height(56.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (isStaffRecording) NonVegRed else Color(0xFF0D9488))
                            .border(
                                1.5.dp,
                                if (isStaffRecording) NonVegRedContainer else Color(0xFF99F6E4),
                                RoundedCornerShape(12.dp)
                            )
                            .clickable { handleStaffMicClick() }
                            .semantics {
                                contentDescription = if (isStaffRecording) "Stop waiter listening" else "Hold for waiter response in ${state.staffLanguage.uppercase()}"
                            }
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center,
                            modifier = Modifier.padding(horizontal = 8.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(34.dp)) {
                                if (isStaffRecording) {
                                    Box(
                                        modifier = Modifier
                                            .size(34.dp)
                                            .scale(micPulseScale)
                                            .background(Color.White.copy(alpha = micPulseAlpha), CircleShape)
                                    )
                                }
                                Icon(
                                    imageVector = if (isStaffRecording) Icons.Default.GraphicEq else Icons.Default.Hearing,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(6.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = if (isStaffRecording) "Listening..." else "Waiter Response",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                                Text(
                                    text = if (isStaffRecording) "Tap to Stop" else "Waiter (${state.staffLanguage.uppercase()})",
                                    fontSize = 10.sp,
                                    color = Color.White.copy(alpha = 0.85f)
                                )
                            }
                            if (isStaffRecording) {
                                AudioWaveBars(isActive = true, tint = Color.White)
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // 4. SCROLLABLE BRIDGE CONTENT (Staff card, Live translation, Phrases, & Raise Complaint Card at bottom)
            val activePhrases = remember(selectedCategory) {
                PhraseBoard.getPhrasesByCategory(selectedCategory)
            }

            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                // ITEM 1: OUTWARD-FACING STAFF CARD (Clean High-Contrast Typography, Flip Button)
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = NeoSurface),
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(2.dp, NeoPrimary),
                        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .rotate(if (state.isFlippedForStaff) 180f else 0f)
                            .semantics(mergeDescendants = true) {
                                contentDescription = "Staff display showing: ${state.activeStaffCardText}"
                            }
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.Visibility,
                                        contentDescription = null,
                                        tint = NeoPrimary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "SHOW TO WAITER / STAFF",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = NeoPrimary,
                                        letterSpacing = 0.8.sp
                                    )
                                }

                                IconButton(
                                    onClick = {
                                        hapticEngine.trigger(HapticPattern.CLICK)
                                        onSpeakAudio(state.activeStaffCardText, state.staffLanguage)
                                    },
                                    modifier = Modifier
                                        .size(32.dp)
                                        .clip(CircleShape)
                                        .background(NeoPrimaryContainer)
                                        .border(1.dp, NeoPrimary.copy(alpha = 0.4f), CircleShape)
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                                        contentDescription = "Play loudly to staff",
                                        tint = NeoPrimary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(6.dp))

                            Text(
                                text = state.activeStaffCardText,
                                fontSize = 26.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = NeoTextPrimary,
                                textAlign = TextAlign.Center,
                                lineHeight = 32.sp
                            )

                            if (state.activeStaffCardSubtext.isNotBlank()) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = state.activeStaffCardSubtext,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = NeoTextSecondary,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                }

                // ITEM 2: LIVE DINER TRANSLATION / WAITER RESPONSE CARD
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = NeoSurface),
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.5.dp, NeoBorder),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.Translate,
                                        contentDescription = null,
                                        tint = NeoPrimary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "LIVE TRANSLATION / WAITER REPLIES",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = NeoTextSecondary,
                                        letterSpacing = 0.5.sp
                                    )
                                }

                                if (state.isProcessing) {
                                    CircularProgressIndicator(
                                        color = NeoPrimary,
                                        modifier = Modifier.size(14.dp),
                                        strokeWidth = 2.dp
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(6.dp))

                            Text(
                                text = state.liveDinerCaption,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = NeoTextPrimary,
                                lineHeight = 20.sp
                            )
                        }
                    }
                }

                // ITEM 3: INSTANT COMMUNICATION PHRASE CATEGORY TABS
                item {
                    Column {
                        Text(
                            text = "Instant Communication Phrases",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = NeoTextPrimary
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            val categories = listOf(
                                Triple("DISABILITY", "Disability Notice", Icons.AutoMirrored.Filled.Accessible),
                                Triple("ESSENTIALS", "Water & Bill", Icons.Default.LocalDrink),
                                Triple("FOOD_PREF", "Food Specs", Icons.Default.Restaurant),
                                Triple("SERVICE", "Table Service", Icons.Default.RoomService)
                            )
                            items(categories) { (catKey, catLabel, icon) ->
                                val isSelected = selectedCategory == catKey
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier
                                        .height(34.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(if (isSelected) NeoPrimary else NeoSurface)
                                        .border(
                                            1.dp,
                                            if (isSelected) NeoPrimary else NeoBorder,
                                            RoundedCornerShape(10.dp)
                                        )
                                        .clickable {
                                            hapticEngine.trigger(HapticPattern.CLICK)
                                            selectedCategory = catKey
                                        }
                                        .padding(horizontal = 10.dp)
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = icon,
                                            contentDescription = null,
                                            tint = if (isSelected) NeoOnPrimary else NeoTextSecondary,
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = catLabel,
                                            fontSize = 11.sp,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                            color = if (isSelected) NeoOnPrimary else NeoTextPrimary
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // ITEMS 4: QUICK PHRASE CARDS
                items(activePhrases, key = { it.id }) { phrase ->
                    val dinerDisplay = phrase.getTextForLanguage(state.dinerLanguage)
                    val staffDisplay = phrase.getTextForLanguage(state.staffLanguage)
                    val phraseIcon = getPhraseMaterialIcon(phrase.iconName)

                    Card(
                        colors = CardDefaults.cardColors(containerColor = NeoSurface),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, NeoBorder),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                hapticEngine.trigger(HapticPattern.CONFIRMATION)
                                earconEngine.play(EarconType.ORDER_CONFIRMED)
                                controller.selectPhrase(phrase) { staffText, staffLang ->
                                    onSpeakAudio(staffText, staffLang)
                                }
                            }
                            .semantics(mergeDescendants = true) {
                                contentDescription = "Tap to tell staff: $dinerDisplay. Translates to: $staffDisplay."
                            }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(NeoSurfacePressed)
                                    .border(1.dp, NeoBorder, RoundedCornerShape(8.dp))
                            ) {
                                Icon(
                                    imageVector = phraseIcon,
                                    contentDescription = null,
                                    tint = NeoPrimary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }

                            Spacer(modifier = Modifier.width(10.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = dinerDisplay,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = NeoTextPrimary
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "Staff will hear: \"$staffDisplay\"",
                                    fontSize = 11.sp,
                                    color = NeoTextSecondary
                                )
                            }

                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                                contentDescription = null,
                                tint = NeoPrimary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }

                // ITEM 5: RAISE COMPLAINT OR FEEDBACK FEATURE (AT BOTTOM OF BRIDGE PAGE)
                item {
                    val isFeedbackRecording = isRecording && recordingTarget == "FEEDBACK"

                    Card(
                        colors = CardDefaults.cardColors(containerColor = NeoSurface),
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.5.dp, Color(0xFFF59E0B).copy(alpha = 0.5f)),
                        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .semantics(mergeDescendants = true) {
                                contentDescription = "Raise Complaint or Feedback Section"
                            }
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp)
                        ) {
                            // Header
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Color(0xFFFEF3C7))
                                        .border(1.dp, Color(0xFFF59E0B).copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Campaign,
                                        contentDescription = null,
                                        tint = Color(0xFFD97706),
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Raise Complaint or Feedback",
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = NeoTextPrimary
                                    )
                                    Text(
                                        text = "Speak feedback or issue in any language. Svara translates & speaks to staff.",
                                        fontSize = 11.sp,
                                        color = NeoTextSecondary,
                                        lineHeight = 15.sp
                                    )
                                }
                            }

                            // LIVE CARD / CONFIRMATION CARD ("Raised to Floor Manager / Captain")
                            if (activeRaisedComplaint != null && showComplaintConfirmation) {
                                Spacer(modifier = Modifier.height(12.dp))
                                Card(
                                    colors = CardDefaults.cardColors(
                                        containerColor = if (activeRaisedComplaint!!.isCompliment) Color(0xFFF0FDF4) else Color(0xFFFFFBEB)
                                    ),
                                    shape = RoundedCornerShape(12.dp),
                                    border = BorderStroke(
                                        1.5.dp,
                                        if (activeRaisedComplaint!!.isCompliment) Color(0xFF16A34A) else Color(0xFFD97706)
                                    ),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(
                                                    imageVector = if (activeRaisedComplaint!!.isCompliment) Icons.Default.ThumbUp else Icons.Default.CheckCircle,
                                                    contentDescription = null,
                                                    tint = if (activeRaisedComplaint!!.isCompliment) Color(0xFF16A34A) else Color(0xFFD97706),
                                                    modifier = Modifier.size(16.dp)
                                                )
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text(
                                                    text = if (activeRaisedComplaint!!.isCompliment) "Compliment Shared with Captain & Chef" else "Raised to Floor Manager / Captain",
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = if (activeRaisedComplaint!!.isCompliment) Color(0xFF15803D) else Color(0xFFB45309)
                                                )
                                            }
                                            IconButton(
                                                onClick = { showComplaintConfirmation = false },
                                                modifier = Modifier.size(24.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Close,
                                                    contentDescription = "Dismiss confirmation",
                                                    tint = NeoTextSecondary,
                                                    modifier = Modifier.size(14.dp)
                                                )
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(6.dp))

                                        // Category Tag
                                        Text(
                                            text = "CATEGORY: ${activeRaisedComplaint!!.categoryTitle.uppercase()}",
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = NeoTextSecondary,
                                            letterSpacing = 0.5.sp
                                        )

                                        Spacer(modifier = Modifier.height(4.dp))

                                        // Diner utterance
                                        Text(
                                            text = "You (${state.dinerLanguage.uppercase()}): \"${activeRaisedComplaint!!.dinerText}\"",
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = NeoTextPrimary
                                        )

                                        Spacer(modifier = Modifier.height(4.dp))

                                        // Translated utterance spoken to staff
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = "🗣️ Spoken to Staff (${state.staffLanguage.uppercase()}): \"${activeRaisedComplaint!!.staffText}\"",
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = if (activeRaisedComplaint!!.isCompliment) Color(0xFF15803D) else Color(0xFFB45309),
                                                modifier = Modifier.weight(1f)
                                            )
                                            IconButton(
                                                onClick = {
                                                    hapticEngine.trigger(HapticPattern.CLICK)
                                                    onSpeakAudio(activeRaisedComplaint!!.staffText, state.staffLanguage)
                                                },
                                                modifier = Modifier
                                                    .size(30.dp)
                                                    .clip(CircleShape)
                                                    .background(if (activeRaisedComplaint!!.isCompliment) Color(0xFFDCFCE7) else Color(0xFFFEF3C7))
                                            ) {
                                                Icon(
                                                    imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                                                    contentDescription = "Replay to staff",
                                                    tint = if (activeRaisedComplaint!!.isCompliment) Color(0xFF16A34A) else Color(0xFFD97706),
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            // Microphone Button to speak complaint/feedback
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(46.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(if (isFeedbackRecording) NonVegRed else NeoSurfacePressed)
                                    .border(
                                        1.5.dp,
                                        if (isFeedbackRecording) NonVegRedContainer else Color(0xFFF59E0B).copy(alpha = 0.5f),
                                        RoundedCornerShape(12.dp)
                                    )
                                    .clickable { handleFeedbackMicClick() }
                                    .semantics {
                                        contentDescription = if (isFeedbackRecording) "Stop recording feedback" else "Speak complaint or feedback in your language"
                                    }
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center,
                                    modifier = Modifier.padding(horizontal = 10.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(28.dp)) {
                                        if (isFeedbackRecording) {
                                            Box(
                                                modifier = Modifier
                                                    .size(28.dp)
                                                    .scale(micPulseScale)
                                                    .background(Color.White.copy(alpha = micPulseAlpha), CircleShape)
                                            )
                                        }
                                        Icon(
                                            imageVector = if (isFeedbackRecording) Icons.Default.GraphicEq else Icons.Default.Mic,
                                            contentDescription = null,
                                            tint = if (isFeedbackRecording) Color.White else Color(0xFFD97706),
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = if (isFeedbackRecording) "Listening to Feedback... Tap to Send" else "Tap Mic to Speak Issue in ${when(state.dinerLanguage) { "te" -> "తెలుగు"; "hi" -> "हिंदी"; else -> "English" }}",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isFeedbackRecording) Color.White else NeoTextPrimary
                                    )
                                    if (isFeedbackRecording) {
                                        Spacer(modifier = Modifier.width(8.dp))
                                        AudioWaveBars(isActive = true, tint = Color.White)
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            Text(
                                text = "Quick One-Tap Feedback Issues",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = NeoTextPrimary
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            // 6 Feedback Category Chips (in 3 rows of 2)
                            val feedbackChips = remember { ComplaintFeedbackBoard.ITEMS }
                            feedbackChips.chunked(2).forEach { pair ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    pair.forEach { chipItem ->
                                        val isSelected = activeRaisedComplaint?.categoryTitle == chipItem.title
                                        Card(
                                            colors = CardDefaults.cardColors(
                                                containerColor = if (isSelected) NeoSurfacePressed else NeoSurface
                                            ),
                                            shape = RoundedCornerShape(10.dp),
                                            border = BorderStroke(
                                                1.dp,
                                                if (isSelected) Color(0xFFF59E0B) else NeoBorder
                                            ),
                                            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                                            modifier = Modifier
                                                .weight(1f)
                                                .clickable { handleFeedbackChipClick(chipItem) }
                                                .semantics(mergeDescendants = true) {
                                                    contentDescription = "One tap issue: ${chipItem.title}"
                                                }
                                        ) {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(horizontal = 8.dp, vertical = 9.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Box(
                                                    contentAlignment = Alignment.Center,
                                                    modifier = Modifier
                                                        .size(28.dp)
                                                        .clip(RoundedCornerShape(6.dp))
                                                        .background(if (chipItem.isCompliment) Color(0xFFDCFCE7) else Color(0xFFFEF3C7))
                                                ) {
                                                    Icon(
                                                        imageVector = getFeedbackMaterialIcon(chipItem.iconName),
                                                        contentDescription = null,
                                                        tint = if (chipItem.isCompliment) Color(0xFF16A34A) else Color(0xFFD97706),
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                }
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text(
                                                    text = chipItem.title,
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = NeoTextPrimary,
                                                    lineHeight = 14.sp
                                                )
                                            }
                                        }
                                    }
                                    if (pair.size == 1) {
                                        Spacer(modifier = Modifier.weight(1f))
                                    }
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                            }
                        }
                    }
                }

                // ITEM 6: BOTTOM SPACING
                item {
                    Spacer(modifier = Modifier.height(24.dp))
                }
            }
        }
    }
}

@Composable
private fun AudioWaveBars(
    isActive: Boolean,
    tint: Color = Color.White,
    modifier: Modifier = Modifier
) {
    if (isActive) {
        val transition = rememberInfiniteTransition(label = "waveBars")
        val h1 by transition.animateFloat(
            initialValue = 4f,
            targetValue = 18f,
            animationSpec = infiniteRepeatable(
                animation = tween(320, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "h1"
        )
        val h2 by transition.animateFloat(
            initialValue = 16f,
            targetValue = 6f,
            animationSpec = infiniteRepeatable(
                animation = tween(260, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "h2"
        )
        val h3 by transition.animateFloat(
            initialValue = 8f,
            targetValue = 20f,
            animationSpec = infiniteRepeatable(
                animation = tween(400, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "h3"
        )

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.5.dp),
            modifier = modifier
        ) {
            Box(modifier = Modifier.width(3.dp).height(h1.dp).background(tint, RoundedCornerShape(1.5.dp)))
            Box(modifier = Modifier.width(3.dp).height(h2.dp).background(tint, RoundedCornerShape(1.5.dp)))
            Box(modifier = Modifier.width(3.dp).height(h3.dp).background(tint, RoundedCornerShape(1.5.dp)))
        }
    }
}

private fun getFeedbackMaterialIcon(iconName: String): ImageVector {
    return when (iconName) {
        "ac_unit" -> Icons.Default.AcUnit
        "hourglass_top" -> Icons.Default.HourglassTop
        "remove_shopping_cart" -> Icons.Default.RemoveShoppingCart
        "local_fire_department" -> Icons.Default.LocalFireDepartment
        "cleaning_services" -> Icons.Default.CleaningServices
        "thumb_up" -> Icons.Default.ThumbUp
        else -> Icons.Default.ReportProblem
    }
}

private fun getPhraseMaterialIcon(iconName: String): ImageVector {
    return when (iconName) {
        "hearing_disabled" -> Icons.Default.HearingDisabled
        "visibility_off" -> Icons.Default.VisibilityOff
        "water_drop" -> Icons.Default.WaterDrop
        "receipt_long" -> Icons.AutoMirrored.Filled.ReceiptLong
        "qr_code_scanner" -> Icons.Default.QrCodeScanner
        "local_fire_department" -> Icons.Default.LocalFireDepartment
        "no_meals" -> Icons.Default.NoMeals
        "whatshot" -> Icons.Default.Whatshot
        "restaurant" -> Icons.Default.Restaurant
        "soup_kitchen" -> Icons.Default.SoupKitchen
        "cleaning_services" -> Icons.Default.CleaningServices
        "hourglass_top" -> Icons.Default.HourglassTop
        "takeout_dining" -> Icons.Default.TakeoutDining
        "wash" -> Icons.Default.Wash
        else -> Icons.Default.ChatBubble
    }
}
