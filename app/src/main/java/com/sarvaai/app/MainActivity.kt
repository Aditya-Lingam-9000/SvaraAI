package com.sarvaai.app

import android.Manifest
import android.os.Bundle
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
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import com.sarvaai.app.audio.earcons.EarconEngine
import com.sarvaai.app.audio.earcons.EarconType
import com.sarvaai.app.audio.haptics.HapticFeedbackEngine
import com.sarvaai.app.audio.haptics.HapticPattern
import com.sarvaai.app.audio.player.DoubleSpeechSuppressor
import com.sarvaai.app.audio.player.SentenceChunker
import com.sarvaai.app.audio.player.SpeechPlayer
import com.sarvaai.app.audio.recorder.AudioRecorder
import com.sarvaai.app.audio.vad.VadStatus
import com.sarvaai.app.bhashini.asr.AsrNmtClient
import com.sarvaai.app.bhashini.matrix.CapabilityMatrix
import com.sarvaai.app.bhashini.models.IngestionResult
import com.sarvaai.app.bhashini.tts.BhashiniTtsClient
import com.sarvaai.app.core.result.Result
import com.sarvaai.app.data.db.AppDatabase
import com.sarvaai.app.ui.components.*
import com.sarvaai.app.ui.theme.*
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private lateinit var hapticEngine: HapticFeedbackEngine
    private lateinit var earconEngine: EarconEngine
    private lateinit var speechSuppressor: DoubleSpeechSuppressor
    private lateinit var database: AppDatabase
    private lateinit var audioRecorder: AudioRecorder
    private lateinit var asrNmtClient: AsrNmtClient
    private lateinit var ttsClient: BhashiniTtsClient
    private lateinit var speechPlayer: SpeechPlayer

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        hapticEngine = HapticFeedbackEngine(this)
        earconEngine = EarconEngine(this)
        speechSuppressor = DoubleSpeechSuppressor(this)
        database = AppDatabase.getInstance(this)
        audioRecorder = AudioRecorder(this, lifecycleScope)
        asrNmtClient = AsrNmtClient()
        ttsClient = BhashiniTtsClient(this)
        speechPlayer = SpeechPlayer(this)

        setContent {
            var isHighContrast by remember { mutableStateOf(false) }

            SarvaTheme(isHighContrast = isHighContrast) {
                MainAudioPlaygroundScreen(
                    audioRecorder = audioRecorder,
                    asrNmtClient = asrNmtClient,
                    ttsClient = ttsClient,
                    speechPlayer = speechPlayer,
                    hapticEngine = hapticEngine,
                    earconEngine = earconEngine,
                    speechSuppressor = speechSuppressor,
                    isHighContrast = isHighContrast,
                    onToggleHighContrast = { isHighContrast = !isHighContrast }
                )
            }
        }
    }

    override fun onPause() {
        super.onPause()
        if (::audioRecorder.isInitialized) {
            audioRecorder.stopRecording()
        }
        if (::speechPlayer.isInitialized) {
            speechPlayer.pause()
        }
    }

    override fun onStop() {
        super.onStop()
        if (::audioRecorder.isInitialized) {
            audioRecorder.stopRecording()
        }
        if (::speechPlayer.isInitialized) {
            speechPlayer.stop()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::audioRecorder.isInitialized) {
            audioRecorder.release()
        }
        if (::speechPlayer.isInitialized) {
            speechPlayer.release()
        }
    }
}

