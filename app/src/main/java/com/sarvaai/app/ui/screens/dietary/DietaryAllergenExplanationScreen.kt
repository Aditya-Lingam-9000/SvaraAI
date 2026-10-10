package com.sarvaai.app.ui.screens.dietary

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sarvaai.app.adapt.adapter.TableNarrator
import com.sarvaai.app.audio.earcons.EarconEngine
import com.sarvaai.app.audio.earcons.EarconType
import com.sarvaai.app.audio.haptics.HapticFeedbackEngine
import com.sarvaai.app.audio.haptics.HapticPattern
import com.sarvaai.app.audio.recorder.AudioRecorder
import com.sarvaai.app.bhashini.asr.AsrNmtClient
import com.sarvaai.app.core.result.Result
import com.sarvaai.app.data.dao.MenuDao
import com.sarvaai.app.data.entities.MenuItemEntity
import com.sarvaai.app.dialogue.dietary.DietaryCheckResult
import com.sarvaai.app.dialogue.dietary.DietaryCompliance
import com.sarvaai.app.dialogue.dietary.DietaryRuleEngine
import com.sarvaai.app.dialogue.explain.ExplainSimplerEngine
import com.sarvaai.app.llm.GroqClient
import com.sarvaai.app.ui.components.AccessibleButton
import com.sarvaai.app.ui.components.LiveCaptionBar
import com.sarvaai.app.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class DietaryTab {
    SAFETY_CHECK,
    EXPLAIN_SIMPLER,
    DISH_COMPARISON
}

