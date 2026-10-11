package com.sarvaai.app.ui.components

import androidx.compose.animation.core.*
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sarvaai.app.ui.theme.*

/**
 * Modifier extension that paints hardware-accelerated dual-bevel Neomorphic shadows & surfaces.
 */
fun Modifier.neomorphicSurface(
    shape: Shape = RoundedCornerShape(16.dp),
    lightColor: Color = NeoLightShadow,
    darkColor: Color = NeoDarkShadow,
    surfaceColor: Color = NeoSurface,
    borderColor: Color = NeoBorder,
    borderWidth: Dp = 1.dp,
    elevation: Dp = 5.dp,
    isPressed: Boolean = false,
    cornerRadius: Dp = 16.dp
): Modifier = this
    .drawBehind {
        val cornerRadiusPx = cornerRadius.toPx()
        val elevPx = elevation.toPx()

        if (isPressed) {
            // Inset / Pressed concave state
            drawRoundRect(
                color = NeoSurfacePressed,
                topLeft = Offset.Zero,
                size = size,
                cornerRadius = CornerRadius(cornerRadiusPx, cornerRadiusPx)
            )
            // Inner shade along top-left
            drawRoundRect(
                color = darkColor.copy(alpha = 0.30f),
                topLeft = Offset(0f, 0f),
                size = size,
                cornerRadius = CornerRadius(cornerRadiusPx, cornerRadiusPx),
                style = Stroke(width = elevPx.coerceAtLeast(2f))
            )
        } else {
            // Top-Left Specular Light Reflection
            drawRoundRect(
                color = lightColor.copy(alpha = 0.95f),
                topLeft = Offset(-elevPx * 0.7f, -elevPx * 0.7f),
                size = size,
                cornerRadius = CornerRadius(cornerRadiusPx, cornerRadiusPx)
            )
            // Bottom-Right Soft Diffuse Shadow
            drawRoundRect(
                color = darkColor.copy(alpha = 0.60f),
                topLeft = Offset(elevPx * 0.7f, elevPx * 0.7f),
                size = size,
                cornerRadius = CornerRadius(cornerRadiusPx, cornerRadiusPx)
            )
            // Foreground Convex Surface
            drawRoundRect(
                color = surfaceColor,
                topLeft = Offset.Zero,
                size = size,
                cornerRadius = CornerRadius(cornerRadiusPx, cornerRadiusPx)
            )
        }
    }
    .border(
        width = borderWidth,
        color = if (isPressed) borderColor.copy(alpha = 0.5f) else borderColor,
        shape = shape
    )
    .clip(shape)

/**
 * Dual-bevel Neomorphic Card surface container.
 */
@Composable
fun NeomorphicCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    shape: Shape = RoundedCornerShape(16.dp),
    cornerRadius: Dp = 16.dp,
    containerColor: Color = NeoSurface,
    borderColor: Color = NeoBorder,
    elevation: Dp = 5.dp,
    contentDescription: String? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed && onClick != null) 0.985f else 1.0f,
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
        label = "card_scale"
    )

    val cardModifier = if (onClick != null) {
        modifier
            .scale(scale)
            .neomorphicSurface(
                shape = shape,
                surfaceColor = containerColor,
                borderColor = borderColor,
                elevation = elevation,
                isPressed = isPressed,
                cornerRadius = cornerRadius
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .semantics(mergeDescendants = true) {
                if (contentDescription != null) {
                    this.contentDescription = contentDescription
                }
                role = Role.Button
            }
    } else {
        modifier
            .neomorphicSurface(
                shape = shape,
                surfaceColor = containerColor,
                borderColor = borderColor,
                elevation = elevation,
                isPressed = false,
                cornerRadius = cornerRadius
            )
            .semantics(mergeDescendants = true) {
                if (contentDescription != null) {
                    this.contentDescription = contentDescription
                }
            }
    }

    Column(
        modifier = cardModifier.padding(16.dp),
        content = content
    )
}

/**
 * Tactile Neomorphic Button with press-depth spring animation.
 */
