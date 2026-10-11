package com.sarvaai.app.ui.screens.onboarding

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.CompareArrows
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sarvaai.app.R
import com.sarvaai.app.ui.components.NeomorphicButton
import com.sarvaai.app.ui.components.NeomorphicIconButton
import com.sarvaai.app.ui.components.neomorphicSurface
import com.sarvaai.app.ui.theme.*
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * SharedPreferences storage helper managing Onboarding completion status.
 */
object OnboardingPreferences {
    const val PREFS_NAME = "svara_prefs"
    const val KEY_ONBOARDING_COMPLETED = "KEY_ONBOARDING_COMPLETED"
    private const val LEGACY_KEY_COMPLETED = "svara_onboarding_completed"

    fun isOnboardingCompleted(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_ONBOARDING_COMPLETED, false) ||
                prefs.getBoolean(LEGACY_KEY_COMPLETED, false)
    }

    fun setOnboardingCompleted(context: Context, completed: Boolean = true) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putBoolean(KEY_ONBOARDING_COMPLETED, completed)
            .putBoolean(LEGACY_KEY_COMPLETED, completed)
            .apply()
    }

    fun resetOnboarding(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .remove(KEY_ONBOARDING_COMPLETED)
            .remove(LEGACY_KEY_COMPLETED)
            .apply()
    }
}

/**
 * Data model for each feature sliding card in the SvaraAI onboarding journey.
 */
data class OnboardingCardData(
    val id: Int,
    val title: String,
    val badgeIcon: ImageVector,
    val badgeAccentColor: Color,
    val categoryLabel: String,
    val useCase: String,
    val howToUse: String,
    val bhashiniAiHighlight: String,
    val keyCapabilities: List<String>,
    val sampleVoicePrompt: String? = null
)

/**
 * The 9 comprehensive feature cards detailing SvaraAI capabilities.
 */
