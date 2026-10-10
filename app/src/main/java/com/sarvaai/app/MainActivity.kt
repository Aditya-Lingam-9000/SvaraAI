package com.sarvaai.app

import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sarvaai.app.audio.haptics.HapticFeedbackEngine
import com.sarvaai.app.audio.haptics.HapticPattern
import com.sarvaai.app.data.db.AppDatabase
import com.sarvaai.app.ui.components.*
import com.sarvaai.app.ui.theme.*
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private lateinit var hapticEngine: HapticFeedbackEngine
    private lateinit var database: AppDatabase

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        hapticEngine = HapticFeedbackEngine(this)
        database = AppDatabase.getInstance(this)

        setContent {
            var isHighContrast by remember { mutableStateOf(false) }

            SarvaTheme(isHighContrast = isHighContrast) {
                MainAccessibleScreen(
                    isHighContrast = isHighContrast,
                    onToggleHighContrast = { isHighContrast = !isHighContrast },
                    onHapticTrigger = { pattern -> hapticEngine.trigger(pattern) }
                )
            }
        }
    }
}

@Composable
fun MainAccessibleScreen(
    isHighContrast: Boolean,
    onToggleHighContrast: () -> Unit,
    onHapticTrigger: (HapticPattern) -> Unit
) {
    val scrollState = rememberScrollState()
    var captionHighlightIndex by remember { mutableIntStateOf(0) }
    val sampleCaption = "SarvaAI voice assistant is ready. Point your camera at a restaurant menu."

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp)
                .systemBarsPadding()
                .verticalScroll(scrollState),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            // Title & Subtitle
            Text(
                text = "SarvaAI",
                fontSize = 36.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center,
                modifier = Modifier.semantics { heading() }
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "Multilingual Accessible Restaurant Assistant",
                fontSize = 18.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Live Caption Bar Primitive
            LiveCaptionBar(
                fullCaption = sampleCaption,
                highlightedWordIndex = captionHighlightIndex,
                isVisible = true
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Telemetry & Architecture Foundation Card
            AccessibleCard(
                contentDescription = "Phase 1 Foundations Status card: MVI Architecture, WCAG AAA Theme, and Room Database active"
            ) {
                Text(
                    text = "Phase 1: Architecture & Foundations",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.semantics { heading() }
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "• MVI Unidirectional State Pattern\n" +
                            "• WCAG AAA High Contrast Design System\n" +
                            "• Dynamic Typography (up to 200% font scale)\n" +
                            "• Room Database 2.6+ (Paise Financial Precision)\n" +
                            "• O-Haptics Tactile Motor Feedback Engine",
                    fontSize = 15.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    lineHeight = 22.sp
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Dietary Primitives Demonstration Card
            AccessibleCard(
                contentDescription = "Dietary Indicators preview showing Vegetarian, Non-Vegetarian, and Jain tags"
            ) {
                Text(
                    text = "Dietary Indicators",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.semantics { heading() }
                )
                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        DietaryBadge(isVegetarian = true, isJain = false)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(text = "Veg", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface)
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        DietaryBadge(isVegetarian = false, isJain = false)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(text = "Non-Veg", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface)
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        DietaryBadge(isVegetarian = true, isJain = true)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(text = "Jain", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface)
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Interactive Controls Demonstration
            AccessibleButton(
                text = if (isHighContrast) "Switch to Standard Dark Mode" else "Switch to High Contrast Mode",
                onClick = {
                    onHapticTrigger(HapticPattern.CLICK)
                    onToggleHighContrast()
                },
                isPrimary = true,
                contentDescription = "Toggle high contrast mode"
            )

            Spacer(modifier = Modifier.height(12.dp))

            AccessibleButton(
                text = "Test Haptic Confirmation",
                onClick = {
                    onHapticTrigger(HapticPattern.CONFIRMATION)
                    captionHighlightIndex = (captionHighlightIndex + 1) % sampleCaption.split(" ").size
                },
                isPrimary = false,
                contentDescription = "Trigger tactile vibration and advance caption word"
            )

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
