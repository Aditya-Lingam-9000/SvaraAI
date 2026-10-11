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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
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

    val waveColor = VegGreen

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
        }

        // Main Core Neomorphic Circle Button
        val coreSurfaceColor = when {
            isRecording -> VegGreen
            else -> NeoSurface
        }
        val iconColor = when {
            isRecording -> NeoOnPrimary
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
                    lightColor = if (isRecording) Color(0x66FFFFFF) else NeoLightShadow,
                    darkColor = if (isRecording) Color(0x4D000000) else NeoDarkShadow,
                    borderColor = if (isRecording) VegGreen else NeoBorder,
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
 * Modern Neomorphic Dropdown Selector for Indic Multilingual Speech.
 * Supports 6 languages:
 * 1. Telugu (te, "తెలుగు") - Default
 * 2. Hindi (hi, "हिन्दी")
 * 3. Tamil (ta, "தமிழ்")
 * 4. Kannada (kn, "ಕನ್ನಡ")
 * 5. Malayalam (ml, "മലയാളം")
 * 6. English (en, "English")
 *
 * Displays a compact neomorphic pill/button (height 38-40dp) with currently selected language native name + ArrowDropDown.
 * When clicked, opens a neomorphic-styled dropdown menu displaying all 6 languages with native names and checkmark.
 */
@Composable
fun NeomorphicLanguageSelector(
    selectedLanguage: String,
    onLanguageSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val languages = listOf(
        Pair("te", "తెలుగు"),
        Pair("hi", "हिन्दी"),
        Pair("ta", "தமிழ்"),
        Pair("kn", "ಕನ್ನಡ"),
        Pair("ml", "മലയാളം"),
        Pair("en", "English")
    )

    var expanded by remember { mutableStateOf(false) }
    val currentLang = languages.find { it.first.equals(selectedLanguage, ignoreCase = true) }
        ?: languages.first() // Telugu is default

    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed || expanded) 0.96f else 1.0f,
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
        label = "lang_dropdown_scale"
    )

    Box(
        modifier = modifier.wrapContentSize(Alignment.TopStart)
    ) {
        // Compact Neomorphic Button / Pill (Height: 38dp)
        Row(
            modifier = Modifier
                .height(38.dp)
                .scale(scale)
                .neomorphicSurface(
                    shape = RoundedCornerShape(19.dp),
                    surfaceColor = if (expanded) NeoSurfacePressed else NeoSurface,
                    lightColor = if (expanded) Color(0x6693C5FD) else NeoLightShadow,
                    darkColor = if (expanded) Color(0x661E3A8A) else NeoDarkShadow,
                    borderColor = if (expanded) NeoPrimary else NeoBorder,
                    elevation = if (expanded) 2.dp else 4.dp,
                    isPressed = expanded || isPressed,
                    cornerRadius = 19.dp
                )
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = { expanded = !expanded }
                )
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Translate,
                contentDescription = null,
                tint = NeoPrimary,
                modifier = Modifier.size(16.dp)
            )
            Text(
                text = currentLang.second,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = NeoTextPrimary,
                maxLines = 1,
                softWrap = false
            )
            Icon(
                imageVector = Icons.Default.ArrowDropDown,
                contentDescription = if (expanded) "Close language options" else "Select language",
                tint = NeoTextSecondary,
                modifier = Modifier
                    .size(20.dp)
                    .rotate(if (expanded) 180f else 0f)
            )
        }

        // Dropdown Menu displaying all 6 languages with native names & checkmark
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier
                .widthIn(min = 180.dp)
                .background(NeoSurface)
                .border(1.dp, NeoBorder, RoundedCornerShape(16.dp))
                .clip(RoundedCornerShape(16.dp))
        ) {
            languages.forEach { (code, nativeName) ->
                val isSelected = selectedLanguage.equals(code, ignoreCase = true)
                DropdownMenuItem(
                    text = {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = nativeName,
                                fontSize = 14.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSelected) NeoPrimary else NeoTextPrimary
                            )
                            if (isSelected) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = "Selected",
                                    tint = NeoPrimary,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    },
                    onClick = {
                        expanded = false
                        onLanguageSelected(code)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(if (isSelected) NeoPrimaryContainer.copy(alpha = 0.35f) else Color.Transparent)
                )
            }
        }
    }
}

