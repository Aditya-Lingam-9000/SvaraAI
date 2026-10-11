package com.sarvaai.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sarvaai.app.ui.theme.*

/**
 * High-contrast Dietary Badge indicator (Vegetarian, Non-Vegetarian, Jain) with clean vector geometry.
 */
@Composable
fun DietaryBadge(
    isVegetarian: Boolean,
    isJain: Boolean = false,
    modifier: Modifier = Modifier
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = modifier
    ) {
        if (isVegetarian) {
            // Veg Symbol: Green Container Box with Green Inscribed Circle
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(22.dp)
                    .background(VegGreenContainer, RoundedCornerShape(4.dp))
                    .border(1.5.dp, VegGreen, RoundedCornerShape(4.dp))
                    .padding(3.dp)
                    .semantics {
                        contentDescription = "Vegetarian food indicator"
                    }
            ) {
                Canvas(modifier = Modifier.size(9.dp)) {
                    drawCircle(color = VegGreen)
                }
            }
        } else {
            // Non-Veg Symbol: Red Container Box with Red Inscribed Triangle
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(22.dp)
                    .background(NonVegRedContainer, RoundedCornerShape(4.dp))
                    .border(1.5.dp, NonVegRed, RoundedCornerShape(4.dp))
                    .padding(3.dp)
                    .semantics {
                        contentDescription = "Non-vegetarian food indicator"
                    }
            ) {
                Canvas(modifier = Modifier.size(9.dp)) {
                    val path = Path().apply {
                        moveTo(size.width / 2f, 0f)
                        lineTo(size.width, size.height)
                        lineTo(0f, size.height)
                        close()
                    }
                    drawPath(path = path, color = NonVegRed)
                }
            }
        }

        if (isJain) {
            Box(
                modifier = Modifier
                    .background(JainYellowContainer, RoundedCornerShape(4.dp))
                    .border(1.5.dp, JainYellow, RoundedCornerShape(4.dp))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
                    .semantics {
                        contentDescription = "Jain suitable dish, prepared without root vegetables"
                    }
            ) {
                Text(
                    text = "JAIN",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = JainYellowOnContainer
                )
            }
        }
    }
}
