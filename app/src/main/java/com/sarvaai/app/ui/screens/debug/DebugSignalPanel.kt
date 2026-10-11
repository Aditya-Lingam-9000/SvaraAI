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
                            text = "Simulate Elderly Diner (1.5x Font + Taps)",
                            onClick = {
                                hapticEngine.trigger(HapticPattern.CONFIRMATION)
                                inferenceEngine.resetOverride()
                                signalCollector.injectSimulatedSignals(
                                    CollectedSignals(fontScale = 1.5f, repeatTapCount = 3)
                                )
                                inferenceEngine.evaluate(signalCollector.signals.value)
                            },
                            modifier = Modifier.fillMaxWidth(),
                            isPrimary = activeFlags.largeText || activeFlags.slowTts,
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.FormatSize,
                                    contentDescription = null,
                                    tint = if (activeFlags.largeText || activeFlags.slowTts) NeoOnPrimary else NeoTextPrimary,
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
                            ScoreRow("Ageing Score", inferenceEngine.ageingScore, activeFlags.largeText || activeFlags.slowTts)
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
                            FlagRow("largeText", "High contrast 1.3x UI scaling", activeFlags.largeText) {
                                inferenceEngine.setManualFlags(activeFlags.copy(largeText = it))
                            }
                            FlagRow("slowTts", "0.8x speech rate for clarity", activeFlags.slowTts) {
                                inferenceEngine.setManualFlags(activeFlags.copy(slowTts = it))
                            }
                            FlagRow("stepByStep", "Enforce confirmation per step", activeFlags.stepByStep) {
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
