package com.sarvaai.app.ui.screens.debug

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sarvaai.app.adapt.OutputAdapter
import com.sarvaai.app.adapt.verbalizer.NumberVerbalizer
import com.sarvaai.app.llm.models.StructuredAiResponse
import com.sarvaai.app.llm.models.UiAction
import com.sarvaai.app.ui.components.AccessibleButton
import com.sarvaai.app.ui.theme.*

@Composable
fun RawVsAccessibleDebugView(
    isAudioPlaying: Boolean = false,
    onPlayAudio: (text: String, language: String) -> Unit,
    onStopAudio: () -> Unit = {},
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedLanguage by remember { mutableStateOf("te") }
    val scrollState = rememberScrollState()

    // Safe Back handling
    BackHandler {
        onStopAudio()
        onClose()
    }

    DisposableEffect(Unit) {
        onDispose {
            onStopAudio()
        }
    }

    // Sample complex dish comparison query
    val sampleRawMarkdown = """
    **Kadai Paneer vs Paneer Butter Masala:**
    * Kadai Paneer: Spicy gravy with bell peppers (₹280) [Medium-High]
    * Paneer Butter Masala: Mild buttery cashew gravy (₹320) [Mild]
    * Order recommendation: Kadai Paneer + 2 Garlic Naans (₹90 each) = Total ₹460.
    """.trimIndent()

    val sampleStructuredResponse = remember(selectedLanguage) {
        when (selectedLanguage) {
            "te" -> StructuredAiResponse(
                intent = "COMPARE_DISHES",
                headline = "కడాయి పనీర్ vs పనీర్ బటర్ మసాలా",
                spoken = "కడాయి పనీర్ కాస్త కారంగా క్యాప్సికమ్‌తో ఉంటుంది, ధర ₹280. పనీర్ బటర్ మసాలా తియ్యని జీడిపప్పు గ్రేవీతో ఉంటుంది, ధర ₹320. రెండు గార్లిక్ నాన్‌లతో కలిపి మొత్తం ₹460 అవుతుంది.",
                caption = "కడాయి పనీర్ ₹280 (కారం). పనీర్ బటర్ మసాలా ₹320 (మృదువు). మొత్తం ₹460.",
                bullets = listOf(
                    "కడాయి పనీర్: ₹280 (మధ్యస్థ కారం)",
                    "పనీర్ బటర్ మసాలా: ₹320 (వెన్న & జీడిపప్పు గ్రేవీ)",
                    "మొత్తం కాంబో: ₹460"
                ),
                actions = listOf(
                    UiAction("act_1", "Add Kadai Paneer Combo", "ADD_TO_CART", "kadai_paneer_combo:1")
                )
            )
            "hi" -> StructuredAiResponse(
                intent = "COMPARE_DISHES",
                headline = "कड़ाही पनीर बनाम पनीर बटर मसाला",
                spoken = "कड़ाही पनीर शिमला मिर्च के साथ तीखा होता है, कीमत ₹280 है। पनीर बटर मसाला काजू ग्रेवी के साथ हल्का मीठा होता है, कीमत ₹320 है। दो गार्लिक नान के साथ कुल बिल ₹460 होगा।",
                caption = "कड़ाही पनीर ₹280 (तीखा)। पनीर बटर मसाला ₹320 (हल्का)। कुल ₹460।",
                bullets = listOf(
                    "कड़ाही पनीर: ₹280 (तीखा ग्रेवी)",
                    "पनीर बटर मसाला: ₹320 (मक्खन और काजू)",
                    "कुल कॉम्बो: ₹460"
                ),
                actions = listOf(
                    UiAction("act_1", "कड़ाही पनीर कॉम्बो जोड़ें", "ADD_TO_CART", "kadai_paneer_combo:1")
                )
            )
            else -> StructuredAiResponse(
                intent = "COMPARE_DISHES",
                headline = "Kadai Paneer vs Paneer Butter Masala",
                spoken = "Kadai Paneer is spicy with bell peppers, priced at ₹280. Paneer Butter Masala has a rich buttery cashew gravy, priced at ₹320. With two garlic naans, your total is ₹460.",
                caption = "Kadai Paneer ₹280 (Spicy). Paneer Butter Masala ₹320 (Mild). Total ₹460.",
                bullets = listOf(
                    "Kadai Paneer: ₹280 (Spicy with capsicum)",
                    "Paneer Butter Masala: ₹320 (Rich buttery gravy)",
                    "Recommended Combo: ₹460"
                ),
                actions = listOf(
                    UiAction("act_1", "Add Recommended Combo", "ADD_TO_CART", "kadai_paneer_combo:1")
                )
            )
        }
    }

    val adaptedOutput = remember(sampleStructuredResponse, selectedLanguage) {
        OutputAdapter.adapt(sampleStructuredResponse, selectedLanguage)
    }

    Surface(
        modifier = modifier.fillMaxSize(),
        color = NeoBackground
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
                .systemBarsPadding()
                .verticalScroll(scrollState)
        ) {
            // Top Bar
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
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
                            imageVector = Icons.Default.CompareArrows,
                            contentDescription = null,
                            tint = NeoPrimary,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "Raw vs Accessible",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = NeoTextPrimary,
                            modifier = Modifier.semantics { heading() }
                        )
                        Text(
                            text = "Side-by-Side Multi-Disability Access Evaluation",
                            fontSize = 12.sp,
                            color = NeoTextSecondary
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (isAudioPlaying) {
                        IconButton(
                            onClick = onStopAudio,
                            modifier = Modifier
                                .padding(end = 6.dp)
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(NonVegRedContainer)
                                .border(1.dp, NonVegRed, CircleShape)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Stop,
                                contentDescription = "Stop playing audio",
                                tint = NonVegRed,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    IconButton(
                        onClick = {
                            onStopAudio()
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
                            contentDescription = "Close comparison view",
                            tint = NeoTextPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Language Selector
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                listOf("te" to "తెలుగు (TE)", "hi" to "हिन्दी (HI)", "en" to "English (EN)").forEach { (code, label) ->
                    val isSelected = selectedLanguage == code
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .weight(1f)
                            .height(42.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (isSelected) NeoPrimary else NeoSurface)
                            .border(
                                1.5.dp,
                                if (isSelected) NeoPrimary else NeoBorder,
                                RoundedCornerShape(10.dp)
                            )
                            .clickable {
                                if (selectedLanguage != code) {
                                    onStopAudio()
                                    selectedLanguage = code
                                }
                            }
                    ) {
                        Text(
                            text = label,
                            fontSize = 12.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            color = if (isSelected) NeoOnPrimary else NeoTextPrimary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Card 1: Raw LLM Output (Unadapted)
            Card(
                colors = CardDefaults.cardColors(containerColor = NeoSurface),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.5.dp, NonVegRed),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(32.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(NonVegRedContainer)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = null,
                                tint = NonVegRed,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Standard Raw LLM Output (Inaccessible)",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = NonVegRed
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = "• Raw markdown symbols cause robotic stutter in TTS\n• Unverbalized currency symbols (₹280) break screen readers\n• Unbounded dense paragraphs confuse elderly and cognitive users",
                        fontSize = 12.sp,
                        color = NeoTextSecondary,
                        lineHeight = 18.sp
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(NeoSurfacePressed)
                            .border(1.dp, NeoBorder, RoundedCornerShape(10.dp))
                            .padding(12.dp)
                    ) {
                        Text(
                            text = sampleRawMarkdown,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
                            color = NeoTextPrimary
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    AccessibleButton(
                        text = "Hear Unadapted Raw Audio",
                        onClick = { onPlayAudio(sampleRawMarkdown, selectedLanguage) },
                        isPrimary = false,
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                                contentDescription = null,
                                tint = NeoTextPrimary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Card 2: SarvaAI Adapted Accessible Output
            Card(
                colors = CardDefaults.cardColors(containerColor = NeoSurface),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(2.dp, VegGreen),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(32.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(VegGreenContainer)
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = VegGreen,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "SarvaAI Accessible Output Adapter",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = VegGreen
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = "• Currency verbalized to exact spoken words (${NumberVerbalizer.verbalizePrice(460, selectedLanguage)})\n• Markdown and symbols stripped for acoustic clarity\n• Sentence-chunked streaming with live caption highlighting\n• TalkBack semantic merger and high contrast buttons (WCAG AAA)",
                        fontSize = 12.sp,
                        color = NeoTextSecondary,
                        lineHeight = 18.sp
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Accessible Structured Preview
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(NeoPrimaryContainer.copy(alpha = 0.4f))
                            .border(1.dp, NeoPrimary.copy(alpha = 0.4f), RoundedCornerShape(10.dp))
                            .padding(14.dp)
                    ) {
                        Column {
                            Text(
                                text = adaptedOutput.formattedHeadline,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = NeoPrimary
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = adaptedOutput.spokenText,
                                fontSize = 14.sp,
                                color = NeoTextPrimary,
                                lineHeight = 20.sp
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            adaptedOutput.formattedBullets.forEach { bullet ->
                                Text(
                                    text = "• $bullet",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = NeoTextSecondary
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    AccessibleButton(
                        text = "Hear SarvaAI Accessible Audio",
                        onClick = { onPlayAudio(adaptedOutput.spokenText, selectedLanguage) },
                        isPrimary = true,
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                                contentDescription = null,
                                tint = NeoOnPrimary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}