/**
 * Step-by-Step Senior Help Concierge Dialog.
 *
 * Implements the unhurried, step-by-step assistance flow designed specifically for elderly diners:
 * - One question at a time (Language -> Menu Explorer -> Dietary Customization -> Order & Waiter Help)
 * - Extra-large tactile buttons and high contrast typography (20sp+ headings, 16sp+ options)
 * - Persistent Repeat Aloud button with unhurried 0.8x speech narration
 * - No timeouts that punish slowness
 */
@Composable
fun StepByStepAgeingConcierge(
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    selectedLanguage: String = "te",
    onSelectLanguage: ((String) -> Unit)? = null,
    onSpeak: ((String) -> Unit)? = null,
    onSpeakNarration: ((String) -> Unit)? = null,
    onStopAudio: (() -> Unit)? = null,
    onStopNarration: (() -> Unit)? = null,
    onNavigateToMenu: (() -> Unit)? = null,
    onNavigateToCart: (() -> Unit)? = null,
    onNavigateToWaiter: (() -> Unit)? = null,
    cartDao: Any? = null,
    hapticEngine: Any? = null,
    earconEngine: Any? = null
) {
    val effectiveSpeak = onSpeakNarration ?: onSpeak
    val effectiveStop = onStopNarration ?: onStopAudio
    var currentStep by remember { mutableIntStateOf(1) }
    val totalSteps = 4
    val scrollState = rememberScrollState()

    val stepNarration = when (currentStep) {
        1 -> when (selectedLanguage.lowercase()) {
            "te" -> "మొదటి అడుగు: దయచేసి మీరు మాట్లాడాలనుకుంటున్న భాషను ఎంచుకోండి. తెలుగు, హిందీ, తమిళం, కన్నడ, మలయాళం లేదా ఇంగ్లీష్."
            "hi" -> "पहला चरण: कृपया अपनी पसंदीदा भाषा चुनें - तेलुगु, हिन्दी, तमिल, कन्नड़, मलयालम, अथवा अंग्रेज़ी।"
            "ta" -> "முதல் படி: தயவுசெய்து நீங்கள் பேச விரும்பும் மொழியைத் தேர்ந்தெடுக்கவும். தமிழ், தெலுங்கு, இந்தி, கன்னடம், மலையாளம் அல்லது ஆங்கிலம்."
            "kn" -> "ಮೊದಲ ಹಂತ: ದಯವಿಟ್ಟು ನೀವು ಮಾತನಾಡಲು ಬಯಸುವ ಭಾಷೆಯನ್ನು ಆಯ್ಕೆಮಾಡಿ. ಕನ್ನಡ, ತೆಲುಗು, ಹಿಂದಿ, ತಮಿಳು, ಮಲಯಾಳಂ ಅಥವಾ ಇಂಗ್ಲಿಷ್."
            "ml" -> "ആദ്യ പടി: ദയവായി നിങ്ങൾ സംസാരിക്കാൻ ആഗ്രഹിക്കുന്ന ഭാഷ തിരഞ്ഞെടുക്കുക. മലയാളം, തെലുങ്ക്, ഹിന്ദി, തമിഴ്, കന്നഡ അല്ലെങ്കിൽ ഇംഗ്ലീഷ്."
            else -> "Step 1: First, which language would you prefer to speak? Telugu, Hindi, Tamil, Kannada, Malayalam, or English."
        }
        2 -> when (selectedLanguage.lowercase()) {
            "te" -> "రెండవ అడుగు: మీరు మెనూని కెమెరాతో చూడాలనుకుంటున్నారా, లేదా ఆహార ప్రశ్నలు అడగాలనుకుంటున్నారా?"
            "hi" -> "दूसरा चरण: क्या आप मुद्रित मेनू को कैमरे से स्कैन करना चाहते हैं, या किसी व्यंजन के बारे में पूछना चाहते हैं?"
            "ta" -> "இரண்டாம் படி: நீங்கள் மெனுவை கேமரா மூலம் ஸ்கேன் செய்ய விரும்புகிறீர்களா, அல்லது உணவைப் பற்றி கேட்க விரும்புகிறீர்களா?"
            "kn" -> "ಎರಡನೇ ಹಂತ: ನೀವು ಮೆನುವನ್ನು ಕ್ಯಾಮೆರಾದಿಂದ ಸ್ಕ್ಯಾನ್ ಮಾಡಲು ಬಯಸುತ್ತೀರಾ, ಅಥವಾ ಆಹಾರದ ಬಗ್ಗೆ ಕೇಳಲು ಬಯಸುತ್ತೀರಾ?"
            "ml" -> "രണ്ടാം പടി: നിങ്ങൾ ക്യാമറ ഉപയോഗിച്ച് മെനു സ്കാൻ ചെയ്യാൻ ആഗ്രഹിക്കുന്നുണ്ടോ, അതോ ഭക്ഷണത്തെക്കുറിച്ച് ചോദിക്കാൻ ആഗ്രഹിക്കുന്നുണ്ടോ?"
            else -> "Step 2: Would you like to scan the menu with your camera, or ask any question about the food?"
        }
        3 -> when (selectedLanguage.lowercase()) {
            "te" -> "మూడవ అడుగు: మీకు ఏదైనా ఆహార నియమాలు ఉన్నాయా? స్వచ్ఛమైన శాకాహారం, జైన భోజనం, లేదా తక్కువ కారం?"
            "hi" -> "तीसरा चरण: क्या आपकी कोई खान-पान प्राथमिकता है? शुद्ध शाकाहारी, जैन भोजन, या हल्का सुपाच्य कम तीखा भोजन?"
            "ta" -> "மூன்றாம் படி: உங்களுக்கு ஏதேனும் உணவு கட்டுப்பாடுகள் உள்ளதா? தூய சைவ உணவு, சமண உணவு, அல்லது குறைந்த காரம்?"
            "kn" -> "ಮೂರನೇ ಹಂತ: ನಿಮಗೆ ಯಾವುದೇ ಆಹಾರದ ನಿರ್ಬಂಧಗಳಿವೆಯೇ? ಶುದ್ಧ ಸಸ್ಯಾಹಾರಿ, ಜೈನ ಸ್ನೇಹಿ, ಅಥವಾ ಕಡಿಮೆ ಖಾರ?"
            "ml" -> "മൂന്നാം പടി: നിങ്ങൾക്ക് എന്തെങ്കിലും ഭക്ഷണ നിയന്ത്രണങ്ങൾ ഉണ്ടോ? ശുദ്ധ സസ്യാഹാരം, ജൈന ഭക്ഷണം, അല്ലെങ്കിൽ കുറഞ്ഞ എരിവ്?"
            else -> "Step 3: Do you have any dietary restrictions? Pure vegetarian, Jain friendly, or mild and non-spicy?"
        }
        else -> when (selectedLanguage.lowercase()) {
            "te" -> "నాల్గవ అడుగు: మీకు సహాయం చేయడానికి వెయిటర్‌ని పిలవమంటారా, లేదా మీ ఆర్డర్ తనిఖీ చేయమంటారా?"
            "hi" -> "अंतिम चरण: क्या आपकी मेज़ पर वेटर की सहायता चाहिए, या थाली और बिल की समीक्षा करना चाहते हैं?"
            "ta" -> "நான்காம் படி: உங்களுக்கு உதவ பணியாளரை அழைக்க வேண்டுமா, அல்லது உங்கள் ஆர்டரை சரிபார்க்க வேண்டுமா?"
            "kn" -> "ನಾಲ್ಕನೇ ಹಂತ: ನಿಮಗೆ ಸಹಾಯ ಮಾಡಲು ಮಾಣಿ ಕರೆಯಬೇಕೇ, ಅಥವಾ ನಿಮ್ಮ ಆದೇಶವನ್ನು ಪರಿಶೀಲಿಸಬೇಕೇ?"
            "ml" -> "നാലാം പടി: നിങ്ങളെ സഹായിക്കാൻ വെയിറ്ററെ വിളിക്കണോ, അതോ നിങ്ങളുടെ ഓർഡർ പരിശോധിക്കണോ?"
            else -> "Step 4: Would you like to call a waiter to your table, or review your current order and bill?"
        }
    }

    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0x99000000))
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                modifier = modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.92f)
                    .clip(RoundedCornerShape(24.dp)),
                color = NeoBackground,
                shape = RoundedCornerShape(24.dp),
                shadowElevation = 12.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(20.dp)
                ) {
                    // Header Row: Concierge Badge + Title + Close Button
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .size(46.dp)
                                    .neomorphicSurface(
                                        shape = CircleShape,
                                        surfaceColor = NeoPrimaryContainer,
                                        borderColor = NeoPrimary,
                                        elevation = 3.dp,
                                        cornerRadius = 23.dp
                                    )
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Elderly,
                                    contentDescription = null,
                                    tint = NeoPrimary,
                                    modifier = Modifier.size(28.dp)
                                )
                            }

                            Column {
                                Text(
                                    text = "Step-by-Step Senior Help",
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = NeoTextPrimary
                                )
                                Text(
                                    text = "वरिष्ठ सहायक • అడుగుల వారీ సహాయం",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = NeoTextSecondary
                                )
                            }
                        }

                        // Close Button
                        NeomorphicIconButton(
                            icon = Icons.Default.Close,
                            onClick = onClose,
                            contentDescription = "Close senior help dialog",
                            size = 40.dp
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Step Indicator Row: Step X of 4
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Step $currentStep of $totalSteps",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = NeoPrimary
                        )

                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            for (step in 1..totalSteps) {
                                Box(
                                    modifier = Modifier
                                        .size(width = if (step == currentStep) 24.dp else 12.dp, height = 8.dp)
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(if (step == currentStep) NeoPrimary else NeoSurfacePressed)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Scrollable Step Content
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .verticalScroll(scrollState)
                    ) {
                        when (currentStep) {
                            1 -> {
                                // STEP 1: LANGUAGE SELECTION
                                Text(
                                    text = "1. Which language do you prefer?",
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = NeoTextPrimary
                                )
                                Text(
                                    text = "अपनी पसंदीदा भाषा चुनें • మీ భాషను ఎంచుకోండి",
                                    fontSize = 14.sp,
                                    color = NeoTextSecondary,
                                    modifier = Modifier.padding(top = 4.dp, bottom = 14.dp)
                                )

                                val langOptions = listOf(
                                    Triple("te", "తెలుగు (Telugu)", "తెలుగులో సులభంగా మాట్లాడండి"),
                                    Triple("hi", "हिन्दी (Hindi)", "हिन्दी में बात करें और सुनें"),
                                    Triple("ta", "தமிழ் (Tamil)", "தமிழில் எளிதாகப் பேசுங்கள்"),
                                    Triple("kn", "ಕನ್ನಡ (Kannada)", "ಕನ್ನಡದಲ್ಲಿ ಸುಲಭವಾಗಿ ಮಾತನಾಡಿ"),
                                    Triple("ml", "മലയാളം (Malayalam)", "മലയാളത്തിൽ സംസാരിക്കുക"),
                                    Triple("en", "English", "Speak and listen in English")
                                )

                                langOptions.forEach { (code, title, desc) ->
                                    val isSelected = selectedLanguage.equals(code, ignoreCase = true)
                                    NeomorphicCard(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 6.dp),
                                        onClick = {
                                            onSelectLanguage?.invoke(code)
                                            effectiveSpeak?.invoke("Language set to $title")
                                        },
                                        containerColor = if (isSelected) NeoPrimaryContainer else NeoSurface,
                                        borderColor = if (isSelected) NeoPrimary else NeoBorder
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Column {
                                                Text(
                                                    text = title,
                                                    fontSize = 17.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = if (isSelected) NeoPrimary else NeoTextPrimary
                                                )
                                                Text(
                                                    text = desc,
                                                    fontSize = 13.sp,
                                                    color = NeoTextSecondary
                                                )
                                            }
                                            if (isSelected) {
                                                Icon(
                                                    imageVector = Icons.Default.CheckCircle,
                                                    contentDescription = "Selected",
                                                    tint = NeoPrimary,
                                                    modifier = Modifier.size(24.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            2 -> {
                                // STEP 2: MENU ASSISTANCE
                                Text(
                                    text = "2. How can we help you explore food?",
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = NeoTextPrimary
                                )
                                Text(
                                    text = "भोजन चुनने में मदद लें • ఆహారం ఎంచుకోవడంలో సహాయం",
                                    fontSize = 14.sp,
                                    color = NeoTextSecondary,
                                    modifier = Modifier.padding(top = 4.dp, bottom = 14.dp)
                                )

                                NeomorphicCard(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 6.dp),
                                    onClick = {
                                        onClose()
                                        onNavigateToMenu?.invoke()
                                    }
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.CameraAlt,
                                            contentDescription = null,
                                            tint = NeoPrimary,
                                            modifier = Modifier.size(28.dp)
                                        )
                                        Column {
                                            Text(
                                                text = "Scan Printed Menu with Camera",
                                                fontSize = 16.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = NeoTextPrimary
                                            )
                                            Text(
                                                text = "Point camera at table menu for spoken reading",
                                                fontSize = 13.sp,
                                                color = NeoTextSecondary
                                            )
                                        }
                                    }
                                }

                                NeomorphicCard(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 6.dp),
                                    onClick = {
                                        onClose()
                                        onNavigateToMenu?.invoke()
                                    }
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.RestaurantMenu,
                                            contentDescription = null,
                                            tint = VegGreen,
                                            modifier = Modifier.size(28.dp)
                                        )
                                        Column {
                                            Text(
                                                text = "Browse Full Menu by Section",
                                                fontSize = 16.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = NeoTextPrimary
                                            )
                                            Text(
                                                text = "Starters, Main Course, Breads, and Desserts",
                                                fontSize = 13.sp,
                                                color = NeoTextSecondary
                                            )
                                        }
                                    }
                                }

                                NeomorphicCard(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 6.dp),
                                    onClick = {
                                        effectiveSpeak?.invoke("You can tap the big microphone button on the home screen anytime and ask: What mild food do you have today?")
                                    }
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Hearing,
                                            contentDescription = null,
                                            tint = NeoPrimary,
                                            modifier = Modifier.size(28.dp)
                                        )
                                        Column {
                                            Text(
                                                text = "Ask Any Food Question Aloud",
                                                fontSize = 16.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = NeoTextPrimary
                                            )
                                            Text(
                                                text = "Tap to hear sample questions to ask by voice",
                                                fontSize = 13.sp,
                                                color = NeoTextSecondary
                                            )
                                        }
                                    }
                                }
                            }

                            3 -> {
                                // STEP 3: DIETARY PREFERENCES
                                Text(
                                    text = "3. Dietary Preferences & Gentle Food",
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = NeoTextPrimary
                                )
                                Text(
                                    text = "परहेज व सुपाच्य भोजन • మీ ఆహార ప్రాధాన్యతలు",
                                    fontSize = 14.sp,
                                    color = NeoTextSecondary,
                                    modifier = Modifier.padding(top = 4.dp, bottom = 14.dp)
                                )

                                val diets = listOf(
                                    Pair("Pure Vegetarian (शाकाहारी)", "100% vegetarian preparations, green badge items"),
                                    Pair("Jain Food (जैन भोजन)", "Prepared strictly without onion, garlic, or root vegetables"),
                                    Pair("Mild & Gentle (कम तीखा / सादा)", "Low oil, non-spicy, soothing dishes suited for digestion"),
                                    Pair("No Restrictions (सामान्य)", "All regular restaurant dishes and chef specials")
                                )

                                diets.forEach { (name, note) ->
                                    NeomorphicCard(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 5.dp),
                                        onClick = {
                                            effectiveSpeak?.invoke("$name preference noted. Svara will highlight matching dishes.")
                                        }
                                    ) {
                                        Column {
                                            Text(
                                                text = name,
                                                fontSize = 15.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = NeoTextPrimary
                                            )
                                            Text(
                                                text = note,
                                                fontSize = 12.sp,
                                                color = NeoTextSecondary
                                            )
                                        }
                                    }
                                }
                            }

                            4 -> {
                                // STEP 4: WAITER & ORDER REVIEW
                                Text(
                                    text = "4. Assistance & Order Review",
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = NeoTextPrimary
                                )
                                Text(
                                    text = "वेटर सहायता व थाली • సహాయం మరియు ఆర్డర్",
                                    fontSize = 14.sp,
                                    color = NeoTextSecondary,
                                    modifier = Modifier.padding(top = 4.dp, bottom = 14.dp)
                                )

                                NeomorphicCard(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 6.dp),
                                    onClick = {
                                        onClose()
                                        onNavigateToWaiter?.invoke()
                                    }
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.PersonOutline,
                                            contentDescription = null,
                                            tint = NeoPrimary,
                                            modifier = Modifier.size(28.dp)
                                        )
                                        Column {
                                            Text(
                                                text = "Call Waiter to Table (वेटर को बुलाएं)",
                                                fontSize = 16.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = NeoTextPrimary
                                            )
                                            Text(
                                                text = "Quick phrase board to speak to dining staff",
                                                fontSize = 13.sp,
                                                color = NeoTextSecondary
                                            )
                                        }
                                    }
                                }

                                NeomorphicCard(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 6.dp),
                                    onClick = {
                                        onClose()
                                        onNavigateToCart?.invoke()
                                    }
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.ShoppingBag,
                                            contentDescription = null,
                                            tint = VegGreen,
                                            modifier = Modifier.size(28.dp)
                                        )
                                        Column {
                                            Text(
                                                text = "Review Cart & Bill (थाली व बिल)",
                                                fontSize = 16.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = NeoTextPrimary
                                            )
                                            Text(
                                                text = "Check added items, quantities, and totals",
                                                fontSize = 13.sp,
                                                color = NeoTextSecondary
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Persistent Big "Repeat Aloud" Button (Core Senior requirement: Persistent Repeat)
                    NeomorphicButton(
                        text = "Repeat Step Aloud (निर्देश पुनः सुनें)",
                        onClick = {
                            effectiveSpeak?.invoke(stepNarration)
                        },
                        isPrimary = false,
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.VolumeUp,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp)
                            )
                        },
                        modifier = Modifier.fillMaxWidth().height(52.dp)
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    // Step Navigation Buttons (Previous / Next / Done)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        if (currentStep > 1) {
                            Box(modifier = Modifier.weight(1f)) {
                                NeomorphicButton(
                                    text = "Previous (पिछला)",
                                    onClick = { currentStep-- },
                                    isPrimary = false,
                                    modifier = Modifier.fillMaxWidth().height(48.dp)
                                )
                            }
                        }

                        Box(modifier = Modifier.weight(1f)) {
                            if (currentStep < totalSteps) {
                                NeomorphicButton(
                                    text = "Next (अगला)",
                                    onClick = { currentStep++ },
                                    isPrimary = true,
                                    modifier = Modifier.fillMaxWidth().height(48.dp)
                                )
                            } else {
                                NeomorphicButton(
                                    text = "Finish (संपन्न)",
                                    onClick = onClose,
                                    isPrimary = true,
                                    modifier = Modifier.fillMaxWidth().height(48.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

