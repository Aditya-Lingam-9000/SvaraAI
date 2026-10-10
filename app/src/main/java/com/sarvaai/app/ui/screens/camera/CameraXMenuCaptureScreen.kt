package com.sarvaai.app.ui.screens.camera

import android.Manifest
import android.content.Context
import android.util.Log
import android.view.ViewGroup
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.sarvaai.app.audio.earcons.EarconEngine
import com.sarvaai.app.audio.earcons.EarconType
import com.sarvaai.app.audio.haptics.HapticFeedbackEngine
import com.sarvaai.app.audio.haptics.HapticPattern
import com.sarvaai.app.menu.analyzer.CoachingCue
import com.sarvaai.app.menu.analyzer.MenuQualityAnalyzer
import com.sarvaai.app.menu.coaching.AuditoryCoachingEngine
import com.sarvaai.app.menu.extractor.MenuExtractor
import com.sarvaai.app.menu.extractor.ParsedMenuResult
import com.sarvaai.app.ui.components.AccessibleButton
import com.sarvaai.app.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asExecutor
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import java.util.concurrent.Executors

@Composable
fun CameraXMenuCaptureScreen(
    menuExtractor: MenuExtractor,
    hapticEngine: HapticFeedbackEngine,
    earconEngine: EarconEngine,
    selectedLanguage: String,
    onSpeakAnnouncement: (String) -> Unit,
    onMenuExtracted: (ParsedMenuResult) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()

    var hasCameraPermission by remember { mutableStateOf(false) }
    var currentCue by remember { mutableStateOf<CoachingCue?>(null) }
    var isCapturing by remember { mutableStateOf(false) }
    var isProcessingVision by remember { mutableStateOf(false) }
    var statusText by remember { mutableStateOf("Position camera over restaurant menu") }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    var imageCaptureUseCase by remember { mutableStateOf<ImageCapture?>(null) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCameraPermission = granted
        if (granted) {
            hapticEngine.trigger(HapticPattern.CONFIRMATION)
        } else {
            errorMessage = "Camera permission is required to capture and read physical menus."
        }
    }

    LaunchedEffect(Unit) {
        permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    fun capturePhoto() {
        val capture = imageCaptureUseCase ?: return
        if (isCapturing || isProcessingVision) return

        isCapturing = true
        hapticEngine.trigger(HapticPattern.CAMERA_STABLE)
        earconEngine.play(EarconType.LISTEN_STOP)
        statusText = "Capturing menu photo..."

        val executor = Executors.newSingleThreadExecutor()
        capture.takePicture(
            executor,
            object : ImageCapture.OnImageCapturedCallback() {
                override fun onCaptureSuccess(imageProxy: ImageProxy) {
                    val buffer = imageProxy.planes[0].buffer
                    val bytes = ByteArray(buffer.remaining())
                    buffer.get(bytes)
                    imageProxy.close()

                    scope.launch(Dispatchers.Main) {
                        isCapturing = false
                        isProcessingVision = true
                        statusText = "Extracting menu sections and dishes with AI Vision..."
                        hapticEngine.trigger(HapticPattern.CONFIRMATION)
                        onSpeakAnnouncement(
                            when (selectedLanguage) {
                                "te" -> "మెనూ ఫోటో తీయబడింది. వంటకాలను గుర్తిస్తున్నాము..."
                                "hi" -> "मेनू की तस्वीर ले ली गई है। विवरण पढ़ा जा रहा है..."
                                else -> "Photo captured. Extracting menu with AI Vision..."
                            }
                        )

                        val res = menuExtractor.extractAndPersistMenu(bytes)
                        isProcessingVision = false
                        when (res) {
                            is com.sarvaai.app.core.result.Result.Success -> {
                                hapticEngine.trigger(HapticPattern.CONFIRMATION)
                                earconEngine.play(EarconType.ORDER_CONFIRMED)
                                onMenuExtracted(res.data)
                            }
                            is com.sarvaai.app.core.result.Result.Error -> {
                                errorMessage = res.message
                                hapticEngine.trigger(HapticPattern.ERROR_ALERT)
                                earconEngine.play(EarconType.ERROR_ALERT)
                            }
                            else -> {}
                        }
                    }
                }

                override fun onError(exception: ImageCaptureException) {
                    scope.launch(Dispatchers.Main) {
                        isCapturing = false
                        errorMessage = "Camera capture failed: ${exception.message}"
                        hapticEngine.trigger(HapticPattern.ERROR_ALERT)
                        earconEngine.play(EarconType.ERROR_ALERT)
                    }
                }
            }
        )
    }

    Surface(
        modifier = modifier.fillMaxSize(),
        color = CharcoalBackground
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            if (hasCameraPermission) {
                AndroidView(
                    factory = { ctx ->
                        val previewView = PreviewView(ctx).apply {
                            layoutParams = ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT
                            )
                            scaleType = PreviewView.ScaleType.FILL_CENTER
                        }

                        val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                        cameraProviderFuture.addListener({
                            val cameraProvider = cameraProviderFuture.get()

                            val preview = Preview.Builder().build().also {
                                it.setSurfaceProvider(previewView.surfaceProvider)
                            }

                            val capture = ImageCapture.Builder()
                                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                                .build()
                            imageCaptureUseCase = capture

                            val qualityAnalyzer = MenuQualityAnalyzer(
                                onCoachingFeedback = { cue ->
                                    currentCue = cue
                                    val prompt = AuditoryCoachingEngine.getSpokenPrompt(cue, selectedLanguage)
                                    statusText = prompt
                                    onSpeakAnnouncement(prompt)
                                    if (cue == CoachingCue.PERFECT_ALIGNMENT) {
                                        hapticEngine.trigger(HapticPattern.CAMERA_STABLE)
                                    }
                                },
                                onStabilityConfirmed = {
                                    capturePhoto()
                                }
                            )

                            val analysis = ImageAnalysis.Builder()
                                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                                .build().also {
                                    it.setAnalyzer(Executors.newSingleThreadExecutor(), qualityAnalyzer)
                                }

                            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

                            try {
                                cameraProvider.unbindAll()
                                cameraProvider.bindToLifecycle(
                                    lifecycleOwner,
                                    cameraSelector,
                                    preview,
                                    capture,
                                    analysis
                                )
                            } catch (e: Exception) {
                                Log.e("CameraXScreen", "Use case binding failed", e)
                            }
                        }, ContextCompat.getMainExecutor(ctx))

                        previewView
                    },
                    modifier = Modifier.fillMaxSize()
                )

                // High Contrast Framing Target Box
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .fillMaxHeight(0.75f)
                            .border(
                                width = 3.dp,
                                color = if (currentCue == CoachingCue.PERFECT_ALIGNMENT) VegGreen else SaffronPrimary,
                                shape = RoundedCornerShape(16.dp)
                            )
                    )
                }
            }

            // Top Header Overlay
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .background(Color.Black.copy(alpha = 0.75f))
                    .padding(horizontal = 20.dp, vertical = 16.dp)
                    .systemBarsPadding()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Blind-Accessible Menu Capture",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = SaffronPrimary,
                        modifier = Modifier.semantics { heading() }
                    )

                    IconButton(
                        onClick = onClose,
                        modifier = Modifier.size(44.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "Close Camera", tint = PureWhite)
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = statusText,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (currentCue == CoachingCue.PERFECT_ALIGNMENT) VegGreen else HighContrastYellow,
                    textAlign = TextAlign.Start
                )
            }

            // Bottom Shutter Controls Overlay
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .background(Color.Black.copy(alpha = 0.85f))
                    .padding(horizontal = 20.dp, vertical = 20.dp)
                    .systemBarsPadding(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                errorMessage?.let { err ->
                    Text(
                        text = err,
                        fontSize = 13.sp,
                        color = NonVegRed,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // Manual Capture Button (Accessible 64dp+ tap target)
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(72.dp)
                            .clip(CircleShape)
                            .background(if (isProcessingVision) CharcoalSurfaceVariant else SaffronPrimary)
                            .border(3.dp, PureWhite, CircleShape)
                            .clickable(enabled = !isProcessingVision && !isCapturing) {
                                capturePhoto()
                            }
                            .semantics {
                                contentDescription = "Capture Menu Photo manually"
                            }
                    ) {
                        if (isProcessingVision) {
                            CircularProgressIndicator(color = SaffronPrimary, modifier = Modifier.size(32.dp))
                        } else {
                            Icon(imageVector = Icons.Default.CameraAlt, contentDescription = null, tint = SaffronOnPrimary, modifier = Modifier.size(36.dp))
                        }
                    }

                    // Load Sample Restaurant Menu Button (for instant testing)
                    AccessibleButton(
                        text = "Load Sample Grand Menu",
                        onClick = {
                            scope.launch {
                                isProcessingVision = true
                                statusText = "Loading sample restaurant menu into Room DB..."
                                onSpeakAnnouncement("Loading sample restaurant menu...")
                                val sampleRes = menuExtractor.extractAndPersistMenu(ByteArray(0))
                                isProcessingVision = false
                                if (sampleRes is com.sarvaai.app.core.result.Result.Success) {
                                    hapticEngine.trigger(HapticPattern.CONFIRMATION)
                                    earconEngine.play(EarconType.ORDER_CONFIRMED)
                                    onMenuExtracted(sampleRes.data)
                                }
                            }
                        },
                        modifier = Modifier.weight(1f),
                        isPrimary = false
                    )
                }
            }
        }
    }
}
