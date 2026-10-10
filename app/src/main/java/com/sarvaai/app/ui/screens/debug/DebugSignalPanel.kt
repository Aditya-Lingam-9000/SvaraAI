package com.sarvaai.app.ui.screens.debug

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
                    .padding(vertical = 12.dp)
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "🛠️ Dynamic Adaptation Debug",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = SaffronPrimary,
                        modifier = Modifier.semantics { heading() }
                    )
                    Text(
                        text = "Real-time Signal Injection & Hysteresis Profiling",
                        fontSize = 12.sp,
                        color = SubtitleText
                    )
                }

                IconButton(
                    onClick = {
                        hapticEngine.trigger(HapticPattern.CLICK)
                        onClose()
                    }
                ) {
                    Icon(imageVector = Icons.Default.Close, contentDescription = "Close Debug Panel", tint = PureWhite)
                }
            }

            // Announcement Banner if adaptation active
            announcement?.let { msg ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(SaffronPrimary.copy(alpha = 0.15f))
                        .border(1.dp, SaffronPrimary, RoundedCornerShape(8.dp))
                        .padding(10.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = "📢 Active Adaptation Announcement", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = SaffronPrimary)
                            Text(text = msg, fontSize = 12.sp, color = PureWhite)
                        }
                        IconButton(
                            onClick = { inferenceEngine.undoAdaptation() },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(imageVector = Icons.AutoMirrored.Filled.Undo, contentDescription = "Undo adaptation", tint = HighContrastYellow)
                        }
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))
            }

            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Section 1: Quick Simulation Presets
                item {
                    Text(text = "1. Simulation Presets (Jury Testing)", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = HighContrastYellow)
                    Spacer(modifier = Modifier.height(8.dp))

                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        AccessibleButton(
                            text = "👁️ Simulate Blind Diner (TalkBack Active)",
                            onClick = {
                                hapticEngine.trigger(HapticPattern.CONFIRMATION)
                                inferenceEngine.resetOverride()
                                signalCollector.injectSimulatedSignals(
                                    CollectedSignals(isTouchExplorationOn = true, touchlessElapsedSec = 20)
                                )
                                inferenceEngine.evaluate(signalCollector.signals.value)
                            },
                            modifier = Modifier.fillMaxWidth(),
                            isPrimary = activeFlags.voiceFirst
                        )

                        AccessibleButton(
                            text = "🧏 Simulate Deaf Diner (Muted + Captions)",
                            onClick = {
                                hapticEngine.trigger(HapticPattern.CONFIRMATION)
                                inferenceEngine.resetOverride()
                                signalCollector.injectSimulatedSignals(
                                    CollectedSignals(isSystemCaptioningOn = true, isMediaMuted = true)
                                )
                                inferenceEngine.evaluate(signalCollector.signals.value)
                            },
                            modifier = Modifier.fillMaxWidth(),
                            isPrimary = activeFlags.captionsOn
                        )

                        AccessibleButton(
                            text = "🗣️ Simulate Speech Difficulty (ASR Failures x3)",
                            onClick = {
                                hapticEngine.trigger(HapticPattern.CONFIRMATION)
                                inferenceEngine.resetOverride()
                                signalCollector.injectSimulatedSignals(
                                    CollectedSignals(failedAsrConsecutiveCount = 3)
                                )
                                inferenceEngine.evaluate(signalCollector.signals.value)
                            },
                            modifier = Modifier.fillMaxWidth(),
                            isPrimary = activeFlags.phraseBoard
                        )

                        AccessibleButton(
                            text = "👴 Simulate Elderly Diner (1.5x Font + Taps)",
                            onClick = {
                                hapticEngine.trigger(HapticPattern.CONFIRMATION)
                                inferenceEngine.resetOverride()
                                signalCollector.injectSimulatedSignals(
                                    CollectedSignals(fontScale = 1.5f, repeatTapCount = 3)
                                )
                                inferenceEngine.evaluate(signalCollector.signals.value)
                            },
                            modifier = Modifier.fillMaxWidth(),
                            isPrimary = activeFlags.largeText || activeFlags.slowTts
                        )

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            AccessibleButton(
                                text = "↩️ Undo Adaptation",
                                onClick = {
                                    hapticEngine.trigger(HapticPattern.CLICK)
                                    inferenceEngine.undoAdaptation()
                                },
                                modifier = Modifier.weight(1f),
                                isPrimary = false
                            )

                            AccessibleButton(
                                text = "🔄 Reset All",
                                onClick = {
                                    hapticEngine.trigger(HapticPattern.CLICK)
                                    signalCollector.resetAll()
                                    inferenceEngine.resetOverride()
                                    inferenceEngine.evaluate(signalCollector.signals.value)
                                },
                                modifier = Modifier.weight(1f),
                                isPrimary = false
                            )
                        }
                    }
                }

                // Section 2: Hysteresis Scoring Status
                item {
                    Text(text = "2. Weighted Hysteresis Scores (Threshold: 40/70)", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = HighContrastYellow)
                    Spacer(modifier = Modifier.height(6.dp))

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(CharcoalSurface)
                            .padding(12.dp)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            ScoreRow("Visual Score", inferenceEngine.visualScore, activeFlags.voiceFirst)
                            ScoreRow("Hearing Score", inferenceEngine.hearingScore, activeFlags.captionsOn)
                            ScoreRow("Speech Score", inferenceEngine.speechScore, activeFlags.phraseBoard)
                            ScoreRow("Ageing Score", inferenceEngine.ageingScore, activeFlags.largeText || activeFlags.slowTts)
                        }
                    }
                }

                // Section 3: Active Dynamic Feature Flags
                item {
                    Text(text = "3. Active Dynamic Feature Flags", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = HighContrastYellow)
                    Spacer(modifier = Modifier.height(6.dp))

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(CharcoalSurface)
                            .padding(12.dp)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
                    Text(text = "4. Raw Telemetry Signals", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = HighContrastYellow)
                    Spacer(modifier = Modifier.height(6.dp))

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(CharcoalSurface)
                            .padding(12.dp)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
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

            Spacer(modifier = Modifier.height(10.dp))
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
        Text(text = label, fontSize = 13.sp, color = PureWhite)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "$score / 100",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = if (score >= 70) SaffronPrimary else if (score >= 40) HighContrastYellow else SubtitleText
            )
            Spacer(modifier = Modifier.width(8.dp))
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(if (isActive) VegGreen else Color.Gray)
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
            Text(text = flagName, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = if (isChecked) SaffronPrimary else PureWhite)
            Text(text = description, fontSize = 11.sp, color = SubtitleText)
        }
        Switch(
            checked = isChecked,
            onCheckedChange = onToggle,
            colors = SwitchDefaults.colors(
                checkedThumbColor = PureWhite,
                checkedTrackColor = SaffronPrimary,
                uncheckedThumbColor = Color.LightGray,
                uncheckedTrackColor = CharcoalSurfaceVariant
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
        Text(text = label, fontSize = 12.sp, color = SubtitleText)
        Text(text = value, fontSize = 12.sp, fontWeight = FontWeight.Medium, color = PureWhite)
    }
}