@Composable
fun NeomorphicButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isPrimary: Boolean = true,
    enabled: Boolean = true,
    leadingIcon: (@Composable () -> Unit)? = null,
    contentDescription: String? = null
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed && enabled) 0.96f else 1.0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "btn_scale"
    )

    val shape = RoundedCornerShape(14.dp)
    val buttonSurfaceColor = when {
        !enabled -> NeoSurfacePressed
        isPrimary -> if (isPressed) Color(0xFF1D4ED8) else NeoPrimary
        else -> if (isPressed) NeoSurfacePressed else NeoSurface
    }
    val contentColor = when {
        !enabled -> NeoTextMuted
        isPrimary -> NeoOnPrimary
        else -> NeoTextPrimary
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 54.dp)
            .scale(scale)
            .neomorphicSurface(
                shape = shape,
                surfaceColor = buttonSurfaceColor,
                lightColor = if (isPrimary) Color(0x6693C5FD) else NeoLightShadow,
                darkColor = if (isPrimary) Color(0x661E3A8A) else NeoDarkShadow,
                borderColor = if (isPrimary) Color(0xFF3B82F6) else NeoBorder,
                elevation = if (isPrimary) 6.dp else 4.dp,
                isPressed = isPressed && enabled,
                cornerRadius = 14.dp
            )
            .clickable(
                enabled = enabled,
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .semantics(mergeDescendants = true) {
                this.contentDescription = contentDescription ?: text
                role = Role.Button
            }
    ) {
        Row(
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (leadingIcon != null) {
                CompositionLocalProvider(LocalContentColor provides contentColor) {
                    leadingIcon()
                }
                Spacer(modifier = Modifier.width(8.dp))
            }
            Text(
                text = text,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = contentColor,
                textAlign = TextAlign.Center
            )
        }
    }
}

/**
 * Tactile Neomorphic Icon Button with vector Material Icons.
 */
@Composable
fun NeomorphicIconButton(
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    isSelected: Boolean = false,
    enabled: Boolean = true,
    tint: Color? = null,
    size: Dp = 48.dp
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed && enabled) 0.92f else 1.0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "icon_btn_scale"
    )

    val shape = CircleShape
    val iconTint = tint ?: when {
        !enabled -> NeoTextMuted
        isSelected -> NeoOnPrimary
        else -> NeoTextPrimary
    }
    val surfaceColor = when {
        !enabled -> NeoSurfacePressed
        isSelected -> NeoPrimary
        isPressed -> NeoSurfacePressed
        else -> NeoSurface
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(size)
            .scale(scale)
            .neomorphicSurface(
                shape = shape,
                surfaceColor = surfaceColor,
                lightColor = if (isSelected) Color(0x6693C5FD) else NeoLightShadow,
                darkColor = if (isSelected) Color(0x661E3A8A) else NeoDarkShadow,
                borderColor = if (isSelected) Color(0xFF3B82F6) else NeoBorder,
                elevation = 4.dp,
                isPressed = (isPressed || isSelected) && enabled,
                cornerRadius = size / 2
            )
            .clickable(
                enabled = enabled,
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .semantics {
                if (contentDescription != null) {
                    this.contentDescription = contentDescription
                }
                role = Role.Button
            }
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = iconTint,
            modifier = Modifier.size(size * 0.52f)
        )
    }
}

/**
 * Large Pulsing Neomorphic Microphone Action Button with audio wave and ripple effects.
 */
