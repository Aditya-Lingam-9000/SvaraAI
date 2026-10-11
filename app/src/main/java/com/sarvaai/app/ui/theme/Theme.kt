package com.sarvaai.app.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val NeomorphicLightColorScheme = lightColorScheme(
    primary = NeoPrimary,
    onPrimary = NeoOnPrimary,
    primaryContainer = NeoPrimaryContainer,
    onPrimaryContainer = NeoOnPrimaryContainer,
    background = NeoBackground,
    onBackground = NeoTextPrimary,
    surface = NeoSurface,
    onSurface = NeoTextPrimary,
    surfaceVariant = NeoSurfacePressed,
    onSurfaceVariant = NeoTextSecondary,
    outline = NeoBorder,
    outlineVariant = NeoDarkShadow
)

private val HighContrastColorScheme = lightColorScheme(
    primary = NeoPrimary,
    onPrimary = NeoOnPrimary,
    primaryContainer = NeoPrimaryContainer,
    onPrimaryContainer = NeoOnPrimaryContainer,
    background = HighContrastWhite,
    onBackground = HighContrastBlack,
    surface = HighContrastWhite,
    onSurface = HighContrastBlack,
    surfaceVariant = NeoSurfacePressed,
    onSurfaceVariant = HighContrastBlack,
    outline = HighContrastBorder,
    outlineVariant = HighContrastCardBorder
)

@Composable
fun SarvaTheme(
    isHighContrast: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = if (isHighContrast) HighContrastColorScheme else NeomorphicLightColorScheme
    val view = LocalView.current

    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                window.statusBarColor = colorScheme.background.toArgb()
                window.navigationBarColor = colorScheme.background.toArgb()
                val insetsController = WindowCompat.getInsetsController(window, view)
                insetsController.isAppearanceLightStatusBars = true
                insetsController.isAppearanceLightNavigationBars = true
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = SarvaTypography,
        shapes = SarvaShapes,
        content = content
    )
}
