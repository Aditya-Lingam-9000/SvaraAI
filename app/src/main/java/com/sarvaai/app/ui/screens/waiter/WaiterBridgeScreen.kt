package com.sarvaai.app.ui.screens.waiter

import android.content.Intent
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
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
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
import com.sarvaai.app.bhashini.matrix.CapabilityMatrix
import com.sarvaai.app.llm.GroqClient
import com.sarvaai.app.service.waiter.PhraseBoard
import com.sarvaai.app.service.waiter.PhraseChip
import com.sarvaai.app.service.waiter.WaiterBridgeController
import com.sarvaai.app.service.wakeword.WakeWordDetector
import com.sarvaai.app.ui.components.AccessibleButton
import com.sarvaai.app.ui.theme.*
import kotlinx.coroutines.launch

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
    var recordingTarget by remember { mutableStateOf<String?>(null) } // "DINER" or "STAFF"

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
                                // Optionally speak translated waiter message to visually impaired diners
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

    Surface(
        modifier = modifier.fillMaxSize(),
        color = CharcoalBackground
    ) {
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
                        text = "🧑‍🍳 Waiter Bridge (Phase 10)",
                        fontSize = 19.sp,
                        fontWeight = FontWeight.Bold,
                        color = SaffronPrimary,
                        modifier = Modifier.semantics { heading() }
                    )
                    Text(
                        text = "Cross-Language Dual-Screen Staff Interaction",
                        fontSize = 12.sp,
                        color = SubtitleText
                    )
                }

                // Flip Card Button (for waiter standing across table)
                IconButton(
                    onClick = {
                        hapticEngine.trigger(HapticPattern.CLICK)
                        controller.toggleFlipForStaff()
                    },
                    modifier = Modifier
                        .padding(end = 4.dp)
                        .size(36.dp)
                        .background(if (state.isFlippedForStaff) SaffronPrimary else CharcoalSurfaceVariant, CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Default.Flip,
                        contentDescription = "Flip staff card for waiter viewing",
                        tint = if (state.isFlippedForStaff) SaffronOnPrimary else PureWhite,
                        modifier = Modifier.size(18.dp)
                    )
                }

                if (isAudioPlaying) {
                    IconButton(
                        onClick = onStopAudio,
                        modifier = Modifier
                            .padding(end = 4.dp)
                            .size(36.dp)
                            .background(NonVegRed.copy(alpha = 0.2f), CircleShape)
                    ) {
                        Icon(imageVector = Icons.Default.Stop, contentDescription = "Stop speech playback", tint = NonVegRed)
                    }
                }

                IconButton(
                    onClick = {
                        hapticEngine.trigger(HapticPattern.CLICK)
                        onStopAudio()
                        onClose()
                    }
                ) {
                    Icon(imageVector = Icons.Default.Close, contentDescription = "Close Waiter Bridge", tint = PureWhite)
                }
            }

            // Language Selector Row (Diner Lang <-> Staff Lang)
            Card(
                colors = CardDefaults.cardColors(containerColor = CharcoalSurface),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, Color(0xFF333333), RoundedCornerShape(10.dp))
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
                        Text(text = "🗣️ You Speak / Read", fontSize = 11.sp, color = SubtitleText, fontWeight = FontWeight.Medium)
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            listOf("te" to "తెలుగు", "hi" to "हिंदी", "en" to "Eng").forEach { (code, label) ->
                                val isSelected = state.dinerLanguage == code
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(30.dp)
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(if (isSelected) SaffronPrimary else CharcoalSurfaceVariant)
                                        .clickable {
                                            hapticEngine.trigger(HapticPattern.CLICK)
                                            controller.setDinerLanguage(code)
                                        }
                                ) {
                                    Text(
                                        text = label,
                                        fontSize = 11.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isSelected) SaffronOnPrimary else PureWhite
                                    )
                                }
                            }
                        }
                    }

                    Icon(
                        imageVector = Icons.Default.ArrowForward,
                        contentDescription = "to",
                        tint = SaffronPrimary,
                        modifier = Modifier.padding(horizontal = 6.dp).size(16.dp)
                    )

                    // Staff Lang Selector
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = "🧑‍🍳 Staff Reads / Hears", fontSize = 11.sp, color = SubtitleText, fontWeight = FontWeight.Medium)
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            listOf("hi" to "हिंदी", "te" to "తెలుగు", "en" to "Eng").forEach { (code, label) ->
                                val isSelected = state.staffLanguage == code
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(30.dp)
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(if (isSelected) Color(0xFF00ADB5) else CharcoalSurfaceVariant)
                                        .clickable {
                                            hapticEngine.trigger(HapticPattern.CLICK)
                                            controller.setStaffLanguage(code)
                                        }
                                ) {
                                    Text(
                                        text = label,
                                        fontSize = 11.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        color = PureWhite
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // GIANT OUTWARD-FACING STAFF CARD (32sp+ typography, flipped option)
            Card(
                colors = CardDefaults.cardColors(containerColor = Color.Black),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(2.5.dp, HighContrastYellow, RoundedCornerShape(14.dp))
                    .rotate(if (state.isFlippedForStaff) 180f else 0f)
                    .semantics(mergeDescendants = true) {
                        contentDescription = "Staff display showing: ${state.activeStaffCardText}"
                    }
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "👀 SHOW TO WAITER / STAFF",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = HighContrastYellow,
                            letterSpacing = 1.sp
                        )

                        IconButton(
                            onClick = {
                                hapticEngine.trigger(HapticPattern.CLICK)
                                onSpeakAudio(state.activeStaffCardText, state.staffLanguage)
                            },
                            modifier = Modifier
                                .size(32.dp)
                                .background(HighContrastYellow.copy(alpha = 0.2f), CircleShape)
                        ) {
                            Icon(
                                imageVector = Icons.Default.VolumeUp,
                                contentDescription = "Play loudly to staff",
                                tint = HighContrastYellow,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = state.activeStaffCardText,
                        fontSize = 30.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = PureWhite,
                        textAlign = TextAlign.Center,
                        lineHeight = 36.sp
                    )

                    if (state.activeStaffCardSubtext.isNotBlank()) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = state.activeStaffCardSubtext,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = SaffronPrimary,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // LIVE DINER CAPTION / WAITER RESPONSE BOX
            Card(
                colors = CardDefaults.cardColors(containerColor = CharcoalSurfaceVariant),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, Color(0xFF444444), RoundedCornerShape(10.dp))
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "💬 LIVE TRANSLATION / WAITER REPLIES",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = SaffronPrimary
                        )

                        if (state.isProcessing) {
                            CircularProgressIndicator(color = SaffronPrimary, modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = state.liveDinerCaption,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = PureWhite,
                        lineHeight = 22.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // QUICK PHRASE CATEGORIES TABS
            Text(
                text = "⚡ Instant Communication Phrases",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = SubtitleText
            )
            Spacer(modifier = Modifier.height(6.dp))
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                val categories = listOf(
                    "DISABILITY" to "🦻 Disability Notice",
                    "ESSENTIALS" to "💧 Water & Bill",
                    "FOOD_PREF" to "🌶️ Food Specs",
                    "SERVICE" to "🛎️ Table Service"
                )
                items(categories) { (catKey, catLabel) ->
                    val isSelected = selectedCategory == catKey
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .height(34.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isSelected) SaffronPrimary else CharcoalSurface)
                            .border(1.dp, if (isSelected) SaffronPrimary else Color(0xFF444444), RoundedCornerShape(8.dp))
                            .clickable {
                                hapticEngine.trigger(HapticPattern.CLICK)
                                selectedCategory = catKey
                            }
                            .padding(horizontal = 10.dp)
                    ) {
                        Text(
                            text = catLabel,
                            fontSize = 12.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            color = if (isSelected) SaffronOnPrimary else PureWhite
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // QUICK PHRASE CHIPS LIST
            val activePhrases = remember(selectedCategory) {
                PhraseBoard.getPhrasesByCategory(selectedCategory)
            }

            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                items(activePhrases, key = { it.id }) { phrase ->
                    val dinerDisplay = phrase.getTextForLanguage(state.dinerLanguage)
                    val staffDisplay = phrase.getTextForLanguage(state.staffLanguage)

                    Card(
                        colors = CardDefaults.cardColors(containerColor = CharcoalSurface),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, Color(0xFF333333), RoundedCornerShape(10.dp))
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
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = dinerDisplay,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = PureWhite
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "🧑‍🍳 Staff will hear: \"$staffDisplay\"",
                                    fontSize = 12.sp,
                                    color = SaffronPrimary
                                )
                            }

                            Icon(
                                imageVector = Icons.Default.VolumeUp,
                                contentDescription = null,
                                tint = SaffronPrimary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // DUAL-MIC CONTROLS (Diner Mic & Waiter Mic)
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                // 1. DINER MIC BUTTON
                val isDinerRecording = isRecording && recordingTarget == "DINER"
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .weight(1f)
                        .height(54.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (isDinerRecording) NonVegRed else SaffronPrimary)
                        .clickable { handleDinerMicClick() }
                        .semantics {
                            contentDescription = if (isDinerRecording) "Stop diner voice input" else "Speak custom message in ${state.dinerLanguage.uppercase()} to translate for staff"
                        }
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = if (isDinerRecording) Icons.Default.Stop else Icons.Default.Mic,
                            contentDescription = null,
                            tint = if (isDinerRecording) PureWhite else SaffronOnPrimary,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (isDinerRecording) "Stop & Send" else "🗣️ Speak to Staff",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isDinerRecording) PureWhite else SaffronOnPrimary
                        )
                    }
                }

                // 2. STAFF / WAITER MIC BUTTON
                val isStaffRecording = isRecording && recordingTarget == "STAFF"
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .weight(1f)
                        .height(54.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (isStaffRecording) NonVegRed else Color(0xFF00ADB5))
                        .clickable { handleStaffMicClick() }
                        .semantics {
                            contentDescription = if (isStaffRecording) "Stop staff listening" else "Hold for staff/waiter to speak in ${state.staffLanguage.uppercase()}"
                        }
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = if (isStaffRecording) Icons.Default.Stop else Icons.Default.Mic,
                            contentDescription = null,
                            tint = PureWhite,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (isStaffRecording) "Stop Listening" else "🧑‍🍳 Staff Mic",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = PureWhite
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
        }
    }
}
