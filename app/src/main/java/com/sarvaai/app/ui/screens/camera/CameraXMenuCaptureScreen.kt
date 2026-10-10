package com.sarvaai.app.ui.screens.camera

import android.Manifest
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
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
import com.sarvaai.app.menu.extractor.MenuExtractor
import com.sarvaai.app.menu.extractor.ParsedMenuResult
import com.sarvaai.app.ui.components.AccessibleButton
import com.sarvaai.app.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.Executors

@Composable
fun CameraXMenuCaptureScreen(
    menuExtractor: MenuExtractor,
    hapticEngine: HapticFeedbackEngine,
    earconEngine: EarconEngine,
    selectedLanguage: String,
    onSpeakAnnouncement: (String) -> Unit,
    onStopAnnouncement: () -> Unit = {},
    onMenuExtracted: (ParsedMenuResult) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()

    DisposableEffect(lifecycleOwner) {
        onDispose {
            onStopAnnouncement()
            try {
                val cameraProvider = ProcessCameraProvider.getInstance(context).get()
                cameraProvider.unbindAll()
            } catch (e: Exception) {
                // Ignore teardown exception
            }
        }
    }

    var hasCameraPermission by remember { mutableStateOf(false) }
    var isCapturing by remember { mutableStateOf(false) }
    var isProcessingVision by remember { mutableStateOf(false) }
    var statusText by remember { mutableStateOf("Point camera at restaurant menu and tap Capture") }
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

    fun processCapturedBytes(bytes: ByteArray) {
        scope.launch(Dispatchers.Main) {
            isCapturing = false
            isProcessingVision = true
            errorMessage = null
            statusText = "AI Vision is extracting menu categories, dishes and prices..."
            hapticEngine.trigger(HapticPattern.CONFIRMATION)
            onSpeakAnnouncement(
                when (selectedLanguage) {
                    "te" -> "మెనూ ఫోటో విశ్లేషిస్తున్నాము. దయచేసి వేచి ఉండండి..."
                    "hi" -> "मेनू का विश्लेषण किया जा रहा है। कृपया प्रतीक्षा करें..."
                    else -> "Analyzing menu photo with AI Vision. Please wait..."
                }
            )

            val res = menuExtractor.extractAndPersistMenu(bytes)
            isProcessingVision = false
            when (res) {
                is com.sarvaai.app.core.result.Result.Success -> {
                    hapticEngine.trigger(HapticPattern.CONFIRMATION)
                    earconEngine.play(EarconType.ORDER_CONFIRMED)
                    val sectionCount = res.data.sections.size
                    val itemCount = res.data.items.size
                    onSpeakAnnouncement(
                        when (selectedLanguage) {
                            "te" -> "మెనూ విజయవంతంగా లోడ్ అయింది. $sectionCount విభాగాలు మరియు $itemCount వంటకాలు గుర్తించబడ్డాయి."
                            "hi" -> "मेनू सफलतापूर्वक लोड हुआ। $sectionCount श्रेणियां और $itemCount व्यंजन मिले।"
                            else -> "Menu loaded successfully with $sectionCount categories and $itemCount dishes."
                        }
                    )
                    onMenuExtracted(res.data)
                }
                is com.sarvaai.app.core.result.Result.Error -> {
                    errorMessage = res.message
                    statusText = res.message
                    hapticEngine.trigger(HapticPattern.ERROR_ALERT)
                    earconEngine.play(EarconType.ERROR_ALERT)
                    onSpeakAnnouncement(res.message)
                }
                else -> {}
            }
        }
    }

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch(Dispatchers.IO) {
                try {
                    val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                    if (bytes != null && bytes.isNotEmpty()) {
                        withContext(Dispatchers.Main) {
                            processCapturedBytes(bytes)
                        }
                    } else {
                        withContext(Dispatchers.Main) {
                            errorMessage = "Could not read selected photo file."
                        }
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        errorMessage = "Failed to load image: ${e.message}"
                    }
                }
            }
        }
    }

    fun capturePhoto() {
        val capture = imageCaptureUseCase ?: return
        if (isCapturing || isProcessingVision) return

        isCapturing = true
        errorMessage = null
        hapticEngine.trigger(HapticPattern.CAMERA_STABLE)
        earconEngine.play(EarconType.LISTEN_STOP)
        statusText = "Capturing photo..."

        val tempFile = File(context.cacheDir, "menu_capture_${System.currentTimeMillis()}.jpg")
        val outputOptions = ImageCapture.OutputFileOptions.Builder(tempFile).build()
        val executor = Executors.newSingleThreadExecutor()

        capture.takePicture(
            outputOptions,
            executor,
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                    val bytes = try {
                        val fileBytes = tempFile.readBytes()
                        tempFile.delete()
                        fileBytes
                    } catch (e: Exception) {
                        ByteArray(0)
                    }

                    if (bytes.isNotEmpty()) {
                        processCapturedBytes(bytes)
                    } else {
                        scope.launch(Dispatchers.Main) {
                            isCapturing = false
                            errorMessage = "Failed to read captured photo."
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
                                .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                                .build()
                            imageCaptureUseCase = capture

                            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

                            try {
                                cameraProvider.unbindAll()
                                cameraProvider.bindToLifecycle(
                                    lifecycleOwner,
                                    cameraSelector,
                                    preview,
                                    capture
                                )
                            } catch (e: Exception) {
                                Log.e("CameraXScreen", "Camera binding failed", e)
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
                        .padding(horizontal = 24.dp, vertical = 80.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .fillMaxHeight(0.70f)
                            .border(
                                width = 3.dp,
                                color = SaffronPrimary,
                                shape = RoundedCornerShape(16.dp)
                            )
                    )
                }
            }

            // Processing Vision Full Screen Overlay
            if (isProcessingVision) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.85f)),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(32.dp)
                    ) {
                        CircularProgressIndicator(
                            color = SaffronPrimary,
                            strokeWidth = 4.dp,
                            modifier = Modifier.size(64.dp)
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                        Text(
                            text = "Extracting Menu with AI Vision",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = PureWhite,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Categorizing dishes, prices, and dietary information accurately...",
                            fontSize = 14.sp,
                            color = SubtitleText,
                            textAlign = TextAlign.Center
                        )
                    }
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
                        text = "Menu Capture (Phase 5)",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = SaffronPrimary,
                        modifier = Modifier.semantics { heading() }
                    )

                    IconButton(
                        onClick = {
                            onStopAnnouncement()
                            onClose()
                        },
                        modifier = Modifier.size(44.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "Close Camera", tint = PureWhite)
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = statusText,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = HighContrastYellow,
                    textAlign = TextAlign.Start
                )
            }

            // Bottom Shutter Controls Overlay
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .background(Color.Black.copy(alpha = 0.90f))
                    .padding(horizontal = 20.dp, vertical = 16.dp)
                    .systemBarsPadding(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                errorMessage?.let { err ->
                    Text(
                        text = err,
                        fontSize = 13.sp,
                        color = NonVegRed,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // Manual Capture Button (72dp circular accessible tap target)
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
                                contentDescription = "Capture Menu Photo"
                            }
                    ) {
                        if (isCapturing || isProcessingVision) {
                            CircularProgressIndicator(color = SaffronPrimary, modifier = Modifier.size(32.dp))
                        } else {
                            Icon(
                                imageVector = Icons.Default.CameraAlt,
                                contentDescription = null,
                                tint = SaffronOnPrimary,
                                modifier = Modifier.size(36.dp)
                            )
                        }
                    }

                    // Gallery Upload Button
                    AccessibleButton(
                        text = "📁 Upload Photo",
                        onClick = {
                            galleryLauncher.launch("image/*")
                        },
                        modifier = Modifier.weight(1f),
                        isPrimary = false
                    )

                    // Load Sample Restaurant Menu Button (for instant testing)
                    AccessibleButton(
                        text = "⚡ Sample Menu",
                        onClick = {
                            processCapturedBytes(ByteArray(0))
                        },
                        modifier = Modifier.weight(1f),
                        isPrimary = false
                    )
                }
            }
        }
    }
}
