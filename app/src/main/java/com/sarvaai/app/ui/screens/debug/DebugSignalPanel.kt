package com.sarvaai.app.ui.screens.debug

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sarvaai.app.adapt.inference.ActiveFeatureFlags
import com.sarvaai.app.adapt.inference.ProfileInferenceEngine
import com.sarvaai.app.adapt.signals.CollectedSignals
import com.sarvaai.app.adapt.signals.SignalCollector
import com.sarvaai.app.audio.haptics.HapticFeedbackEngine
import com.sarvaai.app.audio.haptics.HapticPattern
import com.sarvaai.app.ui.components.AccessibleButton
import com.sarvaai.app.ui.theme.*

@Composable
fun DebugSignalPanel(
    signalCollector: SignalCollector,
    inferenceEngine: ProfileInferenceEngine,
    hapticEngine: HapticFeedbackEngine,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val currentSignals by signalCollector.signals.collectAsState()
    val activeFlags by inferenceEngine.flags.collectAsState()
    val isOverridden by inferenceEngine.isUserOverridden.collectAsState()
    val announcement by inferenceEngine.lastAnnouncedReason.collectAsState()

    // Safe Back handling
    BackHandler {
        onClose()
    }

    Surface(
        modifier = modifier.fillMaxSize(),
        color = NeoBackground
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
                    .padding(vertical = 12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(NeoPrimaryContainer)
                            .border(1.dp, NeoBorder, RoundedCornerShape(10.dp))
                    ) {
                        Icon(
                            imageVector = Icons.Default.Tune,
                            contentDescription = null,
                            tint = NeoPrimary,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "Dynamic Adaptation Debug",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = NeoTextPrimary,
                            modifier = Modifier.semantics { heading() }
                        )
                        Text(
                            text = "Real-time Signal Injection & Hysteresis Profiling",
                            fontSize = 12.sp,
                            color = NeoTextSecondary
                        )
                    }
                }

                IconButton(
                    onClick = {
                        hapticEngine.trigger(HapticPattern.CLICK)
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
                        contentDescription = "Close Debug Panel",
                        tint = NeoTextPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            // Announcement Banner if adaptation active
            announcement?.let { msg ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = NeoPrimaryContainer),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, NeoPrimary),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 10.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Campaign,
                                contentDescription = null,
                                tint = NeoPrimary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = "Active Adaptation Announcement",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = NeoPrimary
                                )
                                Text(
                                    text = msg,
                                    fontSize = 12.sp,
                                    color = NeoTextPrimary
                                )
                            }
                        }
                        IconButton(
                            onClick = { inferenceEngine.undoAdaptation() },
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(NeoSurface)
                                .border(1.dp, NeoBorder, CircleShape)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Undo,
                                contentDescription = "Undo adaptation",
                                tint = NeoPrimary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }

            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Section 1: Quick Simulation Presets
                item {
                    Text(
                        text = "1. Simulation Presets (Jury Testing)",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = NeoTextPrimary
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        AccessibleButton(
                            text = "Simulate Blind Diner (TalkBack Active)",
                            onClick = {
                                hapticEngine.trigger(HapticPattern.CONFIRMATION)
                                inferenceEngine.resetOverride()
                                signalCollector.injectSimulatedSignals(
                                    CollectedSignals(isTouchExplorationOn = true, touchlessElapsedSec = 20)
                                )
                                inferenceEngine.evaluate(signalCollector.signals.value)
                            },
                            modifier = Modifier.fillMaxWidth(),
                            isPrimary = activeFlags.voiceFirst,
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.VisibilityOff,
                                    contentDescription = null,
                                    tint = if (activeFlags.voiceFirst) NeoOnPrimary else NeoTextPrimary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        )

                        AccessibleButton(
                            text = "Simulate Deaf Diner (Muted + Captions)",
                            onClick = {
                                hapticEngine.trigger(HapticPattern.CONFIRMATION)
                                inferenceEngine.resetOverride()
                                signalCollector.injectSimulatedSignals(
                                    CollectedSignals(isSystemCaptioningOn = true, isMediaMuted = true)
                                )
                                inferenceEngine.evaluate(signalCollector.signals.value)
                            },
                            modifier = Modifier.fillMaxWidth(),
                            isPrimary = activeFlags.captionsOn,
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.HearingDisabled,
                                    contentDescription = null,
                                    tint = if (activeFlags.captionsOn) NeoOnPrimary else NeoTextPrimary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        )

                        AccessibleButton(
                            text = "Simulate Speech Difficulty (ASR Failures x3)",
                            onClick = {
                                hapticEngine.trigger(HapticPattern.CONFIRMATION)
                                inferenceEngine.resetOverride()
                                signalCollector.injectSimulatedSignals(
                                    CollectedSignals(failedAsrConsecutiveCount = 3)
                                )
                                inferenceEngine.evaluate(signalCollector.signals.value)
                            },
                            modifier = Modifier.fillMaxWidth(),
                            isPrimary = activeFlags.phraseBoard,
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.MicOff,
                                    contentDescription = null,
                                    tint = if (activeFlags.phraseBoard) NeoOnPrimary else NeoTextPrimary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        )

                        AccessibleButton(
                            text = "Simulate Ageing Signals (Font Scale 1.35x + Dwell Pause 4s + Repeat Taps 2)",
                            onClick = {
                                hapticEngine.trigger(HapticPattern.CONFIRMATION)
                                inferenceEngine.resetOverride()
                                signalCollector.injectSimulatedSignals(
                                    CollectedSignals(
                                        fontScale = 1.35f,
                                        averagePauseDurationMs = 4000L,
                                        repeatTapCount = 2
                                    )
                                )
                                inferenceEngine.evaluate(signalCollector.signals.value)
                            },
                            modifier = Modifier.fillMaxWidth(),
                            isPrimary = activeFlags.largeText || activeFlags.slowTts || activeFlags.stepByStep,
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.FormatSize,
                                    contentDescription = null,
                                    tint = if (activeFlags.largeText || activeFlags.slowTts || activeFlags.stepByStep) NeoOnPrimary else NeoTextPrimary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        )

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            AccessibleButton(
                                text = "Undo Adaptation",
                                onClick = {
                                    hapticEngine.trigger(HapticPattern.CLICK)
                                    inferenceEngine.undoAdaptation()
                                },
                                modifier = Modifier.weight(1f),
                                isPrimary = false,
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.Undo,
                                        contentDescription = null,
                                        tint = NeoTextPrimary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            )

                            AccessibleButton(
                                text = "Reset All",
                                onClick = {
                                    hapticEngine.trigger(HapticPattern.CLICK)
                                    signalCollector.resetAll()
                                    inferenceEngine.resetOverride()
                                    inferenceEngine.evaluate(signalCollector.signals.value)
                                },
                                modifier = Modifier.weight(1f),
                                isPrimary = false,
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.Refresh,
                                        contentDescription = null,
                                        tint = NeoTextPrimary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            )
                        }
                    }
                }

                // Dedicated Highlight: Condition 4 Ageing Dynamic Adaptation Showcase
                item {
                    val isAgeingActive = activeFlags.largeText && activeFlags.slowTts && activeFlags.stepByStep
                    val hasAnyAgeingFlag = activeFlags.largeText || activeFlags.slowTts || activeFlags.stepByStep

                    Card(
                        colors = CardDefaults.cardColors(containerColor = NeoSurface),
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(
                            2.dp,
                            if (isAgeingActive) VegGreen else if (hasAnyAgeingFlag) NeoPrimary else NeoBorder
                        ),
                        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            // Section Header with Badge
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
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(if (isAgeingActive) VegGreenContainer else NeoPrimaryContainer)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.FormatSize,
                                            contentDescription = null,
                                            tint = if (isAgeingActive) VegGreen else NeoPrimary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column {
                                        Text(
                                            text = "Condition 4: Ageing (Older Adults)",
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = NeoTextPrimary
                                        )
                                        Text(
                                            text = "Megathon 2026 Page 07 Showcase",
                                            fontSize = 11.sp,
                                            color = NeoTextSecondary
                                        )
                                    }
                                }

                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (isAgeingActive) VegGreenContainer else if (hasAnyAgeingFlag) NeoPrimaryContainer else NeoSurfacePressed)
                                        .border(
                                            1.dp,
                                            if (isAgeingActive) VegGreen else if (hasAnyAgeingFlag) NeoPrimary else NeoBorder,
                                            RoundedCornerShape(8.dp)
                                        )
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        text = if (isAgeingActive) "ADAPTED" else if (hasAnyAgeingFlag) "PARTIAL" else "MONITORING",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isAgeingActive) VegGreen else if (hasAnyAgeingFlag) NeoPrimary else NeoTextSecondary
                                    )
                                }
                            }

                            HorizontalDivider(color = NeoBorder.copy(alpha = 0.5f), thickness = 1.dp)

                            // Live Ageing Score Gauge
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Row(
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = "Live Ageing Score Gauge",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = NeoTextPrimary
                                    )
                                    Text(
                                        text = "${inferenceEngine.ageingScore} / 100",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (inferenceEngine.ageingScore >= 70) VegGreen else if (inferenceEngine.ageingScore >= 40) JainYellow else NeoTextSecondary
                                    )
                                }

                                LinearProgressIndicator(
                                    progress = { (inferenceEngine.ageingScore / 100f).coerceIn(0f, 1f) },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(10.dp)
                                        .clip(RoundedCornerShape(5.dp)),
                                    color = if (inferenceEngine.ageingScore >= 70) VegGreen else if (inferenceEngine.ageingScore >= 40) JainYellow else NeoPrimary,
                                    trackColor = NeoSurfacePressed
                                )

                                Row(
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("0 (Baseline)", fontSize = 10.sp, color = NeoTextMuted)
                                    Text("40 (Lower Threshold)", fontSize = 10.sp, color = JainYellow)
                                    Text("70 (Trigger Threshold)", fontSize = 10.sp, color = VegGreen)
                                }
                            }

                            // ActiveFeatureFlags lighting up
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(
                                    text = "Active Dynamic Feature Flags:",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = NeoTextPrimary
                                )

                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(NeoSurfacePressed)
                                        .border(1.dp, if (isAgeingActive) VegGreen else NeoBorder, RoundedCornerShape(8.dp))
                                        .padding(8.dp)
                                ) {
                                    Text(
                                        text = "ActiveFeatureFlags(largeText=${activeFlags.largeText}, slowTts=${activeFlags.slowTts}, stepByStep=${activeFlags.stepByStep})",
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = if (isAgeingActive) VegGreen else NeoTextPrimary
                                    )
                                }

                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    AgeingFlagPill(
                                        name = "largeText",
                                        detail = "1.35x Scale",
                                        isActive = activeFlags.largeText,
                                        modifier = Modifier.weight(1f)
                                    )
                                    AgeingFlagPill(
                                        name = "slowTts",
                                        detail = "0.8x Voice",
                                        isActive = activeFlags.slowTts,
                                        modifier = Modifier.weight(1f)
                                    )
                                    AgeingFlagPill(
                                        name = "stepByStep",
                                        detail = "Concierge",
                                        isActive = activeFlags.stepByStep,
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }

                            // Transparency Explanation Box
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(NeoPrimaryContainer.copy(alpha = 0.35f))
                                    .border(1.dp, NeoPrimary.copy(alpha = 0.4f), RoundedCornerShape(10.dp))
                                    .padding(10.dp)
                            ) {
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.Default.Info,
                                            contentDescription = null,
                                            tint = NeoPrimary,
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "Dynamic Adaptation Transparency Explanation",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = NeoPrimary
                                        )
                                    }

                                    val explanationText = if (isAgeingActive || hasAnyAgeingFlag) {
                                        "Ageing profile inferred dynamically: Font Scale: ${String.format("%.2f", currentSignals.fontScale)}x (+70), Dwell Pause: ${currentSignals.averagePauseDurationMs}ms (+30), Repeat Taps: ${currentSignals.repeatTapCount} (+40). Exceeded 70 threshold. Features ActiveFeatureFlags(largeText=true, slowTts=true, stepByStep=true) active with full 1-tap undo."
                                    } else {
                                        "Telemetry signals below activation threshold (70). Standard interface active until user hesitation or font scaling triggers hysteresis engine."
                                    }

                                    Text(
                                        text = explanationText,
                                        fontSize = 11.sp,
                                        lineHeight = 15.sp,
                                        color = NeoTextPrimary
                                    )

                                    announcement?.let { msg ->
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = "Voice Announcement: \"$msg\"",
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = NeoTextSecondary
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Section 2: Hysteresis Scoring Status
                item {
                    Text(
                        text = "2. Weighted Hysteresis Scores (Threshold: 40/70)",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = NeoTextPrimary
                    )
                    Spacer(modifier = Modifier.height(6.dp))

                    Card(
                        colors = CardDefaults.cardColors(containerColor = NeoSurface),
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.5.dp, NeoBorder),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            ScoreRow("Visual Score", inferenceEngine.visualScore, activeFlags.voiceFirst)
                            ScoreRow("Hearing Score", inferenceEngine.hearingScore, activeFlags.captionsOn)
                            ScoreRow("Speech Score", inferenceEngine.speechScore, activeFlags.phraseBoard)
                            ScoreRow("Ageing Score (Condition 4)", inferenceEngine.ageingScore, activeFlags.largeText || activeFlags.slowTts || activeFlags.stepByStep)
                        }
                    }
                }

                // Section 3: Active Dynamic Feature Flags
                item {
                    Text(
                        text = "3. Active Dynamic Feature Flags",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = NeoTextPrimary
                    )
                    Spacer(modifier = Modifier.height(6.dp))

                    Card(
                        colors = CardDefaults.cardColors(containerColor = NeoSurface),
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.5.dp, NeoBorder),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            FlagRow("voiceFirst", "Auto-activate voice flow", activeFlags.voiceFirst) {
                                inferenceEngine.setManualFlags(activeFlags.copy(voiceFirst = it))
                            }
                            FlagRow("captionsOn", "Persistent live captioning", activeFlags.captionsOn) {
                                inferenceEngine.setManualFlags(activeFlags.copy(captionsOn = it))
                            }
                            FlagRow("phraseBoard", "Direct waiter phrase board", activeFlags.phraseBoard) {
                                inferenceEngine.setManualFlags(activeFlags.copy(phraseBoard = it))
                            }
                            FlagRow("largeText", "High contrast 1.35x UI scaling (Ageing)", activeFlags.largeText) {
                                inferenceEngine.setManualFlags(activeFlags.copy(largeText = it))
                            }
                            FlagRow("slowTts", "0.8x unhurried speech rate (Ageing)", activeFlags.slowTts) {
                                inferenceEngine.setManualFlags(activeFlags.copy(slowTts = it))
                            }
                            FlagRow("stepByStep", "Enforce step-by-step confirmation (Ageing)", activeFlags.stepByStep) {
                                inferenceEngine.setManualFlags(activeFlags.copy(stepByStep = it))
                            }
                        }
                    }
                }

                // Section 4: Raw Collected System Signals
                item {
                    Text(
                        text = "4. Raw Telemetry Signals",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = NeoTextPrimary
                    )
                    Spacer(modifier = Modifier.height(6.dp))

                    Card(
                        colors = CardDefaults.cardColors(containerColor = NeoSurface),
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.5.dp, NeoBorder),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            SignalItem("Touch Exploration (TalkBack)", currentSignals.isTouchExplorationOn.toString())
                            SignalItem("System Captioning", currentSignals.isSystemCaptioningOn.toString())
                            SignalItem("Media Stream Muted", currentSignals.isMediaMuted.toString())
                            SignalItem("Font Scale", String.format("%.2f", currentSignals.fontScale))
                            SignalItem("Dwell Pause Duration", "${currentSignals.averagePauseDurationMs} ms")
                            SignalItem("Consecutive ASR Failures", currentSignals.failedAsrConsecutiveCount.toString())
                            SignalItem("Repeat Tap Count", currentSignals.repeatTapCount.toString())
                            SignalItem("Touchless Elapsed Sec", currentSignals.touchlessElapsedSec.toString())
                            SignalItem("User Override Active", isOverridden.toString())
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}