val ONBOARDING_CARDS: List<OnboardingCardData> = listOf(
    OnboardingCardData(
        id = 1,
        title = "Voice-First Restaurant Assistant",
        badgeIcon = Icons.Default.Mic,
        badgeAccentColor = NeoPrimary,
        categoryLabel = "Hands-Free Accessibility",
        useCase = "Hands-free dining assistance for visually impaired, elderly, and all diners.",
        howToUse = "Tap the glowing mic or speak the wake-word to browse dishes, check specials, and ask queries.",
        bhashiniAiHighlight = "Real-time acoustic VAD and wake-word recognition engine with instantaneous speech response.",
        keyCapabilities = listOf("Zero-Touch Navigation", "Visual Impairment Ready", "Acoustic Energy VAD", "Screen Reader Synced"),
        sampleVoicePrompt = "\"Hey Svara, what are the popular South Indian breakfast items today?\""
    ),
    OnboardingCardData(
        id = 2,
        title = "Bhashini Multi-Indic ASR & TTS",
        badgeIcon = Icons.Default.Translate,
        badgeAccentColor = Color(0xFF0D9488),
        categoryLabel = "Indic Speech AI",
        useCase = "Voice recognition and natural speech across Hindi, Telugu, Tamil, Kannada, Malayalam, English.",
        howToUse = "Speak naturally in your mother tongue; SvaraAI recognizes regional dialects and replies with native voice inflections.",
        bhashiniAiHighlight = "Powered by official Government of India Bhashini ASR & TTS models with restaurant lexical normalizers.",
        keyCapabilities = listOf("6+ Indic Languages", "Phonetic Normalization", "Dialect Sensitivity", "Low-Latency TTS"),
        sampleVoicePrompt = "\"मसाला डोसा की कीमत क्या है?\" / \"దోశలో నెయ్యి వేస్తారా?\""
    ),
    OnboardingCardData(
        id = 3,
        title = "CameraX Menu Vision Scanner",
        badgeIcon = Icons.Default.DocumentScanner,
        badgeAccentColor = Color(0xFF4F46E5),
        categoryLabel = "Spatial Computer Vision",
        useCase = "AI OCR instantly digitizes multi-page physical menus into structured dishes and prices.",
        howToUse = "Point your camera at any paper or laminated menu; guided spatial voice coaching assists blind diners to frame pages cleanly.",
        bhashiniAiHighlight = "Dual-engine ML Kit spatial OCR + Bhashini Vision OCR with Groq Llama-3 menu structuring.",
        keyCapabilities = listOf("Auditory Framing Coaching", "Dual OCR Engine", "Structured Dish JSON", "Multi-Page Memory"),
        sampleVoicePrompt = "\"Spatial coach: Tilt camera slightly upwards to capture top pricing column.\""
    ),
    OnboardingCardData(
        id = 4,
        title = "Smart Dish Comparison Matrix",
        badgeIcon = Icons.AutoMirrored.Filled.CompareArrows,
        badgeAccentColor = Color(0xFF7C3AED),
        categoryLabel = "Culinary Intelligence",
        useCase = "Side-by-side comparison of dishes by spice level, price, and dietary attributes.",
        howToUse = "Say 'Compare Paneer Tikka and Mushroom Masala' to view nutritional breakdowns, spice ratings, and value differences.",
        bhashiniAiHighlight = "Groq LLM culinary matrix generator evaluating ingredients, portion sizes, preparation styles, and calorie densities.",
        keyCapabilities = listOf("Side-by-Side Matrix", "1-4 Spice Scale", "Calorie Density", "Value Assessment"),
        sampleVoicePrompt = "\"Svara, compare Dal Tadka and Dal Makhani on spice level and calories.\""
    ),
    OnboardingCardData(
        id = 5,
        title = "Dietary & Allergen Safety Guard",
        badgeIcon = Icons.Default.Shield,
        badgeAccentColor = VegGreen,
        categoryLabel = "Safety & Allergen Audit",
        useCase = "Instant vegetarian, Jain, nut-free analysis and culinary Q&A.",
        howToUse = "Ask 'Does Dal Makhani have dairy?' or 'Is this 100% Jain?' for instant ingredient cross-checking and safety alerts.",
        bhashiniAiHighlight = "Culinary safety reasoning engine augmented with live web search to verify regional cooking traditions and hidden allergens.",
        keyCapabilities = listOf("100% Jain Certified", "Tree Nut & Dairy Audit", "Root Vegetable Detection", "Allergen Alerts"),
        sampleVoicePrompt = "\"Does the Shahi Paneer contain cashew nut paste or onion garlic?\""
    ),
    OnboardingCardData(
        id = 6,
        title = "Voice Ordering Cart & Tax Engine",
        badgeIcon = Icons.Default.ShoppingCart,
        badgeAccentColor = Color(0xFFEA580C),
        categoryLabel = "Conversational Cart",
        useCase = "Speak naturally to add items and compute real-time CGST/SGST breakdowns.",
        howToUse = "Say 'Add two Butter Naan and one Dal Tadka' — the cart manages quantities, special cooking requests, and itemized totals.",
        bhashiniAiHighlight = "Conversational entity extraction parses quantities and dish nicknames while Indian GST engines compute statutory tax.",
        keyCapabilities = listOf("Natural Voice Adding", "Cooking Instructions", "2.5% CGST + 2.5% SGST", "Instant Cart Audit"),
        sampleVoicePrompt = "\"Add 2 Garlic Naans with less butter and 1 Butter Chicken to my order.\""
    ),
    OnboardingCardData(
        id = 7,
        title = "Multilingual Waiter Bridge",
        badgeIcon = Icons.Default.SupportAgent,
        badgeAccentColor = Color(0xFF0284C7),
        categoryLabel = "Staff Bridge",
        useCase = "Cross-language communication between diner and staff with instant TTS.",
        howToUse = "Speak in Tamil or Telugu; the phone instantly translates and announces the message aloud to the waiter in Hindi or English.",
        bhashiniAiHighlight = "Bidirectional Bhashini NMT (Neural Machine Translation) paired with fast neural speech synthesis and staff phraseboards.",
        keyCapabilities = listOf("Two-Way Translation", "Instant Audio Announcement", "Quick Waiter Phrases", "Staff Dialect Selector"),
        sampleVoicePrompt = "\"Diner speaks Telugu -> Svara speaks Hindi: 'भैया, कृपया बिल ले आइए'\""
    ),
    OnboardingCardData(
        id = 8,
        title = "Instant Staff Feedback & Escalation",
        badgeIcon = Icons.Default.Campaign,
        badgeAccentColor = Color(0xFFE11D48),
        categoryLabel = "Floor Escalation",
        useCase = "Voice-activated feedback or complaint escalation directly to captain/manager.",
        howToUse = "Express satisfaction or raise table concerns via quick voice notes, alerting floor managers promptly without friction.",
        bhashiniAiHighlight = "Sentiment scoring and automated priority routing flags critical food temperature, dietary, or delay complaints instantly.",
        keyCapabilities = listOf("Urgent Manager Flag", "Floor Captain Alert", "Direct Audio Logging", "Sentiment Classifier"),
        sampleVoicePrompt = "\"Please let the restaurant captain know the soup arrived cold at Table 4.\""
    ),
    OnboardingCardData(
        id = 9,
        title = "Smart Bill Splitter",
        badgeIcon = Icons.Default.Calculate,
        badgeAccentColor = NeoPrimary,
        categoryLabel = "Billing & Verbalization",
        useCase = "Equal split and itemized share calculation with spoken verbalization.",
        howToUse = "Enter diner count, tip percentage, or item claims — hear auditory breakdown of individual shares and UPI round-offs.",
        bhashiniAiHighlight = "Indic Number Verbalizer articulates Rupee and Paise figures with grammatically natural speech across Indian languages.",
        keyCapabilities = listOf("Equal & Itemized Splits", "Tip & GST Allocation", "Spoken Rupee Verbalizer", "UPI Share Summary"),
        sampleVoicePrompt = "\"Total bill of ₹1,850 split 4 ways is ₹462.50 per person, rounded to ₹463.\""
    )
)

