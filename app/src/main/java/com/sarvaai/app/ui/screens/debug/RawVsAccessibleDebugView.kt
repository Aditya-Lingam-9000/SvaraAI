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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.sarvaai.app.adapt.OutputAdapter
import com.sarvaai.app.adapt.verbalizer.NumberVerbalizer
import com.sarvaai.app.llm.models.StructuredAiResponse
import com.sarvaai.app.llm.models.UiAction
import com.sarvaai.app.ui.components.AccessibleButton
import com.sarvaai.app.ui.components.NeomorphicLanguageSelector
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
    var showConciergeDemo by remember { mutableStateOf(false) }
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

            // Language Selector using modern Neomorphic Dropdown
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Select Evaluation Language:",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = NeoTextSecondary
                )
                NeomorphicLanguageSelector(
                    selectedLanguage = selectedLanguage,
                    onLanguageSelected = { code ->
                        if (selectedLanguage != code) {
                            onStopAudio()
                            selectedLanguage = code
                        }
                    }
                )
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

            Spacer(modifier = Modifier.height(24.dp))

            // ========================================================
            // Condition 4: Ageing Condition Showcase
            // (Page 07 of Megathon 2026 Problem Statement)
            // ========================================================
            AgeingConditionShowcase(
                selectedLanguage = selectedLanguage,
                isAudioPlaying = isAudioPlaying,
                onPlayAudio = onPlayAudio,
                onLaunchConciergeDemo = {
                    onStopAudio()
                    showConciergeDemo = true
                }
            )

            Spacer(modifier = Modifier.height(24.dp))
        }

        if (showConciergeDemo) {
            AgeingConciergeDemoDialog(
                selectedLanguage = selectedLanguage,
                isAudioPlaying = isAudioPlaying,
                onPlayAudio = onPlayAudio,
                onStopAudio = onStopAudio,
                onDismiss = {
                    onStopAudio()
                    showConciergeDemo = false
                }
            )
        }
    }
}

private data class AgeingShowcaseData(
    val barrierEnglish: String,
    val barrierLocalized: String?,
    val adaptationsEnglish: String,
    val adaptationsLocalized: String?,
    val rawCardTitle: String,
    val rawText: String,
    val rawAudioSpoken: String,
    val adaptedCardTitle: String,
    val adaptedHeadline: String,
    val adaptedBody: String,
    val adaptedPrompt: String,
    val adaptedAudioSpoken: String
)

