package com.sarvaai.app.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Accessible tactile button styled with the Neomorphism design system.
 */
@Composable
fun AccessibleButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    isPrimary: Boolean = true,
    enabled: Boolean = true,
    leadingIcon: (@Composable () -> Unit)? = null
) {
    NeomorphicButton(
        text = text,
        onClick = onClick,
        modifier = modifier,
        isPrimary = isPrimary,
        enabled = enabled,
        leadingIcon = leadingIcon,
        contentDescription = contentDescription
    )
}