@Composable
fun DietaryAllergenExplanationScreen(
    menuDao: MenuDao?,
    groqClient: GroqClient,
    audioRecorder: AudioRecorder,
    asrNmtClient: AsrNmtClient,
    hapticEngine: HapticFeedbackEngine,
    earconEngine: EarconEngine,
    selectedLanguage: String,
    isAudioPlaying: Boolean = false,
    onSpeakNarration: (String) -> Unit,
    onStopNarration: () -> Unit = {},
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    var selectedTab by remember { mutableStateOf(DietaryTab.SAFETY_CHECK) }
    var selectedDietType by remember { mutableStateOf("JAIN") } // JAIN, NUT_ALLERGY, VEGAN
    var explainLevel by remember { mutableStateOf(3) } // 1, 2, 3
    var liveCaptionText by remember { mutableStateOf<String?>(null) }
    var isAiProcessing by remember { mutableStateOf(false) }

    val isRecording by audioRecorder.isRecording.collectAsState()

    // Sample/Scanned Menu Items
    var menuItems by remember { mutableStateOf<List<MenuItemEntity>>(emptyList()) }
    var selectedDish by remember { mutableStateOf<MenuItemEntity?>(null) }

    // Dish Comparison selections
    var compareDish1 by remember { mutableStateOf<MenuItemEntity?>(null) }
    var compareDish2 by remember { mutableStateOf<MenuItemEntity?>(null) }

    // Dietary Check Result
    var checkResult by remember { mutableStateOf<DietaryCheckResult?>(null) }
    var activeExplanation by remember { mutableStateOf<String?>(null) }

    DisposableEffect(Unit) {
        scope.launch {
            val dbItems = menuDao?.getAllItemsDirect() ?: emptyList()
            if (dbItems.isNotEmpty()) {
                menuItems = dbItems
                selectedDish = dbItems.firstOrNull()
                compareDish1 = dbItems.getOrNull(0)
                compareDish2 = dbItems.getOrNull(1)
            } else {
                // Built-in accessible sample items
                val fallbackItems = listOf(
                    MenuItemEntity(itemId = "d1", sectionId = "s1", nameEnglish = "Masala Dosa", pricePaise = 12000L, isVegetarian = true, description = "Crispy crepe with spiced potato and onion filling"),
                    MenuItemEntity(itemId = "d2", sectionId = "s1", nameEnglish = "Idli Sambar", pricePaise = 8000L, isVegetarian = true, isJain = true, isVegan = true, description = "Steamed rice cakes with lentil soup"),
                    MenuItemEntity(itemId = "d3", sectionId = "s2", nameEnglish = "Shahi Paneer", pricePaise = 26000L, isVegetarian = true, isNutFree = false, description = "Cottage cheese in rich cashew and cream gravy"),
                    MenuItemEntity(itemId = "d4", sectionId = "s2", nameEnglish = "Aloo Gobi", pricePaise = 18000L, isVegetarian = true, isVegan = true, description = "Dry potato and cauliflower curry"),
                    MenuItemEntity(itemId = "d5", sectionId = "s3", nameEnglish = "Gulab Jamun", pricePaise = 9000L, isVegetarian = true, description = "Fried khoya milk balls in cardamom sugar syrup")
                )
                menuItems = fallbackItems
                selectedDish = fallbackItems.firstOrNull()
                compareDish1 = fallbackItems.getOrNull(0)
                compareDish2 = fallbackItems.getOrNull(2)
            }
        }
        onDispose {
            onStopNarration()
            if (audioRecorder.isRecording.value) {
                audioRecorder.stopRecording()
            }
        }
    }

    fun runDietaryCheck(dish: MenuItemEntity, diet: String) {
        val res = when (diet) {
            "JAIN" -> DietaryRuleEngine.checkJainCompliance(dish, selectedLanguage)
            "NUT_ALLERGY" -> DietaryRuleEngine.checkNutAllergy(dish, selectedLanguage)
            "VEGAN" -> DietaryRuleEngine.checkVeganCompliance(dish, selectedLanguage)
            else -> DietaryRuleEngine.checkJainCompliance(dish, selectedLanguage)
        }
        checkResult = res
        liveCaptionText = res.explanation
        when (res.compliance) {
            DietaryCompliance.COMPLIANT -> {
                hapticEngine.trigger(HapticPattern.CONFIRMATION)
                earconEngine.play(EarconType.ORDER_CONFIRMED)
            }
            DietaryCompliance.VIOLATION -> {
                hapticEngine.trigger(HapticPattern.ERROR_ALERT)
                earconEngine.play(EarconType.ERROR_ALERT)
            }
            DietaryCompliance.UNKNOWN_NEEDS_STAFF_CONFIRMATION -> {
                hapticEngine.trigger(HapticPattern.CLICK)
            }
        }
        onSpeakNarration(res.explanation)
    }

    fun updateExplanation(dish: MenuItemEntity, level: Int) {
        val text = ExplainSimplerEngine.getInstantExplanation(dish.nameEnglish, level, selectedLanguage)
        activeExplanation = text
        liveCaptionText = text
        hapticEngine.trigger(HapticPattern.CLICK)
        onSpeakNarration(text)
    }

    fun handleCapturedSpeech(wavBytes: ByteArray) {
        if (wavBytes.isEmpty()) return
        isAiProcessing = true
        scope.launch(Dispatchers.IO) {
            val asrRes = asrNmtClient.transcribeAndTranslate(wavBytes, selectedLanguage)
            val transcript = if (asrRes is Result.Success) asrRes.data.originalTranscript.trim() else ""

            if (transcript.isNotBlank()) {
                val lower = transcript.lowercase()
                withContext(Dispatchers.Main) {
                    isAiProcessing = false
                    liveCaptionText = transcript

                    // Detect intent
                    if (lower.contains("jain") || lower.contains("జైన్") || lower.contains("जैन")) {
                        selectedTab = DietaryTab.SAFETY_CHECK
                        selectedDietType = "JAIN"
                        val matched = menuItems.firstOrNull { lower.contains(it.nameEnglish.lowercase()) } ?: selectedDish
                        matched?.let { runDietaryCheck(it, "JAIN") }
                    } else if (lower.contains("nut") || lower.contains("allergy") || lower.contains("నట్స్") || lower.contains("एलर्जी") || lower.contains("काजू")) {
                        selectedTab = DietaryTab.SAFETY_CHECK
                        selectedDietType = "NUT_ALLERGY"
                        val matched = menuItems.firstOrNull { lower.contains(it.nameEnglish.lowercase()) } ?: selectedDish
                        matched?.let { runDietaryCheck(it, "NUT_ALLERGY") }
                    } else if (lower.contains("explain") || lower.contains("వివరించండి") || lower.contains("बताओ") || lower.contains("level 3") || lower.contains("level 2")) {
                        selectedTab = DietaryTab.EXPLAIN_SIMPLER
                        explainLevel = if (lower.contains("3") || lower.contains("three") || lower.contains("మూడు") || lower.contains("तीन")) 3 else if (lower.contains("2") || lower.contains("two") || lower.contains("రెండు") || lower.contains("दो")) 2 else 1
                        val matched = menuItems.firstOrNull { lower.contains(it.nameEnglish.lowercase()) } ?: selectedDish
                        matched?.let {
                            selectedDish = it
                            updateExplanation(it, explainLevel)
                        }
                    } else if (lower.contains("compare") || lower.contains("పోల్చండి") || lower.contains("तुलना")) {
                        selectedTab = DietaryTab.DISH_COMPARISON
                        if (compareDish1 != null && compareDish2 != null) {
                            val table = TableNarrator.generateComparisonTable(listOf(compareDish1!!, compareDish2!!))
                            val narration = TableNarrator.narrateComparison(table, selectedLanguage)
                            onSpeakNarration(narration)
                        }
                    } else {
                        // General dietary check
                        val matched = menuItems.firstOrNull { lower.contains(it.nameEnglish.lowercase()) } ?: selectedDish
                        matched?.let { runDietaryCheck(it, selectedDietType) }
                    }
                }
            } else {
                withContext(Dispatchers.Main) {
                    isAiProcessing = false
                    hapticEngine.trigger(HapticPattern.ERROR_ALERT)
                    earconEngine.play(EarconType.ERROR_ALERT)
                }
            }
        }
    }

    Surface(
        modifier = modifier.fillMaxSize(),
        color = CharcoalBackground
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp)
                    .systemBarsPadding()
            ) {
                // Top Header Bar
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp)
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Dietary & Food Intelligence (P8)",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = SaffronPrimary,
                            modifier = Modifier.semantics { heading() }
                        )
                        Text(
                            text = "Allergen Rules • 3-Tier Explanations • Table Narrator",
                            fontSize = 12.sp,
                            color = SubtitleText
                        )
                    }

                    if (isAudioPlaying) {
                        IconButton(
                            onClick = onStopNarration,
                            modifier = Modifier
                                .padding(end = 4.dp)
                                .size(36.dp)
                                .background(NonVegRed.copy(alpha = 0.2f), CircleShape)
                        ) {
                            Icon(imageVector = Icons.Default.Stop, contentDescription = "Stop speech", tint = NonVegRed)
                        }
                    }

                    IconButton(
                        onClick = {
                            hapticEngine.trigger(HapticPattern.CLICK)
                            onStopNarration()
                            onClose()
                        }
                    ) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "Close", tint = PureWhite)
                    }
                }

                // Live Captions Bar
                LiveCaptionBar(
                    fullCaption = liveCaptionText ?: "",
                    isVisible = !liveCaptionText.isNullOrBlank(),
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                // Navigation Tabs (Safety Check | Explain Simpler | Dish Comparison)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    listOf(
                        DietaryTab.SAFETY_CHECK to "🛡️ Dietary Safety",
                        DietaryTab.EXPLAIN_SIMPLER to "💡 Explain Simpler",
                        DietaryTab.DISH_COMPARISON to "⚖️ Compare Dishes"
                    ).forEach { (tab, label) ->
                        val isSelected = selectedTab == tab
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .weight(1f)
                                .height(44.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (isSelected) SaffronPrimary else CharcoalSurfaceVariant)
                                .border(1.5.dp, if (isSelected) SaffronPrimary else Color(0xFF444444), RoundedCornerShape(10.dp))
                                .clickable {
                                    hapticEngine.trigger(HapticPattern.CLICK)
                                    selectedTab = tab
                                    onStopNarration()
                                }
                        ) {
                            Text(
                                text = label,
                                fontSize = 11.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSelected) SaffronOnPrimary else PureWhite,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Tab Content Body
                when (selectedTab) {
                    DietaryTab.SAFETY_CHECK -> {
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .verticalScroll(rememberScrollState())
                        ) {
                            Text(text = "1. Select Dietary Profile", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = SaffronPrimary)
                            Spacer(modifier = Modifier.height(6.dp))

                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                                listOf("JAIN" to "🟨 Jain Diet", "NUT_ALLERGY" to "🥜 Nut Allergy", "VEGAN" to "🌱 Vegan").forEach { (diet, label) ->
                                    val isSelected = selectedDietType == diet
                                    Box(
                                        contentAlignment = Alignment.Center,
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(40.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(if (isSelected) HighContrastYellow else CharcoalSurface)
                                            .border(1.dp, if (isSelected) HighContrastYellow else Color(0xFF555555), RoundedCornerShape(8.dp))
                                            .clickable {
                                                hapticEngine.trigger(HapticPattern.CLICK)
                                                selectedDietType = diet
                                                selectedDish?.let { runDietaryCheck(it, diet) }
                                            }
                                    ) {
                                        Text(
                                            text = label,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isSelected) Color.Black else PureWhite
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(14.dp))
                            Text(text = "2. Select Dish to Inspect", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = SaffronPrimary)
                            Spacer(modifier = Modifier.height(6.dp))

                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                                items(menuItems) { dish ->
                                    val isSelected = selectedDish?.itemId == dish.itemId
                                    Box(
                                        contentAlignment = Alignment.Center,
                                        modifier = Modifier
                                            .height(44.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(if (isSelected) SaffronPrimary else CharcoalSurface)
                                            .border(1.5.dp, if (isSelected) SaffronPrimary else Color(0xFF444444), RoundedCornerShape(8.dp))
                                            .clickable {
                                                hapticEngine.trigger(HapticPattern.CLICK)
                                                selectedDish = dish
                                                runDietaryCheck(dish, selectedDietType)
                                            }
                                            .padding(horizontal = 12.dp)
                                    ) {
                                        Text(
                                            text = dish.nameEnglish,
                                            fontSize = 13.sp,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                            color = if (isSelected) SaffronOnPrimary else PureWhite
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            // Verification Result Card
                            checkResult?.let { res ->
                                val badgeColor = when (res.compliance) {
                                    DietaryCompliance.COMPLIANT -> VegGreen
                                    DietaryCompliance.VIOLATION -> NonVegRed
                                    DietaryCompliance.UNKNOWN_NEEDS_STAFF_CONFIRMATION -> HighContrastYellow
                                }
                                val badgeText = when (res.compliance) {
                                    DietaryCompliance.COMPLIANT -> "✅ SAFE / COMPLIANT"
                                    DietaryCompliance.VIOLATION -> "⛔ DIETARY VIOLATION / ALLERGEN RISK"
                                    DietaryCompliance.UNKNOWN_NEEDS_STAFF_CONFIRMATION -> "⚠️ CONFIRM WITH KITCHEN STAFF"
                                }

                                Card(
                                    colors = CardDefaults.cardColors(containerColor = CharcoalSurface),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .border(2.dp, badgeColor, RoundedCornerShape(12.dp))
                                ) {
                                    Column(modifier = Modifier.padding(16.dp)) {
                                        Text(text = badgeText, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, color = badgeColor)
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Text(text = res.explanation, fontSize = 14.sp, color = PureWhite, lineHeight = 20.sp)

                                        if (res.identifiedIngredients.isNotEmpty()) {
                                            Spacer(modifier = Modifier.height(8.dp))
                                            Text(
                                                text = "Flagged Ingredients: ${res.identifiedIngredients.joinToString(", ")}",
                                                fontSize = 12.sp,
                                                color = HighContrastYellow,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                        }

                                        if (res.needsStaffConfirmation) {
                                            Spacer(modifier = Modifier.height(12.dp))
                                            AccessibleButton(
                                                text = "🔔 Call Waiter to Confirm Preparation",
                                                onClick = {
                                                    hapticEngine.trigger(HapticPattern.CONFIRMATION)
                                                    earconEngine.play(EarconType.ORDER_CONFIRMED)
                                                    onSpeakNarration(
                                                        when (selectedLanguage) {
                                                            "te" -> "దయచేసి వేచి ఉండండి, కిచెన్ సిబ్బందికి సమాచారం పంపాము."
                                                            "hi" -> "कृपया प्रतीक्षा करें, किचन स्टाफ को सूचित किया गया है।"
                                                            else -> "Waiter summoned to verify preparation with the kitchen."
                                                        }
                                                    )
                                                },
                                                modifier = Modifier.fillMaxWidth(),
                                                isPrimary = false
                                            )
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))
                            // Medical Disclaimer Note
                            Text(
                                text = "⚖️ Medical & Allergen Disclaimer: Always inform restaurant staff of life-threatening allergies. Cross-contamination may occur in commercial kitchens.",
                                fontSize = 11.sp,
                                color = SubtitleText,
                                lineHeight = 15.sp
                            )
                        }
                    }

                    DietaryTab.EXPLAIN_SIMPLER -> {
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .verticalScroll(rememberScrollState())
                        ) {
                            Text(text = "1. Cognitive Simplicity Level", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = SaffronPrimary)
                            Spacer(modifier = Modifier.height(6.dp))

                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                                listOf(
                                    3 to "Level 3 (Ultra-Simple)",
                                    2 to "Level 2 (Everyday)",
                                    1 to "Level 1 (Culinary)"
                                ).forEach { (lvl, label) ->
                                    val isSelected = explainLevel == lvl
                                    Box(
                                        contentAlignment = Alignment.Center,
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(44.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(if (isSelected) SaffronPrimary else CharcoalSurface)
                                            .border(1.dp, if (isSelected) SaffronPrimary else Color(0xFF555555), RoundedCornerShape(8.dp))
                                            .clickable {
                                                hapticEngine.trigger(HapticPattern.CLICK)
                                                explainLevel = lvl
                                                selectedDish?.let { updateExplanation(it, lvl) }
                                            }
                                            .padding(horizontal = 4.dp)
                                    ) {
                                        Text(
                                            text = label,
                                            fontSize = 11.sp,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                            color = if (isSelected) SaffronOnPrimary else PureWhite,
                                            textAlign = TextAlign.Center
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(14.dp))
                            Text(text = "2. Select Dish to Explain", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = SaffronPrimary)
                            Spacer(modifier = Modifier.height(6.dp))

                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                                items(menuItems) { dish ->
                                    val isSelected = selectedDish?.itemId == dish.itemId
                                    Box(
                                        contentAlignment = Alignment.Center,
                                        modifier = Modifier
                                            .height(44.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(if (isSelected) SaffronPrimary else CharcoalSurface)
                                            .border(1.5.dp, if (isSelected) SaffronPrimary else Color(0xFF444444), RoundedCornerShape(8.dp))
                                            .clickable {
                                                hapticEngine.trigger(HapticPattern.CLICK)
                                                selectedDish = dish
                                                updateExplanation(dish, explainLevel)
                                            }
                                            .padding(horizontal = 12.dp)
                                    ) {
                                        Text(
                                            text = dish.nameEnglish,
                                            fontSize = 13.sp,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                            color = if (isSelected) SaffronOnPrimary else PureWhite
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            activeExplanation?.let { text ->
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = CharcoalSurface),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .border(1.5.dp, SaffronPrimary, RoundedCornerShape(12.dp))
                                ) {
                                    Column(modifier = Modifier.padding(16.dp)) {
                                        Row(
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Text(
                                                text = "${selectedDish?.nameEnglish} (Level $explainLevel)",
                                                fontSize = 16.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = SaffronPrimary
                                            )
                                            Text(
                                                text = "${text.split(" ").filter { it.isNotBlank() }.size} words",
                                                fontSize = 11.sp,
                                                color = SubtitleText
                                            )
                                        }

                                        Spacer(modifier = Modifier.height(10.dp))
                                        Text(
                                            text = text,
                                            fontSize = 15.sp,
                                            color = PureWhite,
                                            lineHeight = 22.sp
                                        )

                                        Spacer(modifier = Modifier.height(14.dp))
                                        AccessibleButton(
                                            text = "🔊 Read Explanation Aloud",
                                            onClick = {
                                                hapticEngine.trigger(HapticPattern.CONFIRMATION)
                                                onSpeakNarration(text)
                                            },
                                            modifier = Modifier.fillMaxWidth(),
                                            isPrimary = true
                                        )
                                    }
                                }
                            }
                        }
                    }

                    DietaryTab.DISH_COMPARISON -> {
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .verticalScroll(rememberScrollState())
                        ) {
                            Text(text = "Compare Two Dishes (Blind Table Narrator)", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = SaffronPrimary)
                            Spacer(modifier = Modifier.height(10.dp))

                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                                // Dish 1 Picker
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(text = "Dish 1:", fontSize = 12.sp, color = SubtitleText)
                                    Spacer(modifier = Modifier.height(4.dp))
                                    menuItems.take(3).forEach { dish ->
                                        val isSel = compareDish1?.itemId == dish.itemId
                                        Box(
                                            contentAlignment = Alignment.Center,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(36.dp)
                                                .padding(vertical = 2.dp)
                                                .clip(RoundedCornerShape(6.dp))
                                                .background(if (isSel) SaffronPrimary else CharcoalSurface)
                                                .border(1.dp, if (isSel) SaffronPrimary else Color(0xFF444444), RoundedCornerShape(6.dp))
                                                .clickable {
                                                    hapticEngine.trigger(HapticPattern.CLICK)
                                                    compareDish1 = dish
                                                }
                                        ) {
                                            Text(text = dish.nameEnglish, fontSize = 12.sp, color = if (isSel) SaffronOnPrimary else PureWhite)
                                        }
                                    }
                                }

                                // Dish 2 Picker
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(text = "Dish 2:", fontSize = 12.sp, color = SubtitleText)
                                    Spacer(modifier = Modifier.height(4.dp))
                                    menuItems.drop(2).take(3).forEach { dish ->
                                        val isSel = compareDish2?.itemId == dish.itemId
                                        Box(
                                            contentAlignment = Alignment.Center,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(36.dp)
                                                .padding(vertical = 2.dp)
                                                .clip(RoundedCornerShape(6.dp))
                                                .background(if (isSel) SaffronPrimary else CharcoalSurface)
                                                .border(1.dp, if (isSel) SaffronPrimary else Color(0xFF444444), RoundedCornerShape(6.dp))
                                                .clickable {
                                                    hapticEngine.trigger(HapticPattern.CLICK)
                                                    compareDish2 = dish
                                                }
                                        ) {
                                            Text(text = dish.nameEnglish, fontSize = 12.sp, color = if (isSel) SaffronOnPrimary else PureWhite)
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            if (compareDish1 != null && compareDish2 != null) {
                                val table = TableNarrator.generateComparisonTable(listOf(compareDish1!!, compareDish2!!))

                                Card(
                                    colors = CardDefaults.cardColors(containerColor = CharcoalSurface),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .border(1.dp, Color(0xFF444444), RoundedCornerShape(12.dp))
                                ) {
                                    Column(modifier = Modifier.padding(14.dp)) {
                                        Text(text = "📊 Comparative Table", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = SaffronPrimary)
                                        Spacer(modifier = Modifier.height(8.dp))

                                        // Table Header
                                        Row(modifier = Modifier.fillMaxWidth()) {
                                            table.columns.forEach { col ->
                                                Text(text = col, modifier = Modifier.weight(1f), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = HighContrastYellow)
                                            }
                                        }
                                        HorizontalDivider(color = Color(0xFF555555), modifier = Modifier.padding(vertical = 6.dp))

                                        // Table Rows
                                        table.rows.forEach { row ->
                                            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                                                row.forEach { cell ->
                                                    Text(text = cell, modifier = Modifier.weight(1f), fontSize = 12.sp, color = PureWhite)
                                                }
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(14.dp))
                                        AccessibleButton(
                                            text = "🎧 Narrate Table Comparison",
                                            onClick = {
                                                hapticEngine.trigger(HapticPattern.CONFIRMATION)
                                                earconEngine.play(EarconType.ORDER_CONFIRMED)
                                                val narration = TableNarrator.narrateComparison(table, selectedLanguage)
                                                liveCaptionText = narration
                                                onSpeakNarration(narration)
                                            },
                                            modifier = Modifier.fillMaxWidth(),
                                            isPrimary = true
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Bottom Mic Bar for Spoken Queries
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(56.dp)
                            .clip(CircleShape)
                            .background(if (isRecording) NonVegRed else if (isAiProcessing) CharcoalSurfaceVariant else SaffronPrimary)
                            .clickable {
                                if (isRecording) {
                                    hapticEngine.trigger(HapticPattern.CLICK)
                                    earconEngine.play(EarconType.LISTEN_STOP)
                                    audioRecorder.stopRecording()
                                } else {
                                    onStopNarration()
                                    hapticEngine.trigger(HapticPattern.SPEECH_TURN_CUE)
                                    earconEngine.play(EarconType.LISTEN_START)
                                    audioRecorder.startRecording(
                                        onUtteranceComplete = { wavBytes ->
                                            hapticEngine.trigger(HapticPattern.CONFIRMATION)
                                            earconEngine.play(EarconType.LISTEN_STOP)
                                            handleCapturedSpeech(wavBytes)
                                        },
                                        onError = {
                                            isAiProcessing = false
                                            hapticEngine.trigger(HapticPattern.ERROR_ALERT)
                                        }
                                    )
                                }
                            }
                            .semantics {
                                contentDescription = if (isRecording) "Stop listening" else "Speak dietary question"
                            }
                    ) {
                        if (isAiProcessing) {
                            CircularProgressIndicator(color = SaffronPrimary, modifier = Modifier.size(24.dp))
                        } else {
                            Icon(
                                imageVector = if (isRecording) Icons.Default.MicOff else Icons.Default.Mic,
                                contentDescription = null,
                                tint = if (isRecording) PureWhite else SaffronOnPrimary,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                    }

                    Text(
                        text = "Tap mic to ask: 'Is Aloo Gobi Jain?' or 'Explain Dosa at level 3'",
                        fontSize = 12.sp,
                        color = SubtitleText,
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))
            }
        }
    }
}