@Composable
private fun AgeingConditionShowcase(
    selectedLanguage: String,
    isAudioPlaying: Boolean,
    onPlayAudio: (text: String, language: String) -> Unit,
    onLaunchConciergeDemo: () -> Unit
) {
    val ageingData = remember(selectedLanguage) {
        when (selectedLanguage) {
            "te" -> AgeingShowcaseData(
                barrierEnglish = "Small text, high complexity, rushed speech, and low comfort with dense tech interfaces.",
                barrierLocalized = "చిన్న అక్షరాలు, సంక్లిష్టత, వేగవంతమైన మాటలు మరియు సాంకేతిక ఇంటర్‌ఫేస్‌లతో ఇబ్బంది.",
                adaptationsEnglish = "Large text (1.35x), unhurried slow voice (0.8x), and Step-by-Step guided ordering.",
                adaptationsLocalized = "పెద్ద అక్షరాలు (1.35x), ప్రశాంతమైన నెమ్మది స్వరం (0.8x), మరియు దశలవారీగా సహాయపడే ఆర్డరింగ్.",
                rawCardTitle = "Raw Unadapted Interface",
                rawText = "ORD#8492: 1x కడ్.పనీర్ (కార/గ్రేవీ) ₹280 + 2x గా.నాన్ ₹180 + GST 5% ₹23 + డెలి ₹40 - రాయితీ 10% (గరిష్ట ₹30) -> మొత్తం ₹493. 120సె లోపు UPI/కార్డ్ తో చెల్లించండి లేకపోతే రద్దవుతుంది.",
                rawAudioSpoken = "ఆర్డర్ ఎనిమిది నాలుగు తొమ్మిది రెండు: ఒకటి కడ్ పనీర్ కార గ్రేవీ రెండు వందల ఎనభై రూపాయలు ప్లస్ రెండు గా నాన్ నూట ఎనభై రూపాయలు ప్లస్ జిఎస్టి ఐదు శాతం ఇరవై మూడు రూపాయలు ప్లస్ డెలివరీ నలభై రూపాయలు మైనస్ డిస్కౌంట్ పది శాతం గరిష్టంగా ముప్పై రూపాయలు సబ్ టోటల్ నాలుగు వందల తొంభై మూడు రూపాయలు. నూట ఇరవై సెకన్లలో యూపీఐ లేదా కార్డ్ ద్వారా చెల్లించండి లేకపోతే రద్దవుతుంది.",
                adaptedCardTitle = "Svara Ageing-Adapted Interface",
                adaptedHeadline = "దశ 1 (మొత్తం 2 లో): ప్రధాన ఎంపిక",
                adaptedBody = "మీరు ₹460 కి 1 కడాయి పనీర్ మరియు 2 మెత్తని బటర్ నాన్‌లు తీసుకోవాలనుకుంటున్నారా?",
                adaptedPrompt = "నిదానంగా 'అవును' అని చెప్పండి లేదా క్రింది ఆకుపచ్చ బటన్‌ను నొక్కండి.",
                adaptedAudioSpoken = "మొదటి దశ. మీకు నాలుగు వందల అరవై రూపాయలకు ఒక కడాయి పనీర్ మరియు రెండు మెత్తని బటర్ నాన్‌లు కావాలా? నిదానంగా చెప్పండి. అవును అనండి లేదా ఆకుపచ్చ బటన్‌ను నొక్కండి."
            )
            "hi" -> AgeingShowcaseData(
                barrierEnglish = "Small text, high complexity, rushed speech, and low comfort with dense tech interfaces.",
                barrierLocalized = "छोटे अक्षर, उच्च जटिलता, तेज़ आवाज़ और घने तकनीकी इंटरफेस के साथ असहजता।",
                adaptationsEnglish = "Large text (1.35x), unhurried slow voice (0.8x), and Step-by-Step guided ordering.",
                adaptationsLocalized = "बड़े अक्षर (1.35x), शांत धीमी आवाज़ (0.8x), और चरण-दर-चरण निर्देशित ऑर्डरिंग।",
                rawCardTitle = "Raw Unadapted Interface",
                rawText = "ऑर्डर#8492: 1x कड़.पनीर (तीखा/ग्रेवी) ₹280 + 2x गा.नान ₹180 + GST 5% ₹23 + డెలి ₹40 - छूट 10% (अधिकतम ₹30) -> कुल ₹493। 120 सेकंड में UPI/कार्ड से भुगतान करें नहीं तो रद्द।",
                rawAudioSpoken = "ऑर्डर आठ चार नौ दो: एक कड़ पनीर तीखा ग्रेवी दो सौ अस्सी रुपये प्लस दो गा नान एक सौ अस्सी रुपये प्लस जीएसटी पाँच प्रतिशत तेईस रुपये प्लस डिलीवरी चालीस रुपये माइनस छूट दस प्रतिशत अधिकतम तीस रुपये कुल चार सौ तिरानवे रुपये। एक सौ बीस सेकंड में यूपीआई या कार्ड से भुगतान करें नहीं तो रद्द।",
                adaptedCardTitle = "Svara Ageing-Adapted Interface",
                adaptedHeadline = "चरण 1 (2 में से): मुख्य पसंद",
                adaptedBody = "क्या आप ₹460 में 1 कड़ाही पनीर और 2 नरम बटर नान लेना चाहते हैं?",
                adaptedPrompt = "बस आराम से 'हाँ' बोलें या नीचे दिए गए बड़े हरे बटन को दबाएं।",
                adaptedAudioSpoken = "पहला चरण। क्या आप चार सौ साठ रुपये में एक कड़ाही पनीर और दो नरम बटर नान लेना चाहते हैं? आराम से बताएं। बस हाँ कहें या हरे बटन को दबाएं।"
            )
            else -> AgeingShowcaseData(
                barrierEnglish = "Small text, high complexity, rushed speech, and low comfort with dense tech interfaces.",
                barrierLocalized = null,
                adaptationsEnglish = "Large text (1.35x), unhurried slow voice (0.8x), and Step-by-Step guided ordering.",
                adaptationsLocalized = null,
                rawCardTitle = "Raw Unadapted Interface",
                rawText = "ORD#8492: 1x Kd.Pan (spcy/chk.grv) ₹280 + 2x G.Nan ₹180 + GST 5% ₹23 + Del ₹40 - Disc 10% (MAX ₹30) -> Subtot ₹493. Confirm pay via UPI/CC/NetBnk within 120s or session drops.",
                rawAudioSpoken = "Order 8492: One Kadai Pan spicy check gravy 280 rupees plus two G Naan 180 rupees plus GST 5 percent 23 rupees plus delivery 40 rupees minus discount 10 percent maximum 30 rupees subtotal 493 rupees. Confirm payment via UPI CC net banking within 120 seconds or session drops.",
                adaptedCardTitle = "Svara Ageing-Adapted Interface",
                adaptedHeadline = "Step 1 of 2: Main Choice",
                adaptedBody = "Would you like 1 Kadai Paneer with 2 soft Butter Naans for ₹460?",
                adaptedPrompt = "Just say 'Yes' or tap the large green button below.",
                adaptedAudioSpoken = "Step one of two. Would you like one Kadai Paneer and two soft butter naans for four hundred and sixty rupees? Please take your time. Just say Yes, or tap the large green button."
            )
        }
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        // Section Header
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(NeoPrimaryContainer)
                    .border(1.dp, NeoBorder, RoundedCornerShape(8.dp))
            ) {
                Icon(
                    imageVector = Icons.Default.AccessibilityNew,
                    contentDescription = null,
                    tint = NeoPrimary,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.width(10.dp))
            Column {
                Text(
                    text = "Condition 4: Ageing (Older Adults)",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = NeoTextPrimary,
                    modifier = Modifier.semantics { heading() }
                )
                Text(
                    text = "Megathon 2026 Problem Statement (Page 07)",
                    fontSize = 11.sp,
                    color = NeoTextSecondary
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Barrier & Svara Adaptations specification card
        Card(
            colors = CardDefaults.cardColors(containerColor = NeoSurface),
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, NeoBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Barrier
                Row(verticalAlignment = Alignment.Top) {
                    Text(
                        text = "Barrier: ",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = NonVegRed
                    )
                    Column {
                        Text(
                            text = ageingData.barrierEnglish,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = NeoTextPrimary
                        )
                        ageingData.barrierLocalized?.let { loc ->
                            Text(
                                text = loc,
                                fontSize = 11.sp,
                                color = NeoTextSecondary
                            )
                        }
                    }
                }

                HorizontalDivider(color = NeoBorder.copy(alpha = 0.5f), thickness = 1.dp)

                // Svara Adaptations
                Row(verticalAlignment = Alignment.Top) {
                    Text(
                        text = "Svara Adaptations: ",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = VegGreen
                    )
                    Column {
                        Text(
                            text = ageingData.adaptationsEnglish,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = NeoTextPrimary
                        )
                        ageingData.adaptationsLocalized?.let { loc ->
                            Text(
                                text = loc,
                                fontSize = 11.sp,
                                color = NeoTextSecondary
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Side-by-side interactive demo
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Left Card: "Raw Unadapted Interface"
            Card(
                colors = CardDefaults.cardColors(containerColor = NeoSurface),
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.5.dp, NonVegRed),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                modifier = Modifier.weight(1f)
            ) {
                Column(
                    modifier = Modifier.padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(24.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(NonVegRedContainer)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = null,
                                tint = NonVegRed,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = ageingData.rawCardTitle,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = NonVegRed,
                            maxLines = 1
                        )
                    }

                    // Dense 11sp small text, confusing abbreviations, complex multi-item calculation
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(NeoSurfacePressed)
                            .border(1.dp, NeoBorder, RoundedCornerShape(8.dp))
                            .padding(8.dp)
                    ) {
                        Text(
                            text = ageingData.rawText,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            lineHeight = 15.sp,
                            letterSpacing = (-0.3).sp,
                            color = NeoTextPrimary
                        )
                    }

                    Text(
                        text = "• Dense 11sp monospace text\n• Confusing abbreviations\n• Multi-item calculation stress",
                        fontSize = 10.sp,
                        color = NeoTextSecondary,
                        lineHeight = 14.sp
                    )

                    AccessibleButton(
                        text = "Hear Raw Audio (Fast 1.25x Voice)",
                        onClick = { onPlayAudio(ageingData.rawAudioSpoken, selectedLanguage) },
                        isPrimary = false,
                        modifier = Modifier.fillMaxWidth(),
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                                contentDescription = null,
                                tint = NeoTextPrimary,
                                modifier = Modifier.size(15.dp)
                            )
                        }
                    )
                }
            }

            // Right Card: "Svara Ageing-Adapted Interface"
            Card(
                colors = CardDefaults.cardColors(containerColor = NeoSurface),
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(2.dp, VegGreen),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                modifier = Modifier.weight(1f)
            ) {
                Column(
                    modifier = Modifier.padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(24.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(VegGreenContainer)
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = VegGreen,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = ageingData.adaptedCardTitle,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = VegGreen,
                            maxLines = 1
                        )
                    }

                    // Large 20sp bold high-contrast text, simplified single-step prompt
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(NeoPrimaryContainer.copy(alpha = 0.4f))
                            .border(1.5.dp, VegGreen.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
                            .padding(10.dp)
                    ) {
                        Column {
                            Text(
                                text = ageingData.adaptedHeadline,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = VegGreen
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = ageingData.adaptedBody,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                lineHeight = 26.sp,
                                color = NeoTextPrimary
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = ageingData.adaptedPrompt,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                color = NeoTextSecondary
                            )
                        }
                    }

                    Text(
                        text = "• Large 20sp bold text\n• Simplified 1-step prompt\n• Unhurried calm pacing",
                        fontSize = 10.sp,
                        color = NeoTextSecondary,
                        lineHeight = 14.sp
                    )

                    AccessibleButton(
                        text = "Hear Svara Adapted (Calm 0.8x Voice)",
                        onClick = { onPlayAudio(ageingData.adaptedAudioSpoken, selectedLanguage) },
                        isPrimary = true,
                        modifier = Modifier.fillMaxWidth(),
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                                contentDescription = null,
                                tint = NeoOnPrimary,
                                modifier = Modifier.size(15.dp)
                            )
                        }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Interactive button: "Launch Live Step-by-Step Concierge Demo"
        AccessibleButton(
            text = "Launch Live Step-by-Step Concierge Demo",
            onClick = onLaunchConciergeDemo,
            isPrimary = true,
            modifier = Modifier.fillMaxWidth(),
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.SmartToy,
                    contentDescription = null,
                    tint = NeoOnPrimary,
                    modifier = Modifier.size(20.dp)
                )
            }
        )
    }
}

