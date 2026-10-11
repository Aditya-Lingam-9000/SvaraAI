package com.sarvaai.app.ui.screens.camera

import android.Manifest
import android.net.Uri
import android.util.Log
import android.view.ViewGroup
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
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
import com.sarvaai.app.ui.components.NeomorphicCard
import com.sarvaai.app.ui.components.neomorphicSurface
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

    // Safe Back handling
    BackHandler {
        onStopAnnouncement()
        onClose()
    }

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

            val res = menuExtractor.extractAndPersistMenu(bytes, selectedLanguage)
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
        color = NeoBackground
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 1. Clean Flush Top Bar
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp)
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
                            imageVector = Icons.Default.DocumentScanner,
                            contentDescription = null,
                            tint = NeoPrimary,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Menu Vision Capture",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = NeoTextPrimary,
                            modifier = Modifier.semantics { heading() }
                        )
                        Text(
                            text = if (isProcessingVision) "Extracting menu with AI Vision..." else statusText,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = if (isProcessingVision) NeoPrimary else NeoTextSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                IconButton(
                    onClick = {
                        onStopAnnouncement()
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
                        contentDescription = "Close Camera",
                        tint = NeoTextPrimary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            // 2. Dedicated Central Viewfinder (Vertical Format Portrait Viewport, Zero Camera Bleed)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.90f)
                        .aspectRatio(3f / 4f)
                        .neomorphicSurface(
                            shape = RoundedCornerShape(24.dp),
                            surfaceColor = Color(0xFF0F172A),
                            lightColor = NeoLightShadow,
                            darkColor = NeoDarkShadow,
                            borderColor = NeoPrimary,
                            borderWidth = 2.5.dp,
                            elevation = 6.dp,
                            cornerRadius = 24.dp
                        )
                        .clip(RoundedCornerShape(24.dp))
                        .border(2.5.dp, NeoPrimary, RoundedCornerShape(24.dp)),
                    contentAlignment = Alignment.Center
                ) {
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
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(RoundedCornerShape(24.dp))
                        )

                        // Subtle inner framing guideline
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(16.dp)
                                .border(
                                    width = 1.dp,
                                    color = NeoPrimary.copy(alpha = 0.35f),
                                    shape = RoundedCornerShape(16.dp)
                                )
                        )
                    } else {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                            modifier = Modifier.padding(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.CameraAlt,
                                contentDescription = null,
                                tint = NeoTextMuted,
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "Camera permission is required to capture menus",
                                color = Color.White.copy(alpha = 0.85f),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }

            // 3. Bottom Controls Area (Neomorphic Card with centered shutter and 2 equal-width action buttons)
            NeomorphicCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                shape = RoundedCornerShape(20.dp),
                cornerRadius = 20.dp,
                elevation = 5.dp
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    errorMessage?.let { err ->
                        Text(
                            text = err,
                            fontSize = 12.sp,
                            color = NonVegRed,
                            fontWeight = FontWeight.SemiBold,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                    }

                    // Center: Large 68dp Neomorphic Capture Button
                    NeomorphicShutterButton(
                        onClick = { capturePhoto() },
                        isProcessing = isCapturing || isProcessingVision,
                        enabled = hasCameraPermission && !isProcessingVision && !isCapturing
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Row below shutter: 2 equal-width 48dp action buttons
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        // Button 1: "Upload Photo"
                        NeomorphicMenuActionButton(
                            text = "Upload Photo",
                            icon = Icons.Default.UploadFile,
                            onClick = { galleryLauncher.launch("image/*") },
                            enabled = !isProcessingVision && !isCapturing,
                            modifier = Modifier.weight(1f)
                        )

                        // Button 2: "Sample Menu"
                        NeomorphicMenuActionButton(
                            text = "Sample Menu",
                            icon = Icons.Default.RestaurantMenu,
                            onClick = { processCapturedBytes(ByteArray(0)) },
                            enabled = !isProcessingVision && !isCapturing,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun NeomorphicShutterButton(
    onClick: () -> Unit,
    isProcessing: Boolean,
    enabled: Boolean,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed && enabled) 0.93f else 1.0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "shutter_scale"
    )

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(68.dp)
            .scale(scale)
            .neomorphicSurface(
                shape = CircleShape,
                surfaceColor = if (isProcessing) NeoSurfacePressed else NeoPrimary,
                lightColor = Color(0x6693C5FD),
                darkColor = Color(0x661E3A8A),
                borderColor = Color(0xFF3B82F6),
                borderWidth = 2.5.dp,
                elevation = 6.dp,
                isPressed = isPressed && enabled,
                cornerRadius = 34.dp
            )
            .clickable(
                enabled = enabled && !isProcessing,
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .semantics {
                contentDescription = "Capture Menu Photo"
                role = Role.Button
            }
    ) {
        if (isProcessing) {
            CircularProgressIndicator(
                color = NeoOnPrimary,
                modifier = Modifier.size(28.dp),
                strokeWidth = 3.dp
            )
        } else {
            Icon(
                imageVector = Icons.Default.CameraAlt,
                contentDescription = null,
                tint = NeoOnPrimary,
                modifier = Modifier.size(32.dp)
            )
        }
    }
}

@Composable
private fun NeomorphicMenuActionButton(
    text: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed && enabled) 0.96f else 1.0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "action_btn_scale"
    )

    val shape = RoundedCornerShape(14.dp)
    val buttonSurfaceColor = if (isPressed && enabled) NeoSurfacePressed else NeoSurface
    val contentColor = if (enabled) NeoTextPrimary else NeoTextMuted

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .height(48.dp)
            .scale(scale)
            .neomorphicSurface(
                shape = shape,
                surfaceColor = buttonSurfaceColor,
                lightColor = NeoLightShadow,
                darkColor = NeoDarkShadow,
                borderColor = NeoBorder,
                borderWidth = 1.dp,
                elevation = 3.dp,
                isPressed = isPressed && enabled,
                cornerRadius = 14.dp
            )
            .clickable(
                enabled = enabled,
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 8.dp)
            .semantics(mergeDescendants = true) {
                this.contentDescription = text
                role = Role.Button
            }
    ) {
        Row(
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (enabled) NeoPrimary else NeoTextMuted,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = text,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = contentColor,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )
        }
    }
}