/**
 * Main Onboarding Screen implementing the Neomorphic sliding card walkthrough.
 *
 * @param onComplete Callback invoked when onboarding is finished or skipped.
 * @param selectedLanguage Active language code (e.g. "te", "hi", "en").
 * @param onLanguageSelected Callback invoked when user selects a language preference.
 */
@Composable
fun OnboardingScreen(
    onComplete: () -> Unit,
    modifier: Modifier = Modifier,
    selectedLanguage: String = "te",
    onLanguageSelected: (String) -> Unit = {}
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val pagerState = rememberPagerState(
        initialPage = 0,
        pageCount = { ONBOARDING_CARDS.size }
    )

    fun finishOnboarding() {
        OnboardingPreferences.setOnboardingCompleted(context, true)
        onComplete()
    }

    // Hardware back button moves backward through cards or finishes
    BackHandler(enabled = true) {
        if (pagerState.currentPage > 0) {
            coroutineScope.launch {
                pagerState.animateScrollToPage(pagerState.currentPage - 1)
            }
        } else {
            finishOnboarding()
        }
    }

    Surface(
        modifier = modifier.fillMaxSize(),
        color = NeoBackground
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            // Top Header: Logo, Title, and Skip Button
            OnboardingHeader(
                onSkip = { finishOnboarding() },
                currentPage = pagerState.currentPage,
                totalPages = ONBOARDING_CARDS.size
            )

            // Central Sliding Cards Pager
            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 6.dp),
                pageSpacing = 16.dp
            ) { page ->
                val card = ONBOARDING_CARDS[page]
                val pageOffset = (pagerState.currentPage - page) + pagerState.currentPageOffsetFraction

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            val clampedOffset = pageOffset.coerceIn(-1f, 1f)
                            val scale = 1f - (abs(clampedOffset) * 0.05f)
                            val alpha = 1f - (abs(clampedOffset) * 0.35f)
                            scaleX = scale
                            scaleY = scale
                            this.alpha = alpha
                        }
                ) {
                    OnboardingCardView(
                        card = card,
                        isLastCard = page == ONBOARDING_CARDS.size - 1,
                        selectedLanguage = selectedLanguage,
                        onLanguageSelected = onLanguageSelected,
                        onGetStarted = { finishOnboarding() }
                    )
                }
            }

            // Bottom Navigation Controls: Back, Dots, Next / Finish
            OnboardingBottomControls(
                pagerState = pagerState,
                pageCount = ONBOARDING_CARDS.size,
                onBack = {
                    if (pagerState.currentPage > 0) {
                        coroutineScope.launch {
                            pagerState.animateScrollToPage(pagerState.currentPage - 1)
                        }
                    }
                },
                onNext = {
                    if (pagerState.currentPage < ONBOARDING_CARDS.size - 1) {
                        coroutineScope.launch {
                            pagerState.animateScrollToPage(pagerState.currentPage + 1)
                        }
                    } else {
                        finishOnboarding()
                    }
                },
                onDotClick = { targetIndex ->
                    coroutineScope.launch {
                        pagerState.animateScrollToPage(targetIndex)
                    }
                }
            )
        }
    }
}



