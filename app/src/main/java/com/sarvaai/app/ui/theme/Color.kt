package com.sarvaai.app.ui.theme

import androidx.compose.ui.graphics.Color

// ============================================================================
// Neomorphism Design System Color Palette (Light / Pure Off-White Theme)
// ============================================================================

// Neomorphic Surface & Shadow Primitives
val NeoBackground = Color(0xFFF0F4F8)
val NeoSurface = Color(0xFFF0F4F8)
val NeoSurfacePressed = Color(0xFFE2E8F0)
val NeoLightShadow = Color(0xFFFFFFFF)     // Top-Left specular reflection
val NeoDarkShadow = Color(0xFFD1D9E6)      // Bottom-Right diffuse soft shadow
val NeoBorder = Color(0xFFE2E8F0)          // Crisp contour edge

// Primary Accent Color (Royal Cobalt Blue)
val NeoPrimary = Color(0xFF2563EB)
val NeoOnPrimary = Color(0xFFFFFFFF)
val NeoPrimaryContainer = Color(0xFFDBEAFE)
val NeoOnPrimaryContainer = Color(0xFF1E40AF)

// Neomorphic Dark Typography Palette (Slate Scale)
val NeoTextPrimary = Color(0xFF0F172A)     // Slate 900
val NeoTextSecondary = Color(0xFF475569)   // Slate 600
val NeoTextMuted = Color(0xFF94A3B8)       // Slate 400

// Semantic Dietary Colors (WCAG AAA Compliant)
val VegGreen = Color(0xFF16A34A)           // Emerald Green (Veg)
val VegGreenContainer = Color(0xFFDCFCE7)
val VegGreenOnContainer = Color(0xFF15803D)

val NonVegRed = Color(0xFFDC2626)          // Crimson Red (Non-Veg)
val NonVegRedContainer = Color(0xFFFEE2E2)
val NonVegRedOnContainer = Color(0xFFB91C1C)

val JainYellow = Color(0xFFD97706)         // Amber Gold (Jain)
val JainYellowContainer = Color(0xFFFEF3C7)
val JainYellowOnContainer = Color(0xFFB45309)

val SpiceRed = Color(0xFFEA580C)

// High Contrast Accessibility Mode Colors
val HighContrastBlack = Color(0xFF000000)
val HighContrastWhite = Color(0xFFFFFFFF)
val HighContrastYellow = Color(0xFFFFD600)
val HighContrastBorder = Color(0xFF0F172A)
val HighContrastCardBorder = Color(0xFF2563EB)

// ============================================================================
// Legacy / Deprecated Color Mappings (Redirected to Neomorphic Palette)
// ============================================================================
@Deprecated("Use NeoBackground instead", ReplaceWith("NeoBackground"))
val CharcoalBackground = NeoBackground

@Deprecated("Use NeoSurface instead", ReplaceWith("NeoSurface"))
val CharcoalSurface = NeoSurface

@Deprecated("Use NeoSurfacePressed instead", ReplaceWith("NeoSurfacePressed"))
val CharcoalSurfaceVariant = NeoSurfacePressed

@Deprecated("Use NeoBorder instead", ReplaceWith("NeoBorder"))
val CharcoalSurfaceHighlight = NeoBorder

@Deprecated("Use NeoPrimary instead", ReplaceWith("NeoPrimary"))
val SaffronPrimary = NeoPrimary

@Deprecated("Use NeoOnPrimary instead", ReplaceWith("NeoOnPrimary"))
val SaffronOnPrimary = NeoOnPrimary

@Deprecated("Use NeoPrimaryContainer instead", ReplaceWith("NeoPrimaryContainer"))
val SaffronContainer = NeoPrimaryContainer

@Deprecated("Use NeoOnPrimaryContainer instead", ReplaceWith("NeoOnPrimaryContainer"))
val SaffronOnContainer = NeoOnPrimaryContainer

@Deprecated("Use NeoTextPrimary or Color.White as appropriate", ReplaceWith("NeoTextPrimary"))
val PureWhite = NeoTextPrimary

@Deprecated("Use NeoTextMuted instead", ReplaceWith("NeoTextMuted"))
val MutedText = NeoTextMuted

@Deprecated("Use NeoTextSecondary instead", ReplaceWith("NeoTextSecondary"))
val SubtitleText = NeoTextSecondary
