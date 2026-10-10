package com.sarvaai.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.sarvaai.app.audio.haptics.HapticFeedbackEngine
import com.sarvaai.app.audio.haptics.HapticPattern
import com.sarvaai.app.audio.recorder.AudioRecorder
import com.sarvaai.app.audio.vad.VadStatus
import com.sarvaai.app.bhashini.asr.AsrNmtClient
import com.sarvaai.app.bhashini.auth.BhashiniConfigManager
import com.sarvaai.app.bhashini.matrix.CapabilityMatrix
import com.sarvaai.app.bhashini.models.IngestionResult
import com.sarvaai.app.core.result.Result
import com.sarvaai.app.data.db.AppDatabase
import com.sarvaai.app.ui.components.*
import com.sarvaai.app.ui.theme.*
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private lateinit var hapticEngine: HapticFeedbackEngine
    private lateinit var database: AppDatabase
    private lateinit var audioRecorder: AudioRecorder
    private lateinit var asrNmtClient: AsrNmtClient

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        hapticEngine = HapticFeedbackEngine(this)
        database = AppDatabase.getInstance(this)
        audioRecorder = AudioRecorder(this, lifecycleScope)
        asrNmtClient = AsrNmtClient()

        setContent {
            var isHighContrast by remember { mutableStateOf(false) }

            SarvaTheme(isHighContrast = isHighContrast) {
                MainAudioIngestionScreen(
                    audioRecorder = audioRecorder,
                    asrNmtClient = asrNmtClient,
                    hapticEngine = hapticEngine,
                    isHighContrast = isHighContrast,
                    onToggleHighContrast = { isHighContrast = !isHighContrast }
                )
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        audioRecorder.release()
    }
}