@Composable
fun NeomorphicMicButton(
    isRecording: Boolean,
    isProcessing: Boolean,
    onClick: () -> Unit,
    amplitude: Float = 0f,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    // Smooth responsive interpolation for microphone audio amplitude
    val smoothedAmplitude by animateFloatAsState(
        targetValue = if (isRecording) amplitude.coerceIn(0f, 1f) else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "mic_amplitude"
    )

    // Pulsing circular wave animation for active recording / processing
    val infiniteTransition = rememberInfiniteTransition(label = "mic_waves")

    // Concentric Ring 1: Inner Wave Pulse
    val pulseScale1 by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = 1.45f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1400, easing = LinearOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "pulse_scale_1"
    )
    val pulseAlpha1 by infiniteTransition.animateFloat(
        initialValue = 0.65f,
        targetValue = 0.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1400, easing = LinearOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "pulse_alpha_1"
    )

    // Concentric Ring 2: Middle Wave Pulse (Staggered phase delay)
    val pulseScale2 by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = 1.80f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1800, delayMillis = 350, easing = LinearOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "pulse_scale_2"
    )
    val pulseAlpha2 by infiniteTransition.animateFloat(
        initialValue = 0.50f,
        targetValue = 0.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1800, delayMillis = 350, easing = LinearOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "pulse_alpha_2"
    )

    // Concentric Ring 3: Outer Wave Pulse (Further staggered phase delay)
    val pulseScale3 by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = 2.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2200, delayMillis = 700, easing = LinearOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "pulse_scale_3"
    )
    val pulseAlpha3 by infiniteTransition.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2200, delayMillis = 700, easing = LinearOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "pulse_alpha_3"
    )

    // Smooth glowing spin rotation for processing state
    val spinRotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "spin_rotation"
    )

    val waveColor = when {
        isRecording -> VegGreen
        isProcessing -> NeoPrimary
        else -> NeoPrimary
    }

    val dynamicScale by animateFloatAsState(
        targetValue = when {
            isPressed -> 0.94f
            isRecording -> 1.0f + (smoothedAmplitude * 0.15f)
            else -> 1.0f
        },
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "mic_dyn_scale"
    )

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier.size(140.dp)
    ) {
        // 3 concentric animated ripple rings with staggered phase delays and alpha gradients
        if (isRecording) {
            // Ring 3 (Outermost Sonic Ripple)
            Box(
                modifier = Modifier
                    .size(96.dp)
                    .scale(pulseScale3 + (smoothedAmplitude * 0.45f))
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                waveColor.copy(alpha = (pulseAlpha3 * 0.35f).coerceIn(0f, 1f)),
                                waveColor.copy(alpha = (pulseAlpha3 * 0.15f).coerceIn(0f, 1f)),
                                Color.Transparent
                            )
                        )
                    )
                    .border(
                        width = 1.5.dp,
                        color = waveColor.copy(alpha = (pulseAlpha3 * 0.75f).coerceIn(0f, 1f)),
                        shape = CircleShape
                    )
            )

            // Ring 2 (Middle Sonic Ripple)
            Box(
                modifier = Modifier
                    .size(96.dp)
                    .scale(pulseScale2 + (smoothedAmplitude * 0.35f))
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                waveColor.copy(alpha = (pulseAlpha2 * 0.45f).coerceIn(0f, 1f)),
                                waveColor.copy(alpha = (pulseAlpha2 * 0.20f).coerceIn(0f, 1f)),
                                Color.Transparent
                            )
                        )
                    )
                    .border(
                        width = 2.dp,
                        color = waveColor.copy(alpha = (pulseAlpha2 * 0.85f).coerceIn(0f, 1f)),
                        shape = CircleShape
                    )
            )

            // Ring 1 (Innermost Sonic Ripple)
            Box(
                modifier = Modifier
                    .size(96.dp)
                    .scale(pulseScale1 + (smoothedAmplitude * 0.25f))
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                waveColor.copy(alpha = (pulseAlpha1 * 0.55f).coerceIn(0f, 1f)),
                                waveColor.copy(alpha = (pulseAlpha1 * 0.25f).coerceIn(0f, 1f)),
                                Color.Transparent
                            )
                        )
                    )
                    .border(
                        width = 2.dp,
                        color = waveColor.copy(alpha = pulseAlpha1.coerceIn(0f, 1f)),
                        shape = CircleShape
                    )
            )
        } else if (isProcessing) {
            // Smooth glowing spin ring for processing state
            Box(
                modifier = Modifier
                    .size(108.dp)
                    .rotate(spinRotation)
                    .drawBehind {
                        drawCircle(
                            brush = Brush.sweepGradient(
                                colors = listOf(
                                    NeoPrimary.copy(alpha = 0.0f),
                                    NeoPrimary.copy(alpha = 0.35f),
                                    NeoPrimary,
                                    Color.White,
                                    NeoPrimary,
                                    NeoPrimary.copy(alpha = 0.0f)
                                )
                            ),
                            style = Stroke(width = 3.dp.toPx())
                        )
                    }
            )
            Box(
                modifier = Modifier
                    .size(96.dp)
                    .scale(pulseScale1)
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                NeoPrimary.copy(alpha = (pulseAlpha1 * 0.35f).coerceIn(0f, 1f)),
                                Color.Transparent
                            )
                        )
                    )
            )
        }

        // Main Core Neomorphic Circle Button
        val coreSurfaceColor = when {
            isRecording -> VegGreen
            isProcessing -> NeoPrimary
            else -> NeoSurface
        }
        val iconColor = when {
            isRecording || isProcessing -> NeoOnPrimary
            else -> NeoPrimary
        }

        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(96.dp)
                .scale(dynamicScale)
                .neomorphicSurface(
                    shape = CircleShape,
                    surfaceColor = coreSurfaceColor,
                    lightColor = if (isRecording || isProcessing) Color(0x66FFFFFF) else NeoLightShadow,
                    darkColor = if (isRecording) Color(0x4D000000) else if (isProcessing) Color(0x4D1E3A8A) else NeoDarkShadow,
                    borderColor = if (isRecording) VegGreen else if (isProcessing) NeoPrimary else NeoBorder,
                    elevation = 7.dp,
                    isPressed = isPressed,
                    cornerRadius = 48.dp
                )
                .clickable(
                    enabled = !isProcessing,
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = onClick
                )
                .semantics {
                    contentDescription = when {
                        isProcessing -> "Processing speech request"
                        isRecording -> "Stop voice recording"
                        else -> "Start voice dialogue"
                    }
                    role = Role.Button
                }
        ) {
            when {
                isProcessing -> {
                    Icon(
                        imageVector = Icons.Filled.Autorenew,
                        contentDescription = null,
                        tint = iconColor,
                        modifier = Modifier
                            .size(42.dp)
                            .rotate(spinRotation)
                    )
                }
                isRecording -> {
                    Icon(
                        imageVector = Icons.Filled.Stop,
                        contentDescription = null,
                        tint = iconColor,
                        modifier = Modifier.size(42.dp)
                    )
                }
                else -> {
                    Icon(
                        imageVector = Icons.Filled.Mic,
                        contentDescription = null,
                        tint = iconColor,
                        modifier = Modifier.size(42.dp)
                    )
                }
            }
        }
    }
}