@Composable
fun MainAudioPlaygroundScreen(
    audioRecorder: AudioRecorder,
    asrNmtClient: AsrNmtClient,
    ttsClient: BhashiniTtsClient,
    speechPlayer: SpeechPlayer,
    hapticEngine: HapticFeedbackEngine,
    earconEngine: EarconEngine,
    speechSuppressor: DoubleSpeechSuppressor,
    isHighContrast: Boolean,
    onToggleHighContrast: () -> Unit
) {
    val scrollState = rememberScrollState()
    val scope = rememberCoroutineScope()

    var selectedLanguage by remember { mutableStateOf("hi") } // "en", "hi", "te"
    val isRecording by audioRecorder.isRecording.collectAsState()
    val vadStatus by audioRecorder.vadStatus.collectAsState()
    val amplitude by audioRecorder.audioAmplitude.collectAsState()

    val isAudioPlaying by speechPlayer.isPlaying.collectAsState()
    val currentPositionMs by speechPlayer.currentPositionMs.collectAsState()
    val totalDurationMs by speechPlayer.durationMs.collectAsState()
    val playbackSpeed by speechPlayer.playbackSpeed.collectAsState()

    var isProcessing by remember { mutableStateOf(false) }
    var ingestionResult by remember { mutableStateOf<IngestionResult?>(null) }
    var currentSpokenCaption by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    // Calculate highlighted word index based on playback progress
    val words = remember(currentSpokenCaption) {
        if (currentSpokenCaption.isBlank()) emptyList() else currentSpokenCaption.split(" ").filter { it.isNotBlank() }
    }
    val highlightedWordIndex = remember(currentPositionMs, totalDurationMs, words) {
        if (words.isEmpty() || totalDurationMs <= 0 || !isAudioPlaying) {
            -1
        } else {
            val ratio = (currentPositionMs.toFloat() / totalDurationMs.toFloat()).coerceIn(0f, 1f)
            (ratio * words.size).toInt().coerceIn(0, words.size - 1)
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            hapticEngine.trigger(HapticPattern.SPEECH_TURN_CUE)
            earconEngine.play(EarconType.LISTEN_START)
        }
    }

    fun playSampleText(textToSynthesize: String, targetLang: String) {
        val cleanText = textToSynthesize.trim()
        if (cleanText.isBlank() || !cleanText.any { it.isLetterOrDigit() }) {
            return
        }

        scope.launch {
            if (isRecording) {
                audioRecorder.stopRecording()
            }
            speechPlayer.stop()
            isProcessing = true
            errorMessage = null
            currentSpokenCaption = "Synthesizing audio..."

            // Sentence Chunking for Low-Latency TTFA
            val chunks = SentenceChunker.splitIntoChunks(cleanText)
            if (chunks.isEmpty()) {
                isProcessing = false
                currentSpokenCaption = ""
                return@launch
            }

            val fullSpokenBuilder = StringBuilder()

            for ((index, chunk) in chunks.withIndex()) {
                if (chunk.isBlank() || !chunk.any { it.isLetterOrDigit() }) continue
                val res = ttsClient.synthesizeSpeech(chunk, targetLang)
                when (res) {
                    is Result.Success -> {
                        val ttsData = res.data
                        if (index == 0) {
                            isProcessing = false
                            currentSpokenCaption = ttsData.spokenText
                            fullSpokenBuilder.append(ttsData.spokenText)
                            ttsData.audioFile?.let { speechPlayer.enqueueAudioFile(it, clearQueue = true) }
                            speechSuppressor.announceAccessibilityText(ttsData.spokenText, isBhashiniAudioPlaying = true)
                            hapticEngine.trigger(HapticPattern.CONFIRMATION)
                        } else {
                            if (fullSpokenBuilder.isNotEmpty()) fullSpokenBuilder.append(" ")
                            fullSpokenBuilder.append(ttsData.spokenText)
                            currentSpokenCaption = fullSpokenBuilder.toString()
                            ttsData.audioFile?.let { speechPlayer.enqueueAudioFile(it, clearQueue = false) }
                        }
                    }
                    is Result.Error -> {
                        isProcessing = false
                        errorMessage = res.message
                        hapticEngine.trigger(HapticPattern.ERROR_ALERT)
                        earconEngine.play(EarconType.ERROR_ALERT)
                        break
                    }
                    else -> {}
                }
            }
            isProcessing = false
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
                text = "Phase 3: Real-Time Audio Playback & Live Captions",
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Language Selector Chips
            Text(
                text = "Select Speaking & Playback Language",
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
                            .height(54.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (isSelected) SaffronPrimary else CharcoalSurfaceVariant)
                            .border(
                                2.dp,
                                if (isSelected) SaffronPrimary else Color(0xFF444444),
                                RoundedCornerShape(12.dp)
                            )
                            .clickable {
                                if (isRecording) audioRecorder.stopRecording()
                                speechPlayer.stop()
                                selectedLanguage = lang.code
                                hapticEngine.trigger(HapticPattern.CLICK)
                            }
                            .semantics {
                                contentDescription = "${lang.nameEnglish} language (${lang.nameNative})"
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

            Spacer(modifier = Modifier.height(20.dp))

            // Playback Speed Regulation (0.8x, 1.0x, 1.25x, 1.5x)
            Text(
                text = "Pitch-Preserving Speech Speed",
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = SubtitleText
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                listOf(0.8f to "0.8x (Gentle)", 1.0f to "1.0x (Normal)", 1.25f to "1.25x (Fast)", 1.5f to "1.5x (Rapid)").forEach { (speed, label) ->
                    val isCurrent = Math.abs(playbackSpeed - speed) < 0.05f
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isCurrent) SaffronPrimary.copy(alpha = 0.85f) else CharcoalSurfaceVariant)
                            .border(1.5.dp, if (isCurrent) SaffronPrimary else Color(0xFF444444), RoundedCornerShape(8.dp))
                            .clickable {
                                speechPlayer.setSpeed(speed)
                                hapticEngine.trigger(HapticPattern.CLICK)
                            }
                    ) {
                        Text(
                            text = label,
                            fontSize = 11.sp,
                            fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium,
                            color = if (isCurrent) SaffronOnPrimary else PureWhite,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Microphone Button for Speech-to-Speech Flow
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
                            speechPlayer.stop()
                            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                            hapticEngine.trigger(HapticPattern.SPEECH_TURN_CUE)
                            earconEngine.play(EarconType.LISTEN_START)
                            errorMessage = null
                            audioRecorder.startRecording(
                                onUtteranceComplete = { wavBytes ->
                                    hapticEngine.trigger(HapticPattern.CONFIRMATION)
                                    earconEngine.play(EarconType.LISTEN_STOP)
                                    isProcessing = true
                                    scope.launch {
                                        // Step 1: Ingestion (ASR + NMT)
                                        val res = asrNmtClient.transcribeAndTranslate(wavBytes, selectedLanguage)
                                        when (res) {
                                            is Result.Success -> {
                                                ingestionResult = res.data
                                                errorMessage = null

                                                // Step 2: Synthesize AI Response in target language & Stream Playback
                                                val englishResponse = if (res.data.originalTranscript.isNotBlank() && res.data.originalTranscript.any { it.isLetterOrDigit() }) {
                                                    val recognizedItem = if (res.data.englishTranslation.isNotBlank()) res.data.englishTranslation else res.data.originalTranscript
                                                    "I understood your request for $recognizedItem. Adding it to your order."
                                                } else {
                                                    "I could not hear any speech clearly. Please try speaking again."
                                                }
                                                playSampleText(englishResponse, selectedLanguage)
                                            }
                                            is Result.Error -> {
                                                isProcessing = false
                                                errorMessage = res.message
                                                hapticEngine.trigger(HapticPattern.ERROR_ALERT)
                                                earconEngine.play(EarconType.ERROR_ALERT)
                                            }
                                            else -> {
                                                isProcessing = false
                                            }
                                        }
                                    }
                                },
                                onError = { err ->
                                    errorMessage = err
                                    hapticEngine.trigger(HapticPattern.ERROR_ALERT)
                                    earconEngine.play(EarconType.ERROR_ALERT)
                                }
                            )
                        } else {
                            audioRecorder.stopRecording()
                            earconEngine.play(EarconType.LISTEN_STOP)
                            hapticEngine.trigger(HapticPattern.CLICK)
                        }
                    }
                    .semantics {
                        contentDescription = if (isRecording) "Stop voice recording" else "Start speech interaction in ${selectedLanguage.uppercase()}"
                    }
            ) {
                Icon(
                    imageVector = if (isRecording) Icons.Default.Stop else Icons.Default.Mic,
                    contentDescription = null,
                    tint = PureWhite,
                    modifier = Modifier.size(48.dp)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // VAD & Playback Status
            Text(
                text = when {
                    isProcessing -> "Processing with Bhashini ASR + TTS..."
                    isAudioPlaying -> "Speaking aloud via ExoPlayer (Streaming)..."
                    isRecording && vadStatus == VadStatus.SPEECH_ACTIVE -> "Speech Active (Listening...)"
                    isRecording && vadStatus == VadStatus.CALIBRATING -> "Calibrating ambient noise..."
                    isRecording -> "Listening for voice (VAD active)..."
                    else -> "Tap microphone for Speech-to-Speech dialogue"
                },
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = when {
                    isAudioPlaying -> HighContrastYellow
                    isRecording && vadStatus == VadStatus.SPEECH_ACTIVE -> VegGreen
                    else -> PureWhite
                }
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

            Spacer(modifier = Modifier.height(16.dp))

            // Live Word-Synchronized Caption Bar
            LiveCaptionBar(
                fullCaption = currentSpokenCaption,
                highlightedWordIndex = highlightedWordIndex,
                isVisible = currentSpokenCaption.isNotBlank()
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Quick TTS Demonstration Triggers
            Text(
                text = "Quick Voice Assistant Audio Prompts",
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = SubtitleText
            )
            Spacer(modifier = Modifier.height(8.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                AccessibleButton(
                    text = "Order Menu Intro",
                    onClick = {
                        val prompt = "Welcome to SarvaAI. We have hot crispy masala dosa, idli sambar, and filter coffee today. What would you like to order?"
                        playSampleText(prompt, selectedLanguage)
                    },
                    modifier = Modifier.weight(1f),
                    isPrimary = true
                )

                AccessibleButton(
                    text = "Confirm Order",
                    onClick = {
                        val prompt = "Your order for two masala dosas and one filter coffee has been placed successfully. Your total is one hundred and eighty rupees."
                        playSampleText(prompt, selectedLanguage)
                    },
                    modifier = Modifier.weight(1f),
                    isPrimary = false
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Ingestion Results Card
            AnimatedVisibility(visible = ingestionResult != null || errorMessage != null) {
                AccessibleCard(
                    borderColor = if (errorMessage != null) NonVegRed else SaffronPrimary
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Language,
                            contentDescription = null,
                            tint = SaffronPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Dialogue & Ingestion Diagnostics",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.semantics { heading() }
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))

                    errorMessage?.let { err ->
                        Text(
                            text = "Error: $err",
                            fontSize = 14.sp,
                            color = NonVegRed,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    ingestionResult?.let { res ->
                        val isEnglishInput = res.sourceLanguage == "en"

                        Text(
                            text = if (isEnglishInput) "Recognized English Speech:" else "Native Speech (${res.sourceLanguage.uppercase()}):",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = SubtitleText
                        )
                        Text(
                            text = if (res.originalTranscript.isNotEmpty()) res.originalTranscript else "(No speech detected)",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Medium,
                            color = PureWhite
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        if (isEnglishInput) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = VegGreen,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "AI Intent Engine: Direct English (0ms NMT Latency)",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = VegGreen
                                )
                            }
                        } else {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.SwapHoriz,
                                    contentDescription = null,
                                    tint = SaffronPrimary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Unified Order Translation (English):",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = SubtitleText
                                )
                            }
                            Text(
                                text = if (res.englishTranslation.isNotEmpty()) res.englishTranslation else "(Empty)",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = SaffronPrimary
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "ASR+NMT Latency: ${res.latencyMs} ms",
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
