package com.sarvaai.app.ui.screens.debug

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
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
import com.sarvaai.app.ui.components.AccessibleCard
import com.sarvaai.app.ui.theme.*

@Composable
fun RawVsAccessibleDebugView(
    onPlayAudio: (text: String, language: String) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedLanguage by remember { mutableStateOf("te") }
    val scrollState = rememberScrollState()

    // Sample complex dish comparison query
    val sampleRawMarkdown = """
    **Kadai Paneer vs Paneer Butter Masala:**
    * Kadai Paneer: Spicy gravy with bell peppers (₹280) [🌶️🌶️ Medium-High]
    * Paneer Butter Masala: Mild buttery cashew gravy (₹320) [🧈 Mild]
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
        color = CharcoalBackground
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
                Text(
                    text = "Raw vs Accessible Comparison",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = SaffronPrimary,
                    modifier = Modifier.semantics { heading() }
                )

                IconButton(onClick = onClose) {
                    Icon(imageVector = Icons.Default.Close, contentDescription = "Close comparison view", tint = PureWhite)
                }
            }

            Text(
                text = "Live Side-by-Side Jury Evaluation for Multi-Disability Access",
                fontSize = 13.sp,
                color = SubtitleText
            )

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
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isSelected) SaffronPrimary else CharcoalSurfaceVariant)
                            .border(1.5.dp, if (isSelected) SaffronPrimary else Color(0xFF444444), RoundedCornerShape(8.dp))
                            .clickable { selectedLanguage = code }
                    ) {
                        Text(
                            text = label,
                            fontSize = 12.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            color = if (isSelected) SaffronOnPrimary else PureWhite
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Card 1: Raw LLM Output (Unadapted)
            AccessibleCard(borderColor = NonVegRed) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(imageVector = Icons.Default.Warning, contentDescription = null, tint = NonVegRed, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Standard Raw LLM Output (Inaccessible)",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = NonVegRed
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "• Raw markdown symbols (**, *, 🌶️, 🧈) cause robotic stutter in TTS\n• Unverbalized currency symbols (₹280) break screen readers\n• Unbounded dense paragraphs confuse elderly/cognitive users",
                    fontSize = 12.sp,
                    color = SubtitleText,
                    lineHeight = 16.sp
                )
                Spacer(modifier = Modifier.height(10.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF1E1E1E))
                        .border(1.dp, Color(0xFF333333), RoundedCornerShape(8.dp))
                        .padding(12.dp)
                ) {
                    Text(
                        text = sampleRawMarkdown,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        color = Color(0xFFCCCCCC)
                    )
                }
                Spacer(modifier = Modifier.height(10.dp))
                AccessibleButton(
                    text = "Hear Unadapted Raw Audio",
                    onClick = { onPlayAudio(sampleRawMarkdown, selectedLanguage) },
                    isPrimary = false
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Card 2: SarvaAI Adapted Accessible Output
            AccessibleCard(borderColor = VegGreen) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(imageVector = Icons.Default.CheckCircle, contentDescription = null, tint = VegGreen, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "SarvaAI Accessible Output Adapter",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = VegGreen
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "• Currency verbalized to exact spoken words (${NumberVerbalizer.verbalizePrice(460, selectedLanguage)})\n• Markdown & emojis stripped for acoustic clarity\n• Sentence-chunked streaming with live yellow caption highlighting\n• TalkBack semantic merger and high contrast buttons (WCAG AAA)",
                    fontSize = 12.sp,
                    color = SubtitleText,
                    lineHeight = 16.sp
                )
                Spacer(modifier = Modifier.height(10.dp))

                // Accessible Structured Preview
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(CharcoalSurfaceVariant)
                        .border(1.dp, SaffronPrimary, RoundedCornerShape(8.dp))
                        .padding(12.dp)
                ) {
                    Column {
                        Text(
                            text = adaptedOutput.formattedHeadline,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = SaffronPrimary
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = adaptedOutput.spokenText,
                            fontSize = 14.sp,
                            color = PureWhite,
                            lineHeight = 20.sp
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        adaptedOutput.formattedBullets.forEach { bullet ->
                            Text(
                                text = "• $bullet",
                                fontSize = 13.sp,
                                color = HighContrastYellow
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
                AccessibleButton(
                    text = "Hear SarvaAI Accessible Audio",
                    onClick = { onPlayAudio(adaptedOutput.spokenText, selectedLanguage) },
                    isPrimary = true
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
