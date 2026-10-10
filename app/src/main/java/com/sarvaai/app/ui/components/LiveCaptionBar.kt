package com.sarvaai.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sarvaai.app.ui.theme.HighContrastYellow
import com.sarvaai.app.ui.theme.PureWhite

@Composable
fun LiveCaptionBar(
    fullCaption: String,
    highlightedWordIndex: Int = -1,
    isVisible: Boolean = true,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = isVisible && fullCaption.isNotEmpty(),
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = modifier
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .background(Color.Black, RoundedCornerShape(12.dp))
                .border(2.dp, PureWhite, RoundedCornerShape(12.dp))
                .padding(16.dp)
                .semantics {
                    liveRegion = LiveRegionMode.Polite
                }
        ) {
            val words = fullCaption.split(" ")
            val annotated = buildAnnotatedString {
                words.forEachIndexed { index, word ->
                    if (index == highlightedWordIndex) {
                        withStyle(
                            style = SpanStyle(
                                color = HighContrastYellow,
                                fontWeight = FontWeight.ExtraBold,
                                background = Color(0xFF333300)
                            )
                        ) {
                            append(word)
                        }
                    } else {
                        withStyle(style = SpanStyle(color = PureWhite, fontWeight = FontWeight.Normal)) {
                            append(word)
                        }
                    }
                    if (index < words.size - 1) append(" ")
                }
            }

            Text(
                text = annotated,
                fontSize = 20.sp,
                lineHeight = 28.sp,
                style = MaterialTheme.typography.bodyLarge
            )
        }
    }
}