private data class StepDetails(
    val title: String,
    val headline: String,
    val optionA: String,
    val optionB: String,
    val spoken: String
)

@Composable
private fun AgeingConciergeDemoDialog(
    selectedLanguage: String,
    isAudioPlaying: Boolean,
    onPlayAudio: (text: String, language: String) -> Unit,
    onStopAudio: () -> Unit,
    onDismiss: () -> Unit
) {
    var step by remember { mutableIntStateOf(1) } // 1: Dish, 2: Breads, 3: Confirm, 4: Success
    var selectedDish by remember { mutableStateOf("Kadai Paneer") }
    var dishPrice by remember { mutableIntStateOf(280) }
    var selectedBread by remember { mutableStateOf("2 Butter Naans") }
    var breadPrice by remember { mutableIntStateOf(180) }

    val total = dishPrice + breadPrice

    val stepInfo = remember(selectedLanguage, step, selectedDish, selectedBread, total) {
        when (selectedLanguage) {
            "te" -> when (step) {
                1 -> StepDetails(
                    title = "దశ 1 (మొత్తం 3 లో)",
                    headline = "మీరు ఏ ప్రధాన వంటకం తీసుకోవాలనుకుంటున్నారు?",
                    optionA = "🍛 కడాయి పనీర్ (మృదువైనది) - ₹280",
                    optionB = "🍲 దాల్ మఖ్నీ (వెన్నతో కూడినది) - ₹240",
                    spoken = "మొదటి దశ. ఈరోజు మీరు ఏ వంటకం ఆస్వాదించాలనుకుంటున్నారు? కడాయి పనీర్ లేదా దాల్ మఖ్నీ? నిదానంగా చెప్పండి లేదా నొక్కండి."
                )
                2 -> StepDetails(
                    title = "దశ 2 (మొత్తం 3 లో)",
                    headline = "$selectedDish తో వేడి నాన్‌లు కావాలా?",
                    optionA = "🫓 2 బటర్ నాన్‌లు (+₹180)",
                    optionB = "🍚 బాస్మతి రైస్ (+₹140)",
                    spoken = "రెండవ దశ. మీరు ఎంచుకున్న వంటకంతో రెండు వేడి బటర్ నాన్‌లు కావాలా లేదా బాస్మతి రైస్ కావాలా?"
                )
                3 -> StepDetails(
                    title = "దశ 3 (మొత్తం 3 లో)",
                    headline = "ఆర్డర్ వివరాలు: $selectedDish + $selectedBread. మొత్తం బిల్లు ₹$total.",
                    optionA = "✅ ఆర్డర్‌ను వంటగదికి పంపండి (₹$total)",
                    optionB = "🔄 మార్పులు చేయండి",
                    spoken = "చివరి దశ. మీ ఆర్డర్ మొత్తం బిల్లు $total రూపాయలు. వంటగదికి పంపడానికి పెద్ద ఆకుపచ్చ బటన్‌ను నొక్కండి."
                )
                else -> StepDetails(
                    title = "ఆర్డర్ పూర్తయింది!",
                    headline = "టేబుల్ #4 వద్దకు మీ ఆర్డర్ చేరుతుంది. సర్వర్ రమేష్ గారికి సమాచారం చేరింది.",
                    optionA = "మళ్లీ ఆర్డర్ డెమో ప్రారంభించండి",
                    optionB = "ముగించండి",
                    spoken = "మీ ఆర్డర్ విజయవంతంగా నమోదైంది. టేబుల్ నంబర్ నాలుగు. సర్వర్ రమేష్ గారికి సమాచారం చేరింది."
                )
            }
            "hi" -> when (step) {
                1 -> StepDetails(
                    title = "चरण 1 (3 में से)",
                    headline = "आज आप कौन सा मुख्य व्यंजन पसंद करेंगे?",
                    optionA = "🍛 कड़ाही पनीर (हल्का व नरम) - ₹280",
                    optionB = "🍲 दाल मखनी (मलाईदार) - ₹240",
                    spoken = "पहला चरण। आज आप कौन सा मुख्य व्यंजन लेना चाहेंगे? कड़ाही पनीर या दाल मखनी? आराम से बताएं या बटन दबाएं।"
                )
                2 -> StepDetails(
                    title = "चरण 2 (3 में से)",
                    headline = "$selectedDish के साथ क्या लेना चाहेंगे?",
                    optionA = "🫓 2 बटर नान (+₹180)",
                    optionB = "🍚 बासमती चावल (+₹140)",
                    spoken = "दूसरा चरण। आपके व्यंजन के साथ क्या आप दो बटर नान लेना चाहेंगे या बासमती चावल?"
                )
                3 -> StepDetails(
                    title = "चरण 3 (3 में से)",
                    headline = "ऑर्डर विवरण: $selectedDish + $selectedBread। कुल बिल ₹$total।",
                    optionA = "✅ रसोई में ऑर्डर भेजें (₹$total)",
                    optionB = "🔄 पसंद बदलें",
                    spoken = "अंतिम चरण। आपके ऑर्डर का कुल बिल $total रुपये है। रसोई में भेजने के लिए बड़े हरे बटन को दबाएं।"
                )
                else -> StepDetails(
                    title = "ऑर्डर दर्ज हो गया!",
                    headline = "टेबल #4। आपके व्यंजन की तैयारी शुरू हो गई है।",
                    optionA = "नया ऑर्डर शुरू करें",
                    optionB = "बंद करें",
                    spoken = "आपका ऑर्डर दर्ज हो गया है। टेबल नंबर चार। वेटर रमेश को सूचना दे दी गई है।"
                )
            }
            else -> when (step) {
                1 -> StepDetails(
                    title = "Step 1 of 3",
                    headline = "Which main dish would you like to enjoy today?",
                    optionA = "🍛 Kadai Paneer (Mild & Soft) - ₹280",
                    optionB = "🍲 Dal Makhani (Gentle Creamy) - ₹240",
                    spoken = "Step one of three. Which main dish would you like to enjoy today? Kadai Paneer or Dal Makhani? Please take your time."
                )
                2 -> StepDetails(
                    title = "Step 2 of 3",
                    headline = "Would you like hot Naans or Rice with $selectedDish?",
                    optionA = "🫓 2 Butter Naans (+₹180)",
                    optionB = "🍚 Steamed Basmati Rice (+₹140)",
                    spoken = "Step two of three. Would you like two soft butter naans, or steamed basmati rice with your dish?"
                )
                3 -> StepDetails(
                    title = "Step 3 of 3",
                    headline = "Order Summary: $selectedDish + $selectedBread.\nTotal Bill: ₹$total.",
                    optionA = "✅ Confirm Order & Send to Kitchen (₹$total)",
                    optionB = "🔄 Change Selection",
                    spoken = "Final step. Your order total is $total rupees. Press the large green button to comfortably send your order to the kitchen."
                )
                else -> StepDetails(
                    title = "Order Placed Comfortably!",
                    headline = "Table #4 • Order sent to kitchen • Server Ramesh notified",
                    optionA = "Start New Order Demo",
                    optionB = "Close Concierge",
                    spoken = "Your order has been successfully placed. Table number four. Server Ramesh has been notified."
                )
            }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .wrapContentHeight()
                .clip(RoundedCornerShape(20.dp))
                .border(2.dp, VegGreen, RoundedCornerShape(20.dp)),
            color = NeoSurface
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Header with Step Pill & Close
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(34.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(VegGreenContainer)
                        ) {
                            Icon(
                                imageVector = Icons.Default.SmartToy,
                                contentDescription = null,
                                tint = VegGreen,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Svara Ageing Concierge",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = NeoTextPrimary
                            )
                            Text(
                                text = "Guided Step-by-Step • Calm 0.8x Pacing",
                                fontSize = 11.sp,
                                color = VegGreen,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(NeoSurfacePressed)
                            .border(1.dp, NeoBorder, CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close Concierge Demo",
                            tint = NeoTextPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                HorizontalDivider(color = NeoBorder, thickness = 1.dp)

                // Step Pill
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(NeoPrimaryContainer)
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = stepInfo.title,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = NeoPrimary
                    )
                }

                // 20sp Bold Question Box
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(NeoPrimaryContainer.copy(alpha = 0.3f))
                        .border(1.5.dp, NeoPrimary.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                        .padding(16.dp)
                ) {
                    Text(
                        text = stepInfo.headline,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        lineHeight = 28.sp,
                        color = NeoTextPrimary
                    )
                }

                // Spoken audio button
                AccessibleButton(
                    text = "Hear This Step (Calm 0.8x Voice)",
                    onClick = { onPlayAudio(stepInfo.spoken, selectedLanguage) },
                    isPrimary = false,
                    modifier = Modifier.fillMaxWidth(),
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                            contentDescription = null,
                            tint = NeoTextPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                )

                Spacer(modifier = Modifier.height(4.dp))

                // Big Choice Buttons (WCAG AAA touch targets, 60dp height, 18sp text)
                if (step == 1) {
                    AccessibleButton(
                        text = stepInfo.optionA,
                        onClick = {
                            selectedDish = "Kadai Paneer"
                            dishPrice = 280
                            onStopAudio()
                            step = 2
                        },
                        isPrimary = true,
                        modifier = Modifier.fillMaxWidth().height(60.dp)
                    )
                    AccessibleButton(
                        text = stepInfo.optionB,
                        onClick = {
                            selectedDish = "Dal Makhani"
                            dishPrice = 240
                            onStopAudio()
                            step = 2
                        },
                        isPrimary = true,
                        modifier = Modifier.fillMaxWidth().height(60.dp)
                    )
                } else if (step == 2) {
                    AccessibleButton(
                        text = stepInfo.optionA,
                        onClick = {
                            selectedBread = "2 Butter Naans"
                            breadPrice = 180
                            onStopAudio()
                            step = 3
                        },
                        isPrimary = true,
                        modifier = Modifier.fillMaxWidth().height(60.dp)
                    )
                    AccessibleButton(
                        text = stepInfo.optionB,
                        onClick = {
                            selectedBread = "Basmati Rice"
                            breadPrice = 140
                            onStopAudio()
                            step = 3
                        },
                        isPrimary = true,
                        modifier = Modifier.fillMaxWidth().height(60.dp)
                    )
                } else if (step == 3) {
                    AccessibleButton(
                        text = stepInfo.optionA,
                        onClick = {
                            onStopAudio()
                            step = 4
                        },
                        isPrimary = true,
                        modifier = Modifier.fillMaxWidth().height(64.dp)
                    )
                    AccessibleButton(
                        text = stepInfo.optionB,
                        onClick = {
                            onStopAudio()
                            step = 1
                        },
                        isPrimary = false,
                        modifier = Modifier.fillMaxWidth().height(54.dp)
                    )
                } else {
                    AccessibleButton(
                        text = stepInfo.optionA,
                        onClick = {
                            onStopAudio()
                            step = 1
                        },
                        isPrimary = true,
                        modifier = Modifier.fillMaxWidth().height(60.dp)
                    )
                    AccessibleButton(
                        text = stepInfo.optionB,
                        onClick = onDismiss,
                        isPrimary = false,
                        modifier = Modifier.fillMaxWidth().height(54.dp)
                    )
                }
            }
        }
    }
}