/**
 * Top Header featuring the official app logo, SvaraAI branding, and a Neomorphic Skip button.
 */
@Composable
private fun OnboardingHeader(
    onSkip: () -> Unit,
    currentPage: Int,
    totalPages: Int,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        // App Logo + Title
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(46.dp)
                    .neomorphicSurface(
                        shape = CircleShape,
                        surfaceColor = NeoSurface,
                        lightColor = NeoLightShadow,
                        darkColor = NeoDarkShadow,
                        borderColor = NeoBorder,
                        elevation = 4.dp,
                        cornerRadius = 23.dp
                    )
                    .padding(3.dp)
            ) {
                Image(
                    painter = painterResource(id = R.drawable.app_logo),
                    contentDescription = "SvaraAI Official Logo",
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                )
            }

            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "SvaraAI",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = NeoTextPrimary,
                        letterSpacing = (-0.5).sp
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(NeoPrimaryContainer)
                            .padding(horizontal = 5.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "ACCESSIBLE",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = NeoOnPrimaryContainer,
                            letterSpacing = 0.5.sp
                        )
                    }
                }
                Text(
                    text = "Multimodal Dining Intelligence",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color = NeoTextSecondary
                )
            }
        }

        // Skip Button (Neomorphic Pill)
        val interactionSource = remember { MutableInteractionSource() }
        val isPressed by interactionSource.collectIsPressedAsState()
        val isLastPage = currentPage == totalPages - 1

        if (!isLastPage) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .height(36.dp)
                    .neomorphicSurface(
                        shape = RoundedCornerShape(18.dp),
                        surfaceColor = if (isPressed) NeoSurfacePressed else NeoSurface,
                        lightColor = NeoLightShadow,
                        darkColor = NeoDarkShadow,
                        borderColor = NeoBorder,
                        elevation = 3.dp,
                        isPressed = isPressed,
                        cornerRadius = 18.dp
                    )
                    .clickable(
                        interactionSource = interactionSource,
                        indication = null,
                        onClick = onSkip
                    )
                    .padding(horizontal = 14.dp)
                    .semantics {
                        contentDescription = "Skip feature onboarding walkthrough"
                        role = Role.Button
                    }
            ) {
                Text(
                    text = "Skip Tour",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = NeoTextSecondary
                )
            }
        } else {
            // Subtle completion indicator on the final card
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .height(36.dp)
                    .padding(horizontal = 8.dp)
            ) {
                Text(
                    text = "Final Step",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = NeoPrimary
                )
            }
        }
    }
}

/**
 * Individual sliding card component designed with dual-bevel Neomorphism.
 */
