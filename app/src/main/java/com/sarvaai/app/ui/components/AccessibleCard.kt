package com.sarvaai.app.ui.components

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.sarvaai.app.ui.theme.NeoBorder
import com.sarvaai.app.ui.theme.NeoSurface

/**
 * Accessible card container styled with the Neomorphic dual-bevel surface.
 */
@Composable
fun AccessibleCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    contentDescription: String? = null,
    borderColor: Color = NeoBorder,
    containerColor: Color = NeoSurface,
    content: @Composable ColumnScope.() -> Unit
) {
    NeomorphicCard(
        modifier = modifier,
        onClick = onClick,
        contentDescription = contentDescription,
        borderColor = borderColor,
        containerColor = containerColor,
        content = content
    )
}