@Composable
fun MainAudioIngestionScreen(
    audioRecorder: AudioRecorder,
    asrNmtClient: AsrNmtClient,
    hapticEngine: HapticFeedbackEngine,
    isHighContrast: Boolean,
    onToggleHighContrast: () -> Unit
) {
    val scrollState = rememberScrollState()
    val scope = rememberCoroutineScope()

    var selectedLanguage by remember { mutableStateOf("hi") } // "en", "hi", "te"
    val isRecording by audioRecorder.isRecording.collectAsState()
    val vadStatus by audioRecorder.vadStatus.collectAsState()
    val amplitude by audioRecorder.audioAmplitude.collectAsState()

    var isProcessing by remember { mutableStateOf(false) }
    var ingestionResult by remember { mutableStateOf<IngestionResult?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            hapticEngine.trigger(HapticPattern.SPEECH_TURN_CUE)
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp)
                .systemBarsPadding()
                .verticalScroll(scrollState),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            // Header Title
            Text(
                text = "SarvaAI",
                fontSize = 36.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center,
                modifier = Modifier.semantics { heading() }
            )

            Text(
                text = "Multilingual Speech Ingestion Spine",
                fontSize = 17.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Language Selector Chips
            Text(
                text = "Select Speaking Language",
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = SubtitleText
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                CapabilityMatrix.SUPPORTED_LANGUAGES.forEach { lang ->
                    val isSelected = selectedLanguage == lang.code
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .weight(1f)
                            .height(52.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (isSelected) SaffronPrimary else CharcoalSurfaceVariant)
                            .border(
                                2.dp,
                                if (isSelected) SaffronPrimary else Color(0xFF444444),
                                RoundedCornerShape(12.dp)
                            )
                            .clickable {
                                selectedLanguage = lang.code
                                hapticEngine.trigger(HapticPattern.CLICK)
                            }
                    ) {
                        Text(
                            text = "${lang.nameNative}\n(${lang.code.uppercase()})",
                            fontSize = 13.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            color = if (isSelected) SaffronOnPrimary else PureWhite,
                            textAlign = TextAlign.Center,
                            lineHeight = 16.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Microphone & VAD Visualizer Section
            val micButtonColor by animateColorAsState(
                targetValue = when {
                    isProcessing -> Color(0xFFFF9900)
                    isRecording && vadStatus == VadStatus.SPEECH_ACTIVE -> VegGreen
                    isRecording -> SaffronPrimary
                    else -> CharcoalSurfaceVariant
                },
                label = "mic_color"
            )

            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(110.dp)
                    .clip(CircleShape)
                    .background(micButtonColor.copy(alpha = 0.25f))
                    .border(3.dp, micButtonColor, CircleShape)
                    .clickable(enabled = !isProcessing) {
                        if (!isRecording) {
                            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                            hapticEngine.trigger(HapticPattern.SPEECH_TURN_CUE)
                            errorMessage = null
                            audioRecorder.startRecording(
                                onUtteranceComplete = { wavBytes ->
                                    hapticEngine.trigger(HapticPattern.CONFIRMATION)
                                    isProcessing = true
                                    scope.launch {
                                        val res = asrNmtClient.transcribeAndTranslate(wavBytes, selectedLanguage)
                                        isProcessing = false
                                        when (res) {
                                            is Result.Success -> {
                                                ingestionResult = res.data
                                                errorMessage = null
                                            }
                                            is Result.Error -> {
                                                errorMessage = res.message
                                                hapticEngine.trigger(HapticPattern.ERROR_ALERT)
                                            }
                                            else -> {}
                                        }
                                    }
                                },
                                onError = { err ->
                                    errorMessage = err
                                    hapticEngine.trigger(HapticPattern.ERROR_ALERT)
                                }
                            )
                        } else {
                            audioRecorder.stopRecording()
                            hapticEngine.trigger(HapticPattern.CLICK)
                        }
                    }
            ) {
                Icon(
                    imageVector = if (isRecording) Icons.Default.Stop else Icons.Default.Mic,
                    contentDescription = if (isRecording) "Stop Recording" else "Start Speaking",
                    tint = PureWhite,
                    modifier = Modifier.size(48.dp)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // VAD Status & Live Audio Amplitude
            Text(
                text = when {
                    isProcessing -> "Processing with Bhashini ASR + NMT..."
                    isRecording && vadStatus == VadStatus.SPEECH_ACTIVE -> "Speech Active (Listening...)"
                    isRecording && vadStatus == VadStatus.CALIBRATING -> "Calibrating ambient noise..."
                    isRecording -> "Listening for voice (VAD active)..."
                    else -> "Tap microphone to speak"
                },
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (isRecording && vadStatus == VadStatus.SPEECH_ACTIVE) VegGreen else PureWhite
            )

            if (isRecording) {
                Spacer(modifier = Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { amplitude },
                    modifier = Modifier
                        .fillMaxWidth(0.6f)
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp)),
                    color = VegGreen,
                    trackColor = Color(0xFF333333)
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Ingestion Results Card
            AnimatedVisibility(visible = ingestionResult != null || isProcessing || errorMessage != null) {
                AccessibleCard(
                    borderColor = if (errorMessage != null) NonVegRed else SaffronPrimary
                ) {
                    Text(
                        text = "Bhashini Ingestion Output",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.semantics { heading() }
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    if (isProcessing) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = SaffronPrimary)
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(text = "Transcribing & translating...", fontSize = 14.sp, color = MutedText)
                        }
                    }

                    errorMessage?.let { err ->
                        Text(
                            text = "Error: $err",
                            fontSize = 14.sp,
                            color = NonVegRed,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    ingestionResult?.let { res ->
                        Text(
                            text = "Native Transcript (${res.sourceLanguage.uppercase()}):",
                            fontSize = 13.sp,
                            color = SubtitleText
                        )
                        Text(
                            text = if (res.originalTranscript.isNotEmpty()) res.originalTranscript else "(No speech detected)",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Medium,
                            color = PureWhite
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        Text(
                            text = "English NMT Translation:",
                            fontSize = 13.sp,
                            color = SubtitleText
                        )
                        Text(
                            text = if (res.englishTranslation.isNotEmpty()) res.englishTranslation else "(Empty)",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = SaffronPrimary
                        )

                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Latency: ${res.latencyMs} ms",
                            fontSize = 12.sp,
                            color = MutedText
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Accessibility Controls
            AccessibleButton(
                text = if (isHighContrast) "Standard Dark Mode" else "High Contrast Mode",
                onClick = {
                    hapticEngine.trigger(HapticPattern.CLICK)
                    onToggleHighContrast()
                },
                isPrimary = false
            )

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