@Composable
private fun OnboardingCardView(
    card: OnboardingCardData,
    isLastCard: Boolean,
    selectedLanguage: String,
    onLanguageSelected: (String) -> Unit,
    onGetStarted: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()

    Box(
        modifier = modifier
            .fillMaxSize()
            .neomorphicSurface(
                shape = RoundedCornerShape(24.dp),
                surfaceColor = NeoSurface,
                lightColor = NeoLightShadow,
                darkColor = NeoDarkShadow,
                borderColor = NeoBorder,
                elevation = 7.dp,
                cornerRadius = 24.dp
            )
            .padding(2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Top Card Header: Distinctive Icon Badge & Category Pill
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Distinctive Neomorphic Icon Badge
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(62.dp)
                        .neomorphicSurface(
                            shape = CircleShape,
                            surfaceColor = NeoSurface,
                            lightColor = NeoLightShadow,
                            darkColor = NeoDarkShadow,
                            borderColor = card.badgeAccentColor.copy(alpha = 0.35f),
                            elevation = 5.dp,
                            cornerRadius = 31.dp
                        )
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(46.dp)
                            .clip(CircleShape)
                            .background(card.badgeAccentColor.copy(alpha = 0.12f))
                    ) {
                        Icon(
                            imageVector = card.badgeIcon,
                            contentDescription = null,
                            tint = card.badgeAccentColor,
                            modifier = Modifier.size(26.dp)
                        )
                    }
                }

                // Step & Category Pill
                Column(horizontalAlignment = Alignment.End) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(card.badgeAccentColor.copy(alpha = 0.10f))
                            .border(1.dp, card.badgeAccentColor.copy(alpha = 0.30f), RoundedCornerShape(10.dp))
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "STEP ${card.id} OF 9",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = card.badgeAccentColor,
                            letterSpacing = 0.8.sp
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = card.categoryLabel,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = NeoTextMuted
                    )
                }
            }

            // Feature Title
            Text(
                text = card.title,
                fontSize = 21.sp,
                fontWeight = FontWeight.ExtraBold,
                color = NeoTextPrimary,
                lineHeight = 26.sp,
                modifier = Modifier.semantics { heading() }
            )

            // Section 1: What is its use (1 crisp line)
            OnboardingSectionBox(
                headerTitle = "WHAT IS ITS USE",
                accentColor = card.badgeAccentColor,
                icon = Icons.Default.CheckCircle,
                bodyText = card.useCase
            )

            // Section 2: How it is used (1 crisp line)
            OnboardingSectionBox(
                headerTitle = "HOW IT IS USED",
                accentColor = NeoTextSecondary,
                icon = Icons.Default.TouchApp,
                bodyText = card.howToUse
            )

            // Section 3: Bhashini / AI Integration Highlight
            OnboardingAiHighlightBox(
                highlightText = card.bhashiniAiHighlight,
                accentColor = card.badgeAccentColor
            )

            // Interactive Language Selector Badge if Card 2 (Bhashini Multi-Indic)
            if (card.id == 2) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "PREFERRED VOICE LANGUAGE",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = NeoTextMuted,
                        letterSpacing = 0.8.sp
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf("te" to "తెలుగు", "hi" to "हिन्दी", "en" to "English").forEach { (code, name) ->
                            val isChosen = selectedLanguage == code
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(38.dp)
                                    .neomorphicSurface(
                                        shape = RoundedCornerShape(10.dp),
                                        surfaceColor = if (isChosen) NeoPrimary else NeoSurface,
                                        lightColor = if (isChosen) Color(0x6693C5FD) else NeoLightShadow,
                                        darkColor = if (isChosen) Color(0x661E3A8A) else NeoDarkShadow,
                                        borderColor = if (isChosen) NeoPrimary else NeoBorder,
                                        elevation = 3.dp,
                                        isPressed = isChosen,
                                        cornerRadius = 10.dp
                                    )
                                    .clickable { onLanguageSelected(code) }
                                    .semantics {
                                        contentDescription = "Select $name"
                                        role = Role.Button
                                    }
                            ) {
                                Text(
                                    text = name,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isChosen) NeoOnPrimary else NeoTextPrimary
                                )
                            }
                        }
                    }
                }
            }

            // Key Capabilities Chips
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "CORE CAPABILITIES",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = NeoTextMuted,
                    letterSpacing = 0.8.sp
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    card.keyCapabilities.take(2).forEach { cap ->
                        CapabilityPill(text = cap, modifier = Modifier.weight(1f))
                    }
                }
                if (card.keyCapabilities.size > 2) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        card.keyCapabilities.drop(2).take(2).forEach { cap ->
                            CapabilityPill(text = cap, modifier = Modifier.weight(1f))
                        }
                    }
                }
            }

            // Sample Spoken Voice Interaction Quote
            if (card.sampleVoicePrompt != null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(NeoSurfacePressed)
                        .border(1.dp, NeoBorder, RoundedCornerShape(12.dp))
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                            contentDescription = null,
                            tint = card.badgeAccentColor,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = card.sampleVoicePrompt,
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Medium,
                            color = NeoTextPrimary,
                            lineHeight = 15.sp
                        )
                    }
                }
            }

            // Final Card Special CTA: "Get Started / Enter SvaraAI"
            if (isLastCard) {
                Spacer(modifier = Modifier.height(4.dp))
                NeomorphicButton(
                    text = "Get Started / Enter SvaraAI",
                    onClick = onGetStarted,
                    isPrimary = true,
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = null,
                            tint = NeoOnPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                    },
                    contentDescription = "Complete onboarding and enter SvaraAI restaurant companion",
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

/**
 * Reusable section container for "What is its use" and "How it is used".
 */
@Composable
private fun OnboardingSectionBox(
    headerTitle: String,
    accentColor: Color,
    icon: ImageVector,
    bodyText: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(NeoBackground)
            .border(1.dp, NeoBorder, RoundedCornerShape(14.dp))
            .padding(10.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(14.dp)
                )
                Text(
                    text = headerTitle,
                    fontSize = 9.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = accentColor,
                    letterSpacing = 0.8.sp
                )
            }
            Text(
                text = bodyText,
                fontSize = 12.5.sp,
                fontWeight = FontWeight.Medium,
                color = NeoTextPrimary,
                lineHeight = 17.sp
            )
        }
    }
}