/**
 * Neomorphic Pill / Chip filter toggle.
 */
@Composable
fun NeomorphicPill(
    text: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    selectedColor: Color = NeoPrimary,
    selectedContentColor: Color = NeoOnPrimary
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.95f else 1.0f,
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
        label = "pill_scale"
    )

    val shape = RoundedCornerShape(20.dp)
    val containerColor = if (isSelected) selectedColor else NeoSurface
    val contentColor = if (isSelected) selectedContentColor else NeoTextSecondary

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .scale(scale)
            .neomorphicSurface(
                shape = shape,
                surfaceColor = containerColor,
                lightColor = if (isSelected) Color(0x4D93C5FD) else NeoLightShadow,
                darkColor = if (isSelected) Color(0x4D1E3A8A) else NeoDarkShadow,
                borderColor = if (isSelected) selectedColor else NeoBorder,
                elevation = if (isSelected) 2.dp else 4.dp,
                isPressed = isSelected || isPressed,
                cornerRadius = 20.dp
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 14.dp, vertical = 8.dp)
            .semantics {
                role = Role.Tab
            }
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = contentColor,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
            }
            Text(
                text = text,
                fontSize = 13.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                color = contentColor
            )
        }
    }
}

/**
 * Top-bar Segmented Neomorphic Language Switcher for Indic Multilingual Speech (EN, हिन्दी, తెలుగు).
 * Zero emojis, vector icons only.
 */
@Composable
fun NeomorphicLanguageSelector(
    selectedLanguage: String,
    onLanguageSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val languages = listOf(
        Triple("en", "English", "EN"),
        Triple("hi", "हिन्दी", "HI"),
        Triple("te", "తెలుగు", "TE")
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .neomorphicSurface(
                shape = RoundedCornerShape(24.dp),
                surfaceColor = NeoSurfacePressed,
                borderColor = NeoBorder,
                elevation = 2.dp,
                isPressed = true,
                cornerRadius = 24.dp
            )
            .padding(6.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        languages.forEach { (code, nativeName, shortCode) ->
            val isSelected = selectedLanguage.equals(code, ignoreCase = true)
            val interactionSource = remember { MutableInteractionSource() }
            val isPressed by interactionSource.collectIsPressedAsState()
            val scale by animateFloatAsState(
                targetValue = if (isPressed) 0.96f else 1.0f,
                animationSpec = spring(stiffness = Spring.StiffnessMedium),
                label = "lang_scale"
            )

            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .weight(1f)
                    .height(42.dp)
                    .scale(scale)
                    .neomorphicSurface(
                        shape = RoundedCornerShape(20.dp),
                        surfaceColor = if (isSelected) NeoPrimary else NeoSurface,
                        lightColor = if (isSelected) Color(0x6693C5FD) else NeoLightShadow,
                        darkColor = if (isSelected) Color(0x661E3A8A) else NeoDarkShadow,
                        borderColor = if (isSelected) NeoPrimary else NeoBorder,
                        elevation = if (isSelected) 3.dp else 2.dp,
                        isPressed = !isSelected && isPressed,
                        cornerRadius = 20.dp
                    )
                    .clickable(
                        interactionSource = interactionSource,
                        indication = null,
                        onClick = { onLanguageSelected(code) }
                    )
                    .padding(horizontal = 8.dp)
                    .semantics {
                        contentDescription = "$nativeName language option ($shortCode)"
                        role = Role.Tab
                    }
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    if (isSelected) {
                        Icon(
                            imageVector = Icons.Filled.Check,
                            contentDescription = null,
                            tint = NeoOnPrimary,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                    }
                    Text(
                        text = "$nativeName ($shortCode)",
                        fontSize = 13.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        color = if (isSelected) NeoOnPrimary else NeoTextSecondary,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}
