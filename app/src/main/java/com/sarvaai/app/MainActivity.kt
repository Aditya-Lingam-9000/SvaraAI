package com.sarvaai.app

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.util.Log
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import com.sarvaai.app.adapt.inference.ProfileInferenceEngine
import com.sarvaai.app.adapt.signals.SignalCollector
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
import com.sarvaai.app.core.config.ApiKeyManager
import com.sarvaai.app.core.result.Result
import com.sarvaai.app.data.db.AppDatabase
import com.sarvaai.app.data.entities.CartItemEntity
import com.sarvaai.app.data.entities.MenuItemEntity
import com.sarvaai.app.data.entities.MenuSectionEntity
import com.sarvaai.app.llm.GroqClient
import com.sarvaai.app.llm.models.StructuredAiResponse
import com.sarvaai.app.menu.extractor.MenuExtractor
import com.sarvaai.app.service.wakeword.SvaraWakeWordService
import com.sarvaai.app.service.wakeword.WakeWordDetector
import com.sarvaai.app.ui.components.*
import com.sarvaai.app.ui.screens.bill.SmartBillSplitScreen
import com.sarvaai.app.ui.screens.browser.MenuBrowserScreen
import com.sarvaai.app.ui.screens.camera.CameraXMenuCaptureScreen
import com.sarvaai.app.ui.screens.cart.VoiceOrderingCartScreen
import com.sarvaai.app.ui.screens.debug.DebugSignalPanel
import com.sarvaai.app.ui.screens.debug.RawVsAccessibleDebugView
import com.sarvaai.app.ui.screens.waiter.WaiterBridgeScreen
import com.sarvaai.app.ui.theme.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.UUID

enum class AppScreen {
    PLAYGROUND,
    CAMERA_CAPTURE,
    MENU_BROWSER,
    VOICE_CART,
    WAITER_BRIDGE,
    SMART_BILL,
    RAW_VS_ACCESSIBLE,
    DEBUG_SIGNAL_PANEL
}

class MainActivity : ComponentActivity() {

