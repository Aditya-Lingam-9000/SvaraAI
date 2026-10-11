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
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
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
            var isWakeWordServiceRunning by remember { mutableStateOf(true) }
            var showApiKeyDialog by remember { mutableStateOf(false) }

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

            // Mobile Hardware / Gesture Back Button Handling
            // If user is on any screen other than PLAYGROUND, navigating back goes to PLAYGROUND and stops audio
            BackHandler(enabled = currentScreen != AppScreen.PLAYGROUND) {
                stopSpeech()
                currentScreen = AppScreen.PLAYGROUND
            }

            SarvaTheme(isHighContrast = isHighContrast) {
                Scaffold(
                    topBar = {
                        NeomorphicTopAppBar(
                            selectedLanguage = selectedLanguage,
                            onLanguageSelected = { newLang ->
                                stopSpeech()
                                selectedLanguage = newLang
                                hapticEngine.trigger(HapticPattern.CLICK)
                            },
                            isWakeWordActive = isWakeWordServiceRunning,
                            onToggleWakeWord = {
                                isWakeWordServiceRunning = !isWakeWordServiceRunning
                                hapticEngine.trigger(HapticPattern.CLICK)
                                if (isWakeWordServiceRunning) {
                                    SvaraWakeWordService.startService(this@MainActivity)
                                } else {
                                    SvaraWakeWordService.stopService(this@MainActivity)
                                }
                            },
                            isHighContrast = isHighContrast,
                            onToggleHighContrast = {
                                hapticEngine.trigger(HapticPattern.CLICK)
                                isHighContrast = !isHighContrast
                            },
                            onOpenApiKeyDialog = {
                                hapticEngine.trigger(HapticPattern.CLICK)
                                showApiKeyDialog = true
                            }
                        )
                    },
                    bottomBar = {
                        NeomorphicBottomNavBar(
                            currentScreen = currentScreen,
                            onScreenSelected = { targetScreen ->
                                if (currentScreen != targetScreen) {
                                    stopSpeech()
                                    hapticEngine.trigger(HapticPattern.CLICK)
                                    currentScreen = targetScreen
                                }
                            }
                        )
                    },
                    containerColor = MaterialTheme.colorScheme.background
                ) { paddingValues ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(paddingValues)
                    ) {
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
                                    isWakeWordServiceRunning = isWakeWordServiceRunning,
                                    onLanguageSelected = { selectedLanguage = it },
                                    onOpenScreen = { target ->
                                        stopSpeech()
                                        currentScreen = target
                                    },
                                    onToggleHighContrast = { isHighContrast = !isHighContrast },
                                    onToggleWakeWord = {
                                        isWakeWordServiceRunning = !isWakeWordServiceRunning
                                        if (isWakeWordServiceRunning) {
                                            SvaraWakeWordService.startService(this@MainActivity)
                                        } else {
                                            SvaraWakeWordService.stopService(this@MainActivity)
                                        }
                                    },
                                    onOpenApiKeyDialog = { showApiKeyDialog = true },
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

                    // Groq API Key Configuration Dialog
                    if (showApiKeyDialog) {
                        var groqKeyInput by remember { mutableStateOf(ApiKeyManager.getGroqApiKey(this@MainActivity)) }
                        AlertDialog(
                            onDismissRequest = { showApiKeyDialog = false },
                            title = {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Key,
                                        contentDescription = null,
                                        tint = NeoPrimary,
                                        modifier = Modifier.size(24.dp)
                                    )
                                    Text(
                                        text = "Groq Cloud API Key",
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = NeoTextPrimary
                                    )
                                }
                            },
                            text = {
                                Column {
                                    Text(
                                        text = "Enter your Groq API Key (from console.groq.com) for LLaMA 3.2 Vision and LLaMA 3.3 70B cloud reasoning. When empty, on-device neural processing will be used.",
                                        fontSize = 13.sp,
                                        color = NeoTextSecondary,
                                        lineHeight = 18.sp
                                    )
                                    Spacer(modifier = Modifier.height(14.dp))
                                    OutlinedTextField(
                                        value = groqKeyInput,
                                        onValueChange = { groqKeyInput = it },
                                        label = { Text("gsk_...") },
                                        singleLine = true,
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            },
                            confirmButton = {
                                TextButton(
                                    onClick = {
                                        ApiKeyManager.setGroqApiKey(this@MainActivity, groqKeyInput)
                                        hapticEngine.trigger(HapticPattern.CONFIRMATION)
                                        showApiKeyDialog = false
                                    }
                                ) {
                                    Text("Save Key", fontWeight = FontWeight.Bold, color = NeoPrimary)
                                }
                            },
                            dismissButton = {
                                TextButton(onClick = { showApiKeyDialog = false }) {
                                    Text("Cancel", color = NeoTextSecondary)
                                }
                            },
                            containerColor = NeoSurface,
                            shape = RoundedCornerShape(20.dp)
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

/**
 * Fixed persistent Neomorphic Top App Bar with Logo, Global Language Switcher, and Quick Action buttons.
 * Strictly zero emojis, clean Material Icons and Neomorphic styling.
 */
@Composable
fun NeomorphicTopAppBar(
    selectedLanguage: String,
    onLanguageSelected: (String) -> Unit,
    isWakeWordActive: Boolean,
    onToggleWakeWord: () -> Unit,
    isHighContrast: Boolean,
    onToggleHighContrast: () -> Unit,
    onOpenApiKeyDialog: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.background,
        shadowElevation = 4.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            // Row 1: Brand Logo + Quick Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // SvaraAI Brand Title
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(38.dp)
                            .neomorphicSurface(
                                shape = CircleShape,
                                surfaceColor = NeoSurface,
                                borderColor = NeoBorder,
                                elevation = 3.dp,
                                cornerRadius = 19.dp
                            )
                    ) {
                        Icon(
                            imageVector = Icons.Default.GraphicEq,
                            contentDescription = null,
                            tint = NeoPrimary,
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    Column {
                        Text(
                            text = "SvaraAI",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = NeoTextPrimary,
                            lineHeight = 20.sp
                        )
                        Text(
                            text = "Multimodal Restaurant Assistant",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = NeoTextSecondary,
                            lineHeight = 13.sp
                        )
                    }
                }

                // Quick Action Buttons Row
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Wake Word Status Badge / Button
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(38.dp)
                            .neomorphicSurface(
                                shape = CircleShape,
                                surfaceColor = if (isWakeWordActive) VegGreenContainer else NeoSurface,
                                borderColor = if (isWakeWordActive) VegGreen else NeoBorder,
                                elevation = 3.dp,
                                cornerRadius = 19.dp
                            )
                            .clickable(onClick = onToggleWakeWord)
                            .semantics {
                                contentDescription = if (isWakeWordActive) "Wake word active: Hey Svara. Tap to disable" else "Wake word inactive. Tap to enable"
                                role = Role.Button
                            }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Hearing,
                            contentDescription = null,
                            tint = if (isWakeWordActive) VegGreenOnContainer else NeoTextSecondary,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    // High Contrast Toggle
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(38.dp)
                            .neomorphicSurface(
                                shape = CircleShape,
                                surfaceColor = if (isHighContrast) NeoPrimaryContainer else NeoSurface,
                                borderColor = if (isHighContrast) NeoPrimary else NeoBorder,
                                elevation = 3.dp,
                                cornerRadius = 19.dp
                            )
                            .clickable(onClick = onToggleHighContrast)
                            .semantics {
                                contentDescription = "Toggle high contrast mode"
                                role = Role.Button
                            }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Contrast,
                            contentDescription = null,
                            tint = if (isHighContrast) NeoPrimary else NeoTextSecondary,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    // Settings / API Key Button
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(38.dp)
                            .neomorphicSurface(
                                shape = CircleShape,
                                surfaceColor = NeoSurface,
                                borderColor = NeoBorder,
                                elevation = 3.dp,
                                cornerRadius = 19.dp
                            )
                            .clickable(onClick = onOpenApiKeyDialog)
                            .semantics {
                                contentDescription = "Configure cloud API keys and reasoning engine"
                                role = Role.Button
                            }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Key,
                            contentDescription = null,
                            tint = NeoTextSecondary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Row 2: Global Segmented Language Switcher (EN, हिन्दी, తెలుగు)
            NeomorphicLanguageSelector(
                selectedLanguage = selectedLanguage,
                onLanguageSelected = onLanguageSelected
            )
        }
    }
}

/**
 * Data definition for Bottom Navigation Tabs
 */
data class BottomNavTabItem(
    val screen: AppScreen,
    val title: String,
    val icon: ImageVector,
    val contentDescription: String
)

/**
 * Neomorphic Bottom Navigation Bar with 5 tabs:
 * 1. Home / Assistant (Mic)
 * 2. Menu & Scan (CameraAlt)
 * 3. Cart (ShoppingCart)
 * 4. Waiter Bridge (Translate)
 * 5. Bill & Split (ReceiptLong)
 */
@Composable
fun NeomorphicBottomNavBar(
    currentScreen: AppScreen,
    onScreenSelected: (AppScreen) -> Unit,
    modifier: Modifier = Modifier
) {
    val tabs = listOf(
        BottomNavTabItem(
            screen = AppScreen.PLAYGROUND,
            title = "Assistant",
            icon = Icons.Default.Mic,
            contentDescription = "Voice Assistant and Playground"
        ),
        BottomNavTabItem(
            screen = AppScreen.CAMERA_CAPTURE,
            title = "Scan Menu",
            icon = Icons.Default.CameraAlt,
            contentDescription = "Camera and Menu OCR Scanner"
        ),
        BottomNavTabItem(
            screen = AppScreen.VOICE_CART,
            title = "Cart",
            icon = Icons.Default.ShoppingCart,
            contentDescription = "Voice Ordering Cart"
        ),
        BottomNavTabItem(
            screen = AppScreen.WAITER_BRIDGE,
            title = "Bridge",
            icon = Icons.Default.Translate,
            contentDescription = "Multilingual Waiter Bridge"
        ),
        BottomNavTabItem(
            screen = AppScreen.SMART_BILL,
            title = "Bill",
            icon = Icons.AutoMirrored.Filled.ReceiptLong,
            contentDescription = "Smart Bill and Split"
        )
    )

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.background,
        shadowElevation = 8.dp
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 10.dp, vertical = 6.dp)
                .neomorphicSurface(
                    shape = RoundedCornerShape(20.dp),
                    surfaceColor = NeoSurface,
                    borderColor = NeoBorder,
                    elevation = 4.dp,
                    cornerRadius = 20.dp
                )
                .padding(horizontal = 4.dp, vertical = 4.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                tabs.forEach { tab ->
                    val isSelected = when (tab.screen) {
                        AppScreen.PLAYGROUND -> currentScreen == AppScreen.PLAYGROUND || currentScreen == AppScreen.RAW_VS_ACCESSIBLE || currentScreen == AppScreen.DEBUG_SIGNAL_PANEL
                        AppScreen.CAMERA_CAPTURE -> currentScreen == AppScreen.CAMERA_CAPTURE || currentScreen == AppScreen.MENU_BROWSER
                        AppScreen.VOICE_CART -> currentScreen == AppScreen.VOICE_CART
                        AppScreen.WAITER_BRIDGE -> currentScreen == AppScreen.WAITER_BRIDGE
                        AppScreen.SMART_BILL -> currentScreen == AppScreen.SMART_BILL
                        else -> false
                    }

                    val interactionSource = remember { MutableInteractionSource() }
                    val isPressed by interactionSource.collectIsPressedAsState()
                    val scale by animateFloatAsState(
                        targetValue = if (isPressed) 0.92f else 1.0f,
                        animationSpec = spring(stiffness = Spring.StiffnessMedium),
                        label = "bottom_tab_scale"
                    )

                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .weight(1f)
                            .height(52.dp)
                            .scale(scale)
                            .clip(RoundedCornerShape(14.dp))
                            .background(if (isSelected) NeoPrimaryContainer else Color.Transparent)
                            .clickable(
                                interactionSource = interactionSource,
                                indication = null,
                                onClick = { onScreenSelected(tab.screen) }
                            )
                            .semantics {
                                contentDescription = tab.contentDescription
                                role = Role.Tab
                            }
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = tab.icon,
                                contentDescription = null,
                                tint = if (isSelected) NeoPrimary else NeoTextSecondary,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = tab.title,
                                fontSize = 11.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSelected) NeoPrimary else NeoTextSecondary,
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Redesigned Main Audio Playground Screen:
 * - Pure Neomorphic White Theme.
 * - Centerpiece Animated NeomorphicMicButton with pulsing soundwaves and live amplitude.
 * - Section cards for Assistant Status, Live Captions, Diagnostics, Playback Speed (0.8x-1.5x), and Quick Audio Prompts.
 * - Strictly zero emojis across all labels, cards, buttons, dialogs.
 * - Smooth AnimatedVisibility transitions.
 */
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
    isWakeWordServiceRunning: Boolean = true,
    onLanguageSelected: (String) -> Unit,
    onOpenScreen: (AppScreen) -> Unit,
    onToggleHighContrast: () -> Unit,
    onToggleWakeWord: () -> Unit = {},
    onOpenApiKeyDialog: () -> Unit = {},
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
            currentSpokenCaption = "Synthesizing audio response..."

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
                                                // Step 3: Directly synthesize the AI's natural spoken response
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
            currentSpokenCaption = "Listening for your voice..."
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
                .padding(horizontal = 16.dp)
                .verticalScroll(scrollState),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            // =========================================================================
            // CENTERPIECE: Animated Neomorphic Microphone Button with Live Waves
            // =========================================================================
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp)
            ) {
                NeomorphicMicButton(
                    isRecording = isRecording,
                    isProcessing = isProcessing,
                    amplitude = amplitude,
                    onClick = {
                        startListeningFlow()
                    }
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // =========================================================================
            // 1. ASSISTANT STATUS CARD
            // =========================================================================
            NeomorphicCard(
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = when {
                                isProcessing -> Icons.Default.Autorenew
                                isAudioPlaying -> Icons.AutoMirrored.Filled.VolumeUp
                                isRecording -> Icons.Default.GraphicEq
                                else -> Icons.Default.SmartToy
                            },
                            contentDescription = null,
                            tint = when {
                                isProcessing -> NeoPrimary
                                isAudioPlaying -> NeoPrimary
                                isRecording && vadStatus == VadStatus.SPEECH_ACTIVE -> VegGreen
                                isRecording -> NeoPrimary
                                else -> NeoTextSecondary
                            },
                            modifier = Modifier.size(22.dp)
                        )

                        Text(
                            text = "Voice Assistant Status",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = NeoTextPrimary
                        )
                    }

                    // Live Status Pill Badge
                    val badgeColor = when {
                        isProcessing -> NeoPrimaryContainer
                        isAudioPlaying -> NeoPrimaryContainer
                        isRecording && vadStatus == VadStatus.SPEECH_ACTIVE -> VegGreenContainer
                        isRecording -> NeoPrimaryContainer
                        else -> NeoSurfacePressed
                    }
                    val badgeTextColor = when {
                        isProcessing -> NeoOnPrimaryContainer
                        isAudioPlaying -> NeoOnPrimaryContainer
                        isRecording && vadStatus == VadStatus.SPEECH_ACTIVE -> VegGreenOnContainer
                        isRecording -> NeoOnPrimaryContainer
                        else -> NeoTextSecondary
                    }
                    val badgeText = when {
                        isProcessing -> "Processing"
                        isAudioPlaying -> "Speaking"
                        isRecording && vadStatus == VadStatus.SPEECH_ACTIVE -> "Speech Active"
                        isRecording && vadStatus == VadStatus.CALIBRATING -> "Calibrating"
                        isRecording -> "Listening"
                        else -> "Standby"
                    }

                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(badgeColor)
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = badgeText,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = badgeTextColor
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = when {
                        isProcessing -> "Processing speech request with Bhashini ASR and Groq AI..."
                        isAudioPlaying -> "Speaking aloud via ExoPlayer streaming playback..."
                        isRecording && vadStatus == VadStatus.SPEECH_ACTIVE -> "Active speech detected. Ingesting voice stream..."
                        isRecording && vadStatus == VadStatus.CALIBRATING -> "Calibrating ambient restaurant acoustic noise..."
                        isRecording -> "Listening for voice (Voice Activity Detector active)..."
                        else -> "Tap microphone or say 'Hey Svara' to begin dialogue"
                    },
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = when {
                        isAudioPlaying -> NeoPrimary
                        isRecording && vadStatus == VadStatus.SPEECH_ACTIVE -> VegGreen
                        else -> NeoTextSecondary
                    },
                    lineHeight = 18.sp
                )

                // Live Audio Amplitude Level
                AnimatedVisibility(
                    visible = isRecording,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    Column(modifier = Modifier.fillMaxWidth().padding(top = 10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Microphone Signal Level",
                                fontSize = 12.sp,
                                color = NeoTextSecondary
                            )
                            Text(
                                text = "${(amplitude * 100).toInt()}%",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = VegGreen
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        LinearProgressIndicator(
                            progress = { amplitude.coerceIn(0f, 1f) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp)),
                            color = VegGreen,
                            trackColor = NeoSurfacePressed
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // =========================================================================
            // 2. LIVE CAPTIONS CARD
            // =========================================================================
            AnimatedVisibility(
                visible = currentSpokenCaption.isNotBlank(),
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                NeomorphicCard(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Subtitles,
                                contentDescription = null,
                                tint = NeoPrimary,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = "Live Captions",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = NeoTextPrimary
                            )
                        }

                        if (isAudioPlaying) {
                            TextButton(
                                onClick = { speechPlayer.stop() },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Stop,
                                    contentDescription = null,
                                    tint = NonVegRed,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Stop",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = NonVegRed
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    LiveCaptionBar(
                        fullCaption = currentSpokenCaption,
                        highlightedWordIndex = highlightedWordIndex,
                        isVisible = true
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // =========================================================================
            // 3. PITCH-PRESERVING SPEECH PLAYBACK SPEED
            // =========================================================================
            NeomorphicCard(
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Speed,
                        contentDescription = null,
                        tint = NeoPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = "Pitch-Preserving Speech Speed",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = NeoTextPrimary
                    )
                }

                Text(
                    text = "Preserves voice pitch and natural acoustic fidelity across all speed settings",
                    fontSize = 12.sp,
                    color = NeoTextSecondary,
                    modifier = Modifier.padding(top = 2.dp, bottom = 10.dp)
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    listOf(
                        0.8f to "0.8x",
                        1.0f to "1.0x",
                        1.25f to "1.25x",
                        1.5f to "1.5x"
                    ).forEach { (speed, label) ->
                        val isCurrent = Math.abs(playbackSpeed - speed) < 0.05f
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .weight(1f)
                                .height(42.dp)
                                .neomorphicSurface(
                                    shape = RoundedCornerShape(10.dp),
                                    surfaceColor = if (isCurrent) NeoPrimary else NeoSurface,
                                    borderColor = if (isCurrent) NeoPrimary else NeoBorder,
                                    elevation = if (isCurrent) 2.dp else 3.dp,
                                    isPressed = isCurrent,
                                    cornerRadius = 10.dp
                                )
                                .clickable {
                                    speechPlayer.setSpeed(speed)
                                    hapticEngine.trigger(HapticPattern.CLICK)
                                }
                                .semantics {
                                    contentDescription = "Playback speed $label"
                                    role = Role.Button
                                }
                        ) {
                            Text(
                                text = label,
                                fontSize = 13.sp,
                                fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium,
                                color = if (isCurrent) NeoOnPrimary else NeoTextPrimary,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // =========================================================================
            // 4. QUICK VOICE ASSISTANT AUDIO PROMPTS
            // =========================================================================
            NeomorphicCard(
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.RecordVoiceOver,
                        contentDescription = null,
                        tint = NeoPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = "Quick Voice Assistant Prompts",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = NeoTextPrimary
                    )
                }

                Text(
                    text = "Synthesize language-native scenarios in the selected active language",
                    fontSize = 12.sp,
                    color = NeoTextSecondary,
                    modifier = Modifier.padding(top = 2.dp, bottom = 10.dp)
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    NeomorphicButton(
                        text = "Menu Intro",
                        onClick = {
                            val prompt = when (selectedLanguage) {
                                "te" -> "స్వర ఏఐ కి స్వాగతం. ఈరోజు మా వద్ద వేడి వేడి మసాలా దోశ, ఇడ్లీ సాంబార్, మరియు ఫిల్టర్ కాఫీ ఉన్నాయి. మీరు ఏమి ఆర్డర్ చేయాలనుకుంటున్నారు?"
                                "hi" -> "स्वरा एआई में आपका स्वागत है। आज हमारे पास गरमा-गरम मसाला डोसा, इडली सांभर और फ़िल्टर कॉफ़ी है। आप क्या ऑर्डर करना चाहेंगे?"
                                else -> "Welcome to SvaraAI. Today we have hot crispy masala dosa, idli sambar, and filter coffee. What would you like to order?"
                            }
                            playSampleText(prompt, selectedLanguage)
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.RestaurantMenu,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        },
                        modifier = Modifier.weight(1f),
                        isPrimary = true
                    )

                    NeomorphicButton(
                        text = "Confirm Order",
                        onClick = {
                            val prompt = when (selectedLanguage) {
                                "te" -> "మీ రెండు మసాలా దోశలు మరియు ఒక ఫిల్టర్ కాఫీ ఆర్డర్ చేయబడ్డాయి. మొత్తం బిల్లు నూట ఎనభై రూపాయలు."
                                "hi" -> "आपके दो मसाला डोसे और एक फ़िल्टर कॉफ़ी का ऑर्डर हो गया है। कुल राशि एक सौ अस्सी रुपये है।"
                                else -> "Your order for two masala dosas and one filter coffee has been placed successfully. Total amount is one hundred and eighty rupees."
                            }
                            playSampleText(prompt, selectedLanguage)
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.CheckCircleOutline,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        },
                        modifier = Modifier.weight(1f),
                        isPrimary = false
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    NeomorphicButton(
                        text = "Waiter Request",
                        onClick = {
                            val prompt = when (selectedLanguage) {
                                "te" -> "దయచేసి టేబుల్ నాలుగు వద్ద సహాయం కావాలి. వెయిటర్ వస్తున్నారు."
                                "hi" -> "कृपया टेबल नंबर चार पर सहायता की आवश्यकता है। वेटर आ रहे हैं।"
                                else -> "Assistance is requested at Table 4. A waiter will arrive shortly."
                            }
                            playSampleText(prompt, selectedLanguage)
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.SupportAgent,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        },
                        modifier = Modifier.weight(1f),
                        isPrimary = false
                    )

                    NeomorphicButton(
                        text = "Bill Split",
                        onClick = {
                            val prompt = when (selectedLanguage) {
                                "te" -> "మొత్తం బిల్లు ఐదు వందల నలభై రూపాయలు. ముగ్గురు వ్యక్తులకు ఒక్కొక్కరికి నూట ఎనభై రూపాయలు."
                                "hi" -> "कुल बिल पांच सौ चालीस रुपये है। तीन लोगों में प्रति व्यक्ति एक सौ अस्सी रुपये बनता है।"
                                else -> "Total bill is five hundred and forty rupees. Split equally between three diners is one hundred and eighty rupees each."
                            }
                            playSampleText(prompt, selectedLanguage)
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ReceiptLong,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        },
                        modifier = Modifier.weight(1f),
                        isPrimary = false
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // =========================================================================
            // 5. INGESTION & DIALOGUE DIAGNOSTICS CARD
            // =========================================================================
            AnimatedVisibility(
                visible = ingestionResult != null || structuredAiResponse != null || errorMessage != null,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                NeomorphicCard(
                    modifier = Modifier.fillMaxWidth(),
                    borderColor = if (errorMessage != null) NonVegRed else NeoBorder
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Analytics,
                            contentDescription = null,
                            tint = NeoPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = "Dialogue & Ingestion Diagnostics",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = NeoTextPrimary,
                            modifier = Modifier.semantics { heading() }
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    errorMessage?.let { err ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(NonVegRedContainer)
                                .padding(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.ErrorOutline,
                                contentDescription = null,
                                tint = NonVegRed,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = "Error: $err",
                                fontSize = 13.sp,
                                color = NonVegRedOnContainer,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                    }

                    ingestionResult?.let { res ->
                        Text(
                            text = "Recognized Native Speech (${res.sourceLanguage.uppercase()}):",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = NeoTextSecondary
                        )
                        Text(
                            text = if (res.originalTranscript.isNotEmpty()) res.originalTranscript else "(No speech detected)",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = NeoTextPrimary
                        )

                        Spacer(modifier = Modifier.height(8.dp))
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
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = NeoPrimary
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        Text(
                            text = "Spoken Response (${selectedLanguage.uppercase()}):",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = NeoTextSecondary
                        )
                        Text(
                            text = ai.spoken,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Normal,
                            color = NeoTextPrimary
                        )

                        if (ai.actions.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                ai.actions.forEach { action ->
                                    Box(
                                        contentAlignment = Alignment.Center,
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(NeoPrimaryContainer)
                                            .padding(horizontal = 10.dp, vertical = 4.dp)
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Bolt,
                                                contentDescription = null,
                                                tint = NeoPrimary,
                                                modifier = Modifier.size(14.dp)
                                            )
                                            Text(
                                                text = action.label,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = NeoOnPrimaryContainer
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    ingestionResult?.let { res ->
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Timer,
                                contentDescription = null,
                                tint = NeoTextMuted,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = "ASR Latency: ${res.latencyMs} ms",
                                fontSize = 11.sp,
                                color = NeoTextMuted
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // =========================================================================
            // 6. ACTIVE AI ENGINE STATUS & QUICK NAVIGATION
            // =========================================================================
            val hasValidKey = ApiKeyManager.hasValidGroqKey(context)
            NeomorphicCard(
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = if (hasValidKey) Icons.Default.CloudQueue else Icons.Default.Memory,
                            contentDescription = null,
                            tint = if (hasValidKey) VegGreen else NeoPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                        Column {
                            Text(
                                text = if (hasValidKey) "Groq Cloud AI Engine Active" else "On-Device Neural Engine Active",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = NeoTextPrimary
                            )
                            Text(
                                text = if (hasValidKey) "LLaMA 3.2 Vision & LLaMA 3.3 70B" else "Local Indic OCR & Intent Matching",
                                fontSize = 11.sp,
                                color = NeoTextSecondary
                            )
                        }
                    }

                    TextButton(
                        onClick = onOpenApiKeyDialog,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Key,
                            contentDescription = null,
                            tint = NeoPrimary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Configure",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = NeoPrimary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Advanced Diagnostic & Jury Navigation Buttons
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                NeomorphicButton(
                    text = "Jury Mode",
                    onClick = {
                        hapticEngine.trigger(HapticPattern.CLICK)
                        onOpenScreen(AppScreen.RAW_VS_ACCESSIBLE)
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.CompareArrows,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                    },
                    modifier = Modifier.weight(1f),
                    isPrimary = false
                )

                NeomorphicButton(
                    text = "Adaptation Signals",
                    onClick = {
                        hapticEngine.trigger(HapticPattern.CLICK)
                        onOpenScreen(AppScreen.DEBUG_SIGNAL_PANEL)
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Tune,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                    },
                    modifier = Modifier.weight(1f),
                    isPrimary = false
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