/**
 * Special Neomorphic container highlighting the Bhashini / AI Integration engine.
 */
@Composable
private fun OnboardingAiHighlightBox(
    highlightText: String,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(
                Brush.horizontalGradient(
                    colors = listOf(
                        NeoPrimaryContainer.copy(alpha = 0.65f),
                        NeoSurfacePressed
                    )
                )
            )
            .border(1.dp, NeoPrimary.copy(alpha = 0.30f), RoundedCornerShape(14.dp))
            .padding(10.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.AutoAwesome,
                    contentDescription = null,
                    tint = NeoOnPrimaryContainer,
                    modifier = Modifier.size(15.dp)
                )
                Text(
                    text = "BHASHINI & AI ENGINE HIGHLIGHT",
                    fontSize = 9.5.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = NeoOnPrimaryContainer,
                    letterSpacing = 0.8.sp
                )
            }
            Text(
                text = highlightText,
                fontSize = 11.5.sp,
                fontWeight = FontWeight.SemiBold,
                color = NeoTextPrimary,
                lineHeight = 16.sp
            )
        }
    }
}

/**
 * Small micro-pill for core capability tags.
 */
@Composable
private fun CapabilityPill(
    text: String,
    modifier: Modifier = Modifier
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(NeoSurface)
            .border(1.dp, NeoBorder, RoundedCornerShape(8.dp))
            .padding(horizontal = 8.dp, vertical = 6.dp)
    ) {
        Text(
            text = text,
            fontSize = 10.5.sp,
            fontWeight = FontWeight.SemiBold,
            color = NeoTextSecondary,
            textAlign = TextAlign.Center,
            maxLines = 1
        )
    }
}

/**
 * Bottom navigation controls featuring Back, animated Pager Dots, and Next/Finish button.
 */
@Composable
private fun OnboardingBottomControls(
    pagerState: PagerState,
    pageCount: Int,
    onBack: () -> Unit,
    onNext: () -> Unit,
    onDotClick: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val isLastPage = pagerState.currentPage == pageCount - 1
    val isFirstPage = pagerState.currentPage == 0

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp),
        color = NeoBackground
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Back Button
            if (!isFirstPage) {
                NeomorphicIconButton(
                    icon = Icons.AutoMirrored.Filled.ArrowBack,
                    onClick = onBack,
                    contentDescription = "Navigate to previous card",
                    size = 46.dp
                )
            } else {
                Spacer(modifier = Modifier.size(46.dp))
            }

            // Animated Neomorphic Pager Indicator Dots
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.semantics {
                    contentDescription = "Page ${pagerState.currentPage + 1} of $pageCount"
                }
            ) {
                for (i in 0 until pageCount) {
                    val isSelected = pagerState.currentPage == i
                    val dotWidth by animateDpAsState(
                        targetValue = if (isSelected) 22.dp else 8.dp,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = Spring.StiffnessMedium
                        ),
                        label = "dot_width_$i"
                    )
                    val dotColor by animateColorAsState(
                        targetValue = if (isSelected) NeoPrimary else NeoDarkShadow,
                        animationSpec = tween(durationMillis = 250),
                        label = "dot_color_$i"
                    )

                    Box(
                        modifier = Modifier
                            .height(8.dp)
                            .width(dotWidth)
                            .clip(CircleShape)
                            .background(dotColor)
                            .clickable { onDotClick(i) }
                            .semantics {
                                contentDescription = "Jump to card ${i + 1}"
                                role = Role.Button
                            }
                    )
                }
            }

            // Next / Finish Button
            if (!isLastPage) {
                NeomorphicIconButton(
                    icon = Icons.AutoMirrored.Filled.ArrowForward,
                    onClick = onNext,
                    contentDescription = "Navigate to next card",
                    tint = NeoOnPrimary,
                    size = 46.dp,
                    isSelected = true
                )
            } else {
                // Checkmark button on final card
                NeomorphicIconButton(
                    icon = Icons.Default.Check,
                    onClick = onNext,
                    contentDescription = "Finish onboarding and enter app",
                    tint = NeoOnPrimary,
                    size = 46.dp,
                    isSelected = true
                )
            }
        }
    }
}