@Composable
private fun ScoreRow(label: String, score: Int, isActive: Boolean) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(text = label, fontSize = 13.sp, color = NeoTextPrimary, fontWeight = FontWeight.Medium)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "$score / 100",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = if (score >= 70) NeoPrimary else if (score >= 40) JainYellow else NeoTextSecondary
            )
            Spacer(modifier = Modifier.width(8.dp))
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(if (isActive) VegGreen else NeoTextMuted)
            )
        }
    }
}

@Composable
private fun FlagRow(flagName: String, description: String, isChecked: Boolean, onToggle: (Boolean) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = flagName,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = if (isChecked) NeoPrimary else NeoTextPrimary
            )
            Text(text = description, fontSize = 11.sp, color = NeoTextSecondary)
        }
        Switch(
            checked = isChecked,
            onCheckedChange = onToggle,
            colors = SwitchDefaults.colors(
                checkedThumbColor = NeoOnPrimary,
                checkedTrackColor = NeoPrimary,
                uncheckedThumbColor = NeoTextSecondary,
                uncheckedTrackColor = NeoSurfacePressed
            )
        )
    }
}

@Composable
private fun SignalItem(label: String, value: String) {
    Row(
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(text = label, fontSize = 12.sp, color = NeoTextSecondary)
        Text(text = value, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = NeoTextPrimary)
    }
}

@Composable
private fun AgeingFlagPill(name: String, detail: String, isActive: Boolean, modifier: Modifier = Modifier) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (isActive) VegGreenContainer else NeoSurfacePressed)
            .border(
                1.5.dp,
                if (isActive) VegGreen else NeoBorder,
                RoundedCornerShape(8.dp)
            )
            .padding(vertical = 6.dp, horizontal = 4.dp)
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = if (isActive) Icons.Default.CheckCircle else Icons.Default.Circle,
                    contentDescription = null,
                    tint = if (isActive) VegGreen else NeoTextMuted,
                    modifier = Modifier.size(11.dp)
                )
                Spacer(modifier = Modifier.width(3.dp))
                Text(
                    text = name,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isActive) VegGreen else NeoTextPrimary
                )
            }
            Text(
                text = detail,
                fontSize = 9.sp,
                color = if (isActive) VegGreen else NeoTextSecondary
            )
        }
    }
}