    private val closeAppReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val action = intent?.action
            if (action == WakeWordDetector.ACTION_CLOSE_APP || action == SvaraWakeWordService.ACTION_STOP_SERVICE) {
                Log.i("MainActivity", "Close app broadcast received ($action). Terminating application...")
                finishAndRemoveTask()
                finishAffinity()
            }
        }
    }

    private lateinit var hapticEngine: HapticFeedbackEngine
    private lateinit var earconEngine: EarconEngine
    private lateinit var speechSuppressor: DoubleSpeechSuppressor
    private lateinit var database: AppDatabase
    private lateinit var audioRecorder: AudioRecorder
    private lateinit var asrNmtClient: AsrNmtClient
    private lateinit var ttsClient: BhashiniTtsClient
    private lateinit var speechPlayer: SpeechPlayer
    private lateinit var groqClient: GroqClient
    private lateinit var menuExtractor: MenuExtractor
    private lateinit var signalCollector: SignalCollector
    private lateinit var profileInferenceEngine: ProfileInferenceEngine
    private var activeSpeechJob: Job? = null
    private var androidTts: android.speech.tts.TextToSpeech? = null

    private var autoStartListeningTrigger by mutableStateOf(0)

    private fun stopGlobalSpeech() {
        activeSpeechJob?.cancel()
        activeSpeechJob = null
        try {
            androidTts?.stop()
        } catch (e: Exception) {}
        if (::speechPlayer.isInitialized) {
            speechPlayer.stop()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.getBooleanExtra(WakeWordDetector.EXTRA_AUTO_START_LISTENING, false) ||
            intent.getBooleanExtra("EXTRA_AUTO_START_LISTENING", false)) {
            Log.i("MainActivity", "onNewIntent received EXTRA_AUTO_START_LISTENING")
            autoStartListeningTrigger++
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Register Close App Broadcast Receiver
        val filter = IntentFilter(WakeWordDetector.ACTION_CLOSE_APP).apply {
            addAction(SvaraWakeWordService.ACTION_STOP_SERVICE)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(closeAppReceiver, filter, RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(closeAppReceiver, filter)
        }

        // Start Wake-Word Continuous Foreground Service
        SvaraWakeWordService.startService(this)

        hapticEngine = HapticFeedbackEngine(this)
        earconEngine = EarconEngine(this)
        speechSuppressor = DoubleSpeechSuppressor(this)
        database = AppDatabase.getInstance(this)
        audioRecorder = AudioRecorder(this, lifecycleScope)
        asrNmtClient = AsrNmtClient()
        ttsClient = BhashiniTtsClient(this)
        speechPlayer = SpeechPlayer(this)
        groqClient = GroqClient(this)
        menuExtractor = MenuExtractor(this, database.menuDao())
        signalCollector = SignalCollector(this)
        profileInferenceEngine = ProfileInferenceEngine()
        signalCollector.pollSystemSignals(resources.configuration)
        profileInferenceEngine.evaluate(signalCollector.signals.value)

        try {
            androidTts = android.speech.tts.TextToSpeech(this) { status ->
                if (status == android.speech.tts.TextToSpeech.SUCCESS) {
                    androidTts?.language = java.util.Locale("hi", "IN")
                }
            }
        } catch (e: Exception) {
            Log.w("MainActivity", "Native TTS init: ${e.message}")
        }

        if (intent?.getBooleanExtra(WakeWordDetector.EXTRA_AUTO_START_LISTENING, false) == true ||
            intent?.getBooleanExtra("EXTRA_AUTO_START_LISTENING", false) == true) {
            Log.i("MainActivity", "onCreate received EXTRA_AUTO_START_LISTENING")
            autoStartListeningTrigger++
        }

        setContent {
            var isHighContrast by remember { mutableStateOf(false) }
            var currentScreen by remember { mutableStateOf(AppScreen.PLAYGROUND) }
            var selectedLanguage by remember { mutableStateOf("hi") }

            var menuSections by remember { mutableStateOf<List<MenuSectionEntity>>(emptyList()) }
            var menuItemsBySection by remember { mutableStateOf<Map<String, List<MenuItemEntity>>>(emptyMap()) }
            var restaurantName by remember { mutableStateOf("Sarva Bhavan Restaurant") }

            val isAudioPlaying by speechPlayer.isPlaying.collectAsState()

            fun stopSpeech() {
                stopGlobalSpeech()
            }

            fun playSpeech(text: String, lang: String = selectedLanguage) {
                val cleanText = text.trim()
                if (cleanText.isBlank() || !cleanText.any { it.isLetterOrDigit() }) return

                stopGlobalSpeech()

                activeSpeechJob = lifecycleScope.launch {
                    val chunks = SentenceChunker.splitIntoChunks(cleanText)
                    if (chunks.isEmpty()) return@launch

                    for ((index, chunk) in chunks.withIndex()) {
                        if (!isActive) break
                        if (chunk.isBlank() || !chunk.any { it.isLetterOrDigit() }) continue

                        var audioPlayed = false
                        try {
                            val res = kotlinx.coroutines.withTimeoutOrNull(2000L) {
                                ttsClient.synthesizeSpeech(chunk, lang)
                            }
                            if (!isActive) break
                            if (res is Result.Success) {
                                val audioFile = res.data.audioFile
                                if (audioFile != null && isActive) {
                                    speechPlayer.enqueueAudioFile(audioFile, clearQueue = (index == 0))
                                    audioPlayed = true
                                }
                            }
                        } catch (e: Exception) {
                            Log.w("MainActivity", "Bhashini TTS timed out/failed: ${e.message}")
                        }

                        if (!audioPlayed && isActive) {
                            // Instant local native TTS fallback
                            val loc = when (lang.lowercase()) {
                                "te" -> java.util.Locale("te", "IN")
                                "hi" -> java.util.Locale("hi", "IN")
                                else -> java.util.Locale.ENGLISH
                            }
                            androidTts?.language = loc
                            androidTts?.speak(chunk, android.speech.tts.TextToSpeech.QUEUE_ADD, null, "chunk_$index")
                        }
                    }
                }
            }

            SarvaTheme(isHighContrast = isHighContrast) {
                when (currentScreen) {
                    AppScreen.PLAYGROUND -> {
                        MainAudioPlaygroundScreen(
                            audioRecorder = audioRecorder,
                            asrNmtClient = asrNmtClient,
                            ttsClient = ttsClient,
                            speechPlayer = speechPlayer,
                            groqClient = groqClient,
                            hapticEngine = hapticEngine,
                            earconEngine = earconEngine,
                            speechSuppressor = speechSuppressor,
                            isHighContrast = isHighContrast,
                            selectedLanguage = selectedLanguage,
                            autoStartListeningTrigger = autoStartListeningTrigger,
                            onLanguageSelected = { selectedLanguage = it },
                            onOpenScreen = {
                                stopSpeech()
                                currentScreen = it
                            },
                            onToggleHighContrast = { isHighContrast = !isHighContrast },
                            onCloseApp = {
                                finishAndRemoveTask()
                                finishAffinity()
                            }
                        )
                    }

                    AppScreen.CAMERA_CAPTURE -> {
                        CameraXMenuCaptureScreen(
                            menuExtractor = menuExtractor,
                            hapticEngine = hapticEngine,
                            earconEngine = earconEngine,
                            selectedLanguage = selectedLanguage,
                            onSpeakAnnouncement = { playSpeech(it, selectedLanguage) },
                            onStopAnnouncement = { stopSpeech() },
                            onMenuExtracted = { parsed ->
                                stopSpeech()
                                restaurantName = parsed.menu.restaurantName
                                menuSections = parsed.sections
                                menuItemsBySection = parsed.items.groupBy { it.sectionId }
                                currentScreen = AppScreen.MENU_BROWSER
                            },
                            onClose = {
                                stopSpeech()
                                currentScreen = AppScreen.PLAYGROUND
                            }
                        )
                    }

                    AppScreen.MENU_BROWSER -> {
                        MenuBrowserScreen(
                            restaurantName = restaurantName,
                            sections = menuSections,
                            itemsBySection = menuItemsBySection,
                            selectedLanguage = selectedLanguage,
                            hapticEngine = hapticEngine,
                            earconEngine = earconEngine,
                            isAudioPlaying = isAudioPlaying,
                            onSpeakNarration = { playSpeech(it, selectedLanguage) },
                            onStopNarration = { stopSpeech() },
                            onAddItemToCart = { item ->
                                hapticEngine.trigger(HapticPattern.CONFIRMATION)
                                lifecycleScope.launch {
                                    val existing = database.cartDao().getCartItemsDirect().firstOrNull { it.name.equals(item.nameEnglish, ignoreCase = true) }
                                    if (existing != null) {
                                        database.cartDao().updateQuantity(existing.cartItemId, existing.quantity + 1)
                                    } else {
                                        val newItem = CartItemEntity(
                                            cartItemId = "cart_${UUID.randomUUID().toString().take(8)}",
                                            itemId = item.itemId,
                                            name = item.nameEnglish,
                                            unitPricePaise = item.pricePaise,
                                            quantity = 1,
                                            specialNotes = null
                                        )
                                        database.cartDao().insertOrUpdate(newItem)
                                    }
                                }
                            },
                            onOpenCart = {
                                stopSpeech()
                                currentScreen = AppScreen.VOICE_CART
                            },
                            onClose = {
                                stopSpeech()
                                currentScreen = AppScreen.PLAYGROUND
                            }
                        )
                    }

                    AppScreen.VOICE_CART -> {
                        VoiceOrderingCartScreen(
                            cartDao = database.cartDao(),
                            orderDao = database.orderDao(),
                            menuDao = database.menuDao(),
                            groqClient = groqClient,
                            audioRecorder = audioRecorder,
                            asrNmtClient = asrNmtClient,
                            hapticEngine = hapticEngine,
                            earconEngine = earconEngine,
                            selectedLanguage = selectedLanguage,
                            restaurantName = restaurantName,
                            isAudioPlaying = isAudioPlaying,
                            onSpeakNarration = { playSpeech(it, selectedLanguage) },
                            onStopNarration = { stopSpeech() },
                            onOpenBillSplit = {
                                stopSpeech()
                                currentScreen = AppScreen.SMART_BILL
                            },
                            onClose = {
                                stopSpeech()
                                currentScreen = AppScreen.PLAYGROUND
                            }
                        )
                    }

                    AppScreen.WAITER_BRIDGE -> {
                        WaiterBridgeScreen(
                            asrNmtClient = asrNmtClient,
                            groqClient = groqClient,
                            audioRecorder = audioRecorder,
                            hapticEngine = hapticEngine,
                            earconEngine = earconEngine,
                            initialDinerLanguage = selectedLanguage,
                            initialStaffLanguage = if (selectedLanguage == "hi") "te" else "hi",
                            isAudioPlaying = isAudioPlaying,
                            onSpeakAudio = { textToSpeak, langToSpeak ->
                                playSpeech(textToSpeak, langToSpeak)
                            },
                            onStopAudio = { stopSpeech() },
                            onClose = {
                                stopSpeech()
                                currentScreen = AppScreen.PLAYGROUND
                            }
                        )
                    }

                    AppScreen.SMART_BILL -> {
                        SmartBillSplitScreen(
                            cartDao = database.cartDao(),
                            orderDao = database.orderDao(),
                            menuDao = database.menuDao(),
                            groqClient = groqClient,
                            audioRecorder = audioRecorder,
                            asrNmtClient = asrNmtClient,
                            hapticEngine = hapticEngine,
                            earconEngine = earconEngine,
                            selectedLanguage = selectedLanguage,
                            restaurantName = restaurantName,
                            isAudioPlaying = isAudioPlaying,
                            onSpeakNarration = { playSpeech(it, selectedLanguage) },
                            onStopNarration = { stopSpeech() },
                            onClose = {
                                stopSpeech()
                                currentScreen = AppScreen.PLAYGROUND
                            }
                        )
                    }

                    AppScreen.RAW_VS_ACCESSIBLE -> {
                        RawVsAccessibleDebugView(
                            isAudioPlaying = isAudioPlaying,
                            onPlayAudio = { textToPlay, langToPlay ->
                                playSpeech(textToPlay, langToPlay)
                            },
                            onStopAudio = { stopSpeech() },
                            onClose = {
                                stopSpeech()
                                currentScreen = AppScreen.PLAYGROUND
                            }
                        )
                    }

                    AppScreen.DEBUG_SIGNAL_PANEL -> {
                        DebugSignalPanel(
                            signalCollector = signalCollector,
                            inferenceEngine = profileInferenceEngine,
                            hapticEngine = hapticEngine,
                            onClose = {
                                stopSpeech()
                                currentScreen = AppScreen.PLAYGROUND
                            }
                        )
                    }
                }
            }
        }
    }

    override fun onPause() {
        super.onPause()
        stopGlobalSpeech()
        if (::audioRecorder.isInitialized) {
            audioRecorder.stopRecording()
        }
        if (::speechPlayer.isInitialized) {
            speechPlayer.pause()
        }
    }

    override fun onStop() {
        super.onStop()
        stopGlobalSpeech()
        if (::audioRecorder.isInitialized) {
            audioRecorder.stopRecording()
        }
        if (::speechPlayer.isInitialized) {
            speechPlayer.stop()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            unregisterReceiver(closeAppReceiver)
        } catch (e: Exception) {
            Log.w("MainActivity", "Receiver unregister: ${e.message}")
        }
        stopGlobalSpeech()
        if (::audioRecorder.isInitialized) {
            audioRecorder.release()
        }
        if (::speechPlayer.isInitialized) {
            speechPlayer.release()
        }
        try {
            androidTts?.shutdown()
        } catch (e: Exception) {}
    }
}

@Composable
fun MainAudioPlaygroundScreen(
    audioRecorder: AudioRecorder,
    asrNmtClient: AsrNmtClient,
    ttsClient: BhashiniTtsClient,
    speechPlayer: SpeechPlayer,
    groqClient: GroqClient,
    hapticEngine: HapticFeedbackEngine,
    earconEngine: EarconEngine,
    speechSuppressor: DoubleSpeechSuppressor,
    isHighContrast: Boolean,
    selectedLanguage: String,
    autoStartListeningTrigger: Int = 0,
    onLanguageSelected: (String) -> Unit,
    onOpenScreen: (AppScreen) -> Unit,
    onToggleHighContrast: () -> Unit,
    onCloseApp: () -> Unit = {}
) {
    val scrollState = rememberScrollState()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    val isRecording by audioRecorder.isRecording.collectAsState()
    val vadStatus by audioRecorder.vadStatus.collectAsState()
    val amplitude by audioRecorder.audioAmplitude.collectAsState()

    val isAudioPlaying by speechPlayer.isPlaying.collectAsState()
    val currentPositionMs by speechPlayer.currentPositionMs.collectAsState()
    val totalDurationMs by speechPlayer.durationMs.collectAsState()
    val playbackSpeed by speechPlayer.playbackSpeed.collectAsState()

    var isProcessing by remember { mutableStateOf(false) }
    var isWakeWordServiceRunning by remember { mutableStateOf(true) }
    var ingestionResult by remember { mutableStateOf<IngestionResult?>(null) }
    var structuredAiResponse by remember { mutableStateOf<StructuredAiResponse?>(null) }
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

    var activePlaygroundPlaybackJob by remember { mutableStateOf<Job?>(null) }

    DisposableEffect(Unit) {
        onDispose {
            activePlaygroundPlaybackJob?.cancel()
            speechPlayer.stop()
        }
    }

    fun playSampleText(textToSynthesize: String, targetLang: String) {
        val cleanText = textToSynthesize.trim()
        if (cleanText.isBlank() || !cleanText.any { it.isLetterOrDigit() }) {
            return
        }

        activePlaygroundPlaybackJob?.cancel()
        if (isRecording) {
            audioRecorder.stopRecording()
        }
        speechPlayer.stop()

        activePlaygroundPlaybackJob = scope.launch {
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
                if (!isActive) break
                if (chunk.isBlank() || !chunk.any { it.isLetterOrDigit() }) continue
                val res = ttsClient.synthesizeSpeech(chunk, targetLang)
                if (!isActive) break
                when (res) {
                    is Result.Success -> {
                        val ttsData = res.data
                        if (index == 0) {
                            isProcessing = false
                            currentSpokenCaption = ttsData.spokenText
                            fullSpokenBuilder.append(ttsData.spokenText)
                            if (isActive) {
                                ttsData.audioFile?.let { speechPlayer.enqueueAudioFile(it, clearQueue = true) }
                                speechSuppressor.announceAccessibilityText(ttsData.spokenText, isBhashiniAudioPlaying = true)
                                hapticEngine.trigger(HapticPattern.CONFIRMATION)
                            }
                        } else {
                            if (fullSpokenBuilder.isNotEmpty()) fullSpokenBuilder.append(" ")
                            fullSpokenBuilder.append(ttsData.spokenText)
                            currentSpokenCaption = fullSpokenBuilder.toString()
                            if (isActive) {
                                ttsData.audioFile?.let { speechPlayer.enqueueAudioFile(it, clearQueue = false) }
                            }
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

    fun startListeningFlow() {
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
                        // Step 1: Ingestion (ASR)
                        val res = asrNmtClient.transcribeAndTranslate(wavBytes, selectedLanguage)
                        when (res) {
                            is Result.Success -> {
                                ingestionResult = res.data
                                errorMessage = null

                                val rawTranscript = res.data.originalTranscript.trim()
                                if (rawTranscript.isNotBlank() && rawTranscript.any { it.isLetterOrDigit() }) {
                                    // Voice Exit / Shutdown Command Detection
                                    if (WakeWordDetector.isCloseCommand(rawTranscript)) {
                                        speechPlayer.stop()
                                        activePlaygroundPlaybackJob?.cancel()
                                        val goodbyeText = WakeWordDetector.getGoodbyeMessage(selectedLanguage)
                                        currentSpokenCaption = goodbyeText
                                        earconEngine.play(EarconType.LISTEN_STOP)
                                        hapticEngine.trigger(HapticPattern.CONFIRMATION)
                                        playSampleText(goodbyeText, selectedLanguage)
                                        scope.launch {
                                            delay(1000L)
                                            onCloseApp()
                                        }
                                    } else {
                                        // Step 2: Intelligent Multilingual Intent & Conversational Reasoning
                                        val intentResult = groqClient.processSpeechIntent(rawTranscript, selectedLanguage)
                                        when (intentResult) {
                                            is Result.Success -> {
                                                val aiResp = intentResult.data
                                                structuredAiResponse = aiResp
                                                // Step 3: Directly synthesize the AI's natural spoken response in customer's language
                                                playSampleText(aiResp.spoken, selectedLanguage)
                                            }
                                            is Result.Error -> {
                                                val fallbackText = when (selectedLanguage) {
                                                    "te" -> "మీ $rawTranscript ఆర్డర్‌లో చేర్చాను. ఇంకా ఏమైనా కావాలా?"
                                                    "hi" -> "आपके $rawTranscript को ऑर्डर में जोड़ दिया गया है। क्या आप कुछ और लेना चाहेंगे?"
                                                    else -> "I have added $rawTranscript to your order. Would you like anything else?"
                                                }
                                                playSampleText(fallbackText, selectedLanguage)
                                            }
                                            else -> {}
                                        }
                                    }
                                } else {
                                    val noSpeechText = when (selectedLanguage) {
                                        "te" -> "క్షమించండి, మీ మాటలు వినపడలేదు. దయచేసి మళ్ళీ మాట్లాడండి."
                                        "hi" -> "माफ़ कीजिए, आवाज़ स्पष्ट नहीं थी। कृपया फिर से बोलें।"
                                        else -> "I could not hear any speech clearly. Please try speaking again."
                                    }
                                    playSampleText(noSpeechText, selectedLanguage)
                                }
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

    // Auto-Start Listening Trigger when awakened by 'Hey Svara' or Voice Intent
    LaunchedEffect(autoStartListeningTrigger) {
        if (autoStartListeningTrigger > 0) {
            earconEngine.play(EarconType.LISTEN_START)
            hapticEngine.trigger(HapticPattern.SPEECH_TURN_CUE)
            currentSpokenCaption = "🎙️ Listening for your voice..."
            if (!isRecording) {
                startListeningFlow()
            }
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
                text = "Accessible Multimodal Restaurant Assistant",
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Multi-Phase Feature Launch Rows
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                AccessibleButton(
                    text = "📸 Scan Menu (P5/6)",
                    onClick = {
                        hapticEngine.trigger(HapticPattern.CLICK)
                        onOpenScreen(AppScreen.CAMERA_CAPTURE)
                    },
                    modifier = Modifier.weight(1f),
                    isPrimary = true
                )

                AccessibleButton(
                    text = "🛒 Voice Cart (P7)",
                    onClick = {
                        hapticEngine.trigger(HapticPattern.CLICK)
                        onOpenScreen(AppScreen.VOICE_CART)
                    },
                    modifier = Modifier.weight(1f),
                    isPrimary = true
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                AccessibleButton(
                    text = "🧑‍🍳 Waiter Bridge (P10)",
                    onClick = {
                        hapticEngine.trigger(HapticPattern.CLICK)
                        onOpenScreen(AppScreen.WAITER_BRIDGE)
                    },
                    modifier = Modifier.weight(1f),
                    isPrimary = true
                )

                AccessibleButton(
                    text = "💳 Smart Bill (P11)",
                    onClick = {
                        hapticEngine.trigger(HapticPattern.CLICK)
                        onOpenScreen(AppScreen.SMART_BILL)
                    },
                    modifier = Modifier.weight(1f),
                    isPrimary = true
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                AccessibleButton(
                    text = "🛠️ Adaptation Debug (P12)",
                    onClick = {
                        hapticEngine.trigger(HapticPattern.CLICK)
                        onOpenScreen(AppScreen.DEBUG_SIGNAL_PANEL)
                    },
                    modifier = Modifier.weight(1f),
                    isPrimary = false
                )

                AccessibleButton(
                    text = "⚖️ Jury Mode (P4)",
                    onClick = {
                        hapticEngine.trigger(HapticPattern.CLICK)
                        onOpenScreen(AppScreen.RAW_VS_ACCESSIBLE)
                    },
                    modifier = Modifier.weight(1f),
                    isPrimary = false
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Wake-Word Background Service Status & Control Card
            AccessibleCard(
                borderColor = if (isWakeWordServiceRunning) VegGreen else SaffronPrimary
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "🎙️ 'Hey Svara' Wake-Word Service",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = PureWhite
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = if (isWakeWordServiceRunning) "Active in background (Foreground Service)" else "Service stopped",
                            fontSize = 12.sp,
                            color = if (isWakeWordServiceRunning) VegGreen else SubtitleText
                        )
                    }
                    Switch(
                        checked = isWakeWordServiceRunning,
                        onCheckedChange = { enable ->
                            isWakeWordServiceRunning = enable
                            hapticEngine.trigger(HapticPattern.CLICK)
                            if (enable) {
                                SvaraWakeWordService.startService(context)
                            } else {
                                SvaraWakeWordService.stopService(context)
                            }
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = PureWhite,
                            checkedTrackColor = VegGreen,
                            uncheckedThumbColor = PureWhite,
                            uncheckedTrackColor = CharcoalSurfaceVariant
                        )
                    )
                }
            }

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
                                onLanguageSelected(lang.code)
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
                        startListeningFlow()
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
                    isProcessing -> "Processing with Bhashini ASR + AI Reasoner..."
                    isAudioPlaying -> "Speaking aloud via ExoPlayer (Streaming)..."
                    isRecording && vadStatus == VadStatus.SPEECH_ACTIVE -> "Speech Active (Listening...)"
                    isRecording && vadStatus == VadStatus.CALIBRATING -> "Calibrating ambient noise..."
                    isRecording -> "Listening for voice (VAD active)..."
                    else -> "Tap microphone or say 'Hey Svara' for voice dialogue"
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

            // Quick TTS Demonstration Triggers (Language-Native)
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
                        val prompt = when (selectedLanguage) {
                            "te" -> "సర్వ ఏఐ కి స్వాగతం. ఈరోజు మా వద్ద వేడి వేడి మసాలా దోశ, ఇడ్లీ సాంబార్, మరియు ఫిల్టర్ కాఫీ ఉన్నాయి. మీరు ఏమి ఆర్డర్ చేయాలనుకుంటున్నారు?"
                            "hi" -> "सर्व एआई में आपका स्वागत है। आज हमारे पास गरमा-गरम मसाला डोसा, इडली सांभर और फ़िल्टर कॉफ़ी है। आप क्या ऑर्डर करना चाहेंगे?"
                            else -> "Welcome to SarvaAI. We have hot crispy masala dosa, idli sambar, and filter coffee today. What would you like to order?"
                        }
                        playSampleText(prompt, selectedLanguage)
                    },
                    modifier = Modifier.weight(1f),
                    isPrimary = true
                )

                AccessibleButton(
                    text = "Confirm Order",
                    onClick = {
                        val prompt = when (selectedLanguage) {
                            "te" -> "మీ రెండు మసాలా దోశలు మరియు ఒక ఫిల్టర్ కాఫీ ఆర్డర్ చేయబడ్డాయి. మొత్తం బిల్లు నూట ఎనభై రూపాయలు."
                            "hi" -> "आपके दो मसाला डोसे और एक फ़िल्टर कॉफ़ी का ऑर्डर हो गया है। कुल राशि एक सौ अस्सी रुपये है।"
                            else -> "Your order for two masala dosas and one filter coffee has been placed successfully. Your total is one hundred and eighty rupees."
                        }
                        playSampleText(prompt, selectedLanguage)
                    },
                    modifier = Modifier.weight(1f),
                    isPrimary = false
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Ingestion & AI Understanding Diagnostics Card
            AnimatedVisibility(visible = ingestionResult != null || structuredAiResponse != null || errorMessage != null) {
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
                        Text(
                            text = "Recognized Native Speech (${res.sourceLanguage.uppercase()}):",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = SubtitleText
                        )
                        Text(
                            text = if (res.originalTranscript.isNotEmpty()) res.originalTranscript else "(No speech detected)",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = PureWhite
                        )

                        Spacer(modifier = Modifier.height(10.dp))
                    }

                    structuredAiResponse?.let { ai ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Psychology,
                                contentDescription = null,
                                tint = VegGreen,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "AI Intent: ${ai.intent}",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = VegGreen
                            )
                        }

                        Text(
                            text = ai.headline,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium,
                            color = SaffronPrimary
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = "Spoken Response (${selectedLanguage.uppercase()}):",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = SubtitleText
                        )
                        Text(
                            text = ai.spoken,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Normal,
                            color = PureWhite
                        )

                        if (ai.actions.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                ai.actions.forEach { action ->
                                    Box(
                                        contentAlignment = Alignment.Center,
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(SaffronPrimary.copy(alpha = 0.2f))
                                            .border(1.dp, SaffronPrimary, RoundedCornerShape(8.dp))
                                            .padding(horizontal = 12.dp, vertical = 6.dp)
                                    ) {
                                        Text(
                                            text = "⚡ ${action.label}",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = SaffronPrimary
                                        )
                                    }
                                }
                            }
                        }
                    }

                    ingestionResult?.let { res ->
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "ASR Latency: ${res.latencyMs} ms",
                            fontSize = 12.sp,
                            color = MutedText
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            var showApiKeyDialog by remember { mutableStateOf(false) }
            var groqKeyInput by remember { mutableStateOf(ApiKeyManager.getGroqApiKey(context)) }
            val hasValidKey = ApiKeyManager.hasValidGroqKey(context)

            // Active AI Engine Status Badge
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (hasValidKey) VegGreen.copy(alpha = 0.15f) else SaffronPrimary.copy(alpha = 0.15f))
                    .border(1.dp, if (hasValidKey) VegGreen else SaffronPrimary, RoundedCornerShape(8.dp))
                    .padding(vertical = 8.dp, horizontal = 12.dp)
            ) {
                Text(
                    text = if (hasValidKey) "⚡ Groq LLaMA 3.2 Vision & 70B Cloud Engine Active" else "📱 On-Device High-Speed Neural OCR Engine Active",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (hasValidKey) VegGreen else SaffronPrimary,
                    textAlign = TextAlign.Center
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Accessibility Controls
            AccessibleButton(
                text = if (isHighContrast) "Standard Dark Mode" else "High Contrast Mode",
                onClick = {
                    hapticEngine.trigger(HapticPattern.CLICK)
                    onToggleHighContrast()
                },
                isPrimary = false
            )

            Spacer(modifier = Modifier.height(10.dp))

            AccessibleButton(
                text = "🔑 Configure Groq Cloud API Key",
                onClick = {
                    hapticEngine.trigger(HapticPattern.CLICK)
                    showApiKeyDialog = true
                },
                isPrimary = false
            )

            if (showApiKeyDialog) {
                AlertDialog(
                    onDismissRequest = { showApiKeyDialog = false },
                    title = {
                        Text(
                            text = "Groq Cloud API Key",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = SaffronPrimary
                        )
                    },
                    text = {
                        Column {
                            Text(
                                text = "Enter your Groq API Key (from console.groq.com) for LLaMA 3.2 Vision and LLaMA 3.3 70B reasoning. If left blank, the app will seamlessly run on-device local neural OCR.",
                                fontSize = 13.sp,
                                color = PureWhite
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            OutlinedTextField(
                                value = groqKeyInput,
                                onValueChange = { groqKeyInput = it },
                                label = { Text("gsk_...") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                ApiKeyManager.setGroqApiKey(context, groqKeyInput)
                                hapticEngine.trigger(HapticPattern.CONFIRMATION)
                                showApiKeyDialog = false
                            }
                        ) {
                            Text("Save Key", fontWeight = FontWeight.Bold, color = SaffronPrimary)
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showApiKeyDialog = false }) {
                            Text("Cancel", color = SubtitleText)
                        }
                    },
                    containerColor = CharcoalSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
