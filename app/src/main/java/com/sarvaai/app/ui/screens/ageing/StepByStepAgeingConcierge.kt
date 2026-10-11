package com.sarvaai.app.ui.screens.ageing

import android.content.Context
import android.speech.tts.TextToSpeech
import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sarvaai.app.adapt.verbalizer.NumberVerbalizer
import com.sarvaai.app.audio.earcons.EarconEngine
import com.sarvaai.app.audio.earcons.EarconType
import com.sarvaai.app.audio.haptics.HapticFeedbackEngine
import com.sarvaai.app.audio.haptics.HapticPattern
import com.sarvaai.app.data.dao.CartDao
import com.sarvaai.app.data.entities.CartItemEntity
import com.sarvaai.app.ui.components.DietaryBadge
import com.sarvaai.app.ui.components.NeomorphicLanguageSelector
import com.sarvaai.app.ui.components.neomorphicSurface
import com.sarvaai.app.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.Locale
import java.util.UUID

/**
 * Curated Gentle Dish tailored specifically for seniors (soft texture, mild spices, high nutrition).
 */
data class ConciergeDish(
    val id: String,
    val nameEnglish: String,
    val nameHindi: String,
    val nameTelugu: String,
    val descriptionEnglish: String,
    val descriptionHindi: String,
    val descriptionTelugu: String,
    val pricePaise: Long,
    val isVegetarian: Boolean,
    val spiceLevel: Int, // 1 = Gentle/Mild, 2 = Medium, 3 = Spicy
    val digestionTagEnglish: String,
    val digestionTagHindi: String,
    val digestionTagTelugu: String
) {
    val priceRupees: Long get() = pricePaise / 100

    fun getName(lang: String): String = when (lang.lowercase()) {
        "hi" -> nameHindi
        "te" -> nameTelugu
        else -> nameEnglish
    }

    fun getDescription(lang: String): String = when (lang.lowercase()) {
        "hi" -> descriptionHindi
        "te" -> descriptionTelugu
        else -> descriptionEnglish
    }

    fun getDigestionTag(lang: String): String = when (lang.lowercase()) {
        "hi" -> digestionTagHindi
        "te" -> digestionTagTelugu
        else -> digestionTagEnglish
    }
}

/**
 * Steps in the Ageing Concierge flow.
 */
enum class ConciergeStep(val stepNumber: Int) {
    DIETARY_PREFERENCE(1),
    COMFORT_AND_SPICE(2),
    DISH_SELECTION(3),
    REVIEW_AND_ORDER(4),
    ORDER_CONFIRMED_SUCCESS(5)
}

/**
 * Pre-defined list of gentle, senior-friendly dishes covering both vegetarian and non-vegetarian palates.
 */
val SENIOR_CURATED_DISHES = listOf(
    ConciergeDish(
        id = "dish_idli_sambar",
        nameEnglish = "Soft Steamed Idli Sambar",
        nameHindi = "नरम भाप में पकी इडली सांभर",
        nameTelugu = "మెత్తని ఆవిరి ఇడ్లీ సాంబార్",
        descriptionEnglish = "Steamed fluffy rice-lentil cakes served with mild lentil broth and fresh coconut chutney. Extra soft and effortless to digest.",
        descriptionHindi = "भाप में पकी रुई जैसी नरम इडली, हल्की दाल सांभर और ताज़ा नारियल चटनी। चबाने में आसान और पेट के लिए बहुत हल्की।",
        descriptionTelugu = "ఆవిరితో వండిన దూది లాంటి మెత్తని ఇడ్లీలు, తేలికపాటి సాంబార్ మరియు కొబ్బరి పచ్చడి. జీర్ణానికి ఎంతో తేలిక.",
        pricePaise = 8000L,
        isVegetarian = true,
        spiceLevel = 1,
        digestionTagEnglish = "Very Soft • Easy to Chew",
        digestionTagHindi = "अत्यधिक सुपाच्य • चबाने में आसान",
        digestionTagTelugu = "జీర్ణానికి సులువు • ఎంతో మెత్తగా"
    ),
    ConciergeDish(
        id = "dish_moong_khichdi",
        nameEnglish = "Moong Dal Khichdi & Curd",
        nameHindi = "मूंग दाल खिचड़ी और ताज़ा दही",
        nameTelugu = "పెసరపప్పు కిచిడీ మరియు పెరుగు",
        descriptionEnglish = "Slow-cooked golden lentils and rice tempered with pure desi ghee, cumin, and served with comforting fresh curd.",
        descriptionHindi = "शुद्ध देसी घी और जीरा तड़के वाली पौष्टिक मूंग दाल खिचड़ी, साथ में ठंडा ताज़ा दही। पेट को तुरंत ठंडक देती है।",
        descriptionTelugu = "స్వచ్ఛమైన నెయ్యి, జీలకర్ర తాలింపుతో చేసిన బలవర్ధకమైన కిచిడీ మరియు చల్లని తాజా పెరుగు. కడుపుకు ఎంతో హాయి.",
        pricePaise = 12000L,
        isVegetarian = true,
        spiceLevel = 1,
        digestionTagEnglish = "Pure Desi Ghee • Comfort Meal",
        digestionTagHindi = "देसी घी • पेट के लिए सुपाच्य",
        digestionTagTelugu = "స్వచ్ఛమైన నెయ్యి • సౌకర్యవంతమైన ఆహారం"
    ),
    ConciergeDish(
        id = "dish_masala_dosa",
        nameEnglish = "Crispy Golden Masala Dosa",
        nameHindi = "स्वादिष्ट मसाला डोसा",
        nameTelugu = "రుచికరమైన మసాలా దోశ",
        descriptionEnglish = "Crisp fermented rice crepe stuffed with mild, lightly spiced mashed potatoes, accompanied by fresh sambar.",
        descriptionHindi = "हल्के आलू मसाले और गरमा-गरम सांभर के साथ सुनहरी कुरकुरी डोसा। मध्यम तीखेपन के साथ संतुलित स्वाद।",
        descriptionTelugu = "బంగాళాదుంప కూర మరియు వేడి సాంబార్‌తో బంగారు రంగు దోశ. సమతుల్యమైన మసాలాలు.",
        pricePaise = 9000L,
        isVegetarian = true,
        spiceLevel = 2,
        digestionTagEnglish = "Medium Spice • Fresh Potato Masala",
        digestionTagHindi = "मध्यम तीखा • ताज़ा आलू मसाला",
        digestionTagTelugu = "మధ్యస్థ కారం • ఆలూ మసాలా"
    ),
    ConciergeDish(
        id = "dish_dal_makhani",
        nameEnglish = "Creamy Dal Makhani & Soft Phulkas",
        nameHindi = "दाल मखनी और 2 नरम फुल्के",
        nameTelugu = "దాల్ మఖ్నీ మరియు రోటీలు",
        descriptionEnglish = "Slow-simmered whole black lentils cooked gently overnight with butter, served with two tender whole wheat phulkas.",
        descriptionHindi = "धीमी आंच पर पकी मलाईदार दाल मखनी, साथ में दो गरम व बेहद नरम गेहूं के फुल्के।",
        descriptionTelugu = "వెన్నతో నెమ్మదిగా వండిన దాల్ మఖ్నీ మరియు రెండు వేడి, మృదువైన గోధుమ రోటీలు.",
        pricePaise = 16000L,
        isVegetarian = true,
        spiceLevel = 2,
        digestionTagEnglish = "Slow Cooked • 100% Whole Wheat",
        digestionTagHindi = "धीमी आंच पर तैयार • संपूर्ण गेहूं",
        digestionTagTelugu = "నెమ్మదిగా వండినది • గోధుమ రోటీలు"
    ),
    ConciergeDish(
        id = "dish_chicken_soup",
        nameEnglish = "Mild Nourishing Chicken Soup & Rice",
        nameHindi = "पौष्टिक माइल्ड चिकन सूप और चावल",
        nameTelugu = "చికెన్ సూప్ మరియు అన్నం",
        descriptionEnglish = "Clear, warm bone broth with soft shredded chicken pieces, hint of fresh ginger, served with easy-to-swallow steamed rice.",
        descriptionHindi = "ताज़ा अदरक और नरम चिकन के रेशों से बना पौष्टिक व हल्का सूप, साथ में गरमा-गरम उबले चावल।",
        descriptionTelugu = "అల్లం సువాసనతో కూడిన బలవర్ధకమైన చికెన్ సూప్ మరియు మెత్తని అన్నం. గొంతుకు, కడుపుకు ఎంతో హాయి.",
        pricePaise = 16000L,
        isVegetarian = false,
        spiceLevel = 1,
        digestionTagEnglish = "Clear Bone Broth • Gentle & Soothing",
        digestionTagHindi = "हल्का शोरबा • गले के लिए आरामदायक",
        digestionTagTelugu = "తేలికపాటి సూప్ • సులభంగా మింగవచ్చు"
    ),
    ConciergeDish(
        id = "dish_butter_chicken",
        nameEnglish = "Boneless Butter Chicken & Soft Naan",
        nameHindi = "बोनलेस बटर चिकन और नरम नान",
        nameTelugu = "బోన్‌లెస్ బటర్ చికెన్ మరియు నాన్",
        descriptionEnglish = "Tender boneless chicken morsels simmered in a velvety mild tomato-cream gravy, served with soft oven-baked bread.",
        descriptionHindi = "बिना हड्डी का मुलायम चिकन, मखमली टमाटर और मलाईदार ग्रेवी में तैयार, साथ में ताज़ा नरम नान।",
        descriptionTelugu = "ఎముకలు లేని మెత్తటి చికెన్ ముక్కలు, టమోటా క్రీమ్ గ్రేవీ మరియు మృదువైన నాన్.",
        pricePaise = 24000L,
        isVegetarian = false,
        spiceLevel = 1,
        digestionTagEnglish = "100% Boneless • Very Tender Morsels",
        digestionTagHindi = "बिना हड्डी • बेहद मुलायम टुकड़े",
        digestionTagTelugu = "బోన్‌లెస్ • ఎంతో మెత్తటి చికెన్"
    ),
    ConciergeDish(
        id = "dish_pepper_chicken",
        nameEnglish = "Andhra Pepper Chicken & Steamed Rice",
        nameHindi = "आंध्रा काली मिर्च चिकन और चावल",
        nameTelugu = "ఆంధ్రా పెప్పర్ చికెన్ మరియు అన్నం",
        descriptionEnglish = "Succulent chicken sautéed with fresh crushed black pepper, aromatic curry leaves, and warm steamed rice.",
        descriptionHindi = "काली मिर्च और ताज़े करी पत्ते के साथ भुना हुआ मसालेदार चिकन, गरमा-गरम चावल के साथ।",
        descriptionTelugu = "మిరియాల పొడి మరియు కరివేపాకుతో వేయించిన చికెన్, వేడి వేడి అన్నంతో.",
        pricePaise = 22000L,
        isVegetarian = false,
        spiceLevel = 3,
        digestionTagEnglish = "Crushed Black Pepper • Warming Spice",
        digestionTagHindi = "काली मिर्च • स्वादिष्ट तीखापन",
        digestionTagTelugu = "మిరియాల ఘాటు • రుచికరమైనది"
    ),
    ConciergeDish(
        id = "dish_kadai_paneer",
        nameEnglish = "Spicy Kadai Paneer & Garlic Naan",
        nameHindi = "कड़ाही पनीर और गार्लिक नान",
        nameTelugu = "కడాయి పనీర్ మరియు గార్లిక్ నాన్",
        descriptionEnglish = "Fresh cottage cheese cubes tossed with bell peppers and roasted coriander spices, served with warm garlic naan.",
        descriptionHindi = "ताज़ी शिमला मिर्च और भुने मसालों के साथ तीखा पनीर, साथ में सुगंधित गार्लिक नान।",
        descriptionTelugu = "క్యాప్సికమ్ మరియు వేయించిన మసాలాలతో ఘాటైన పనీర్ కూర, గార్లిక్ నాన్‌తో.",
        pricePaise = 21000L,
        isVegetarian = true,
        spiceLevel = 3,
        digestionTagEnglish = "Rich Gravy • Bold Spice",
        digestionTagHindi = "तेज मसाले • तीखा स्वाद",
        digestionTagTelugu = "గాఢమైన మసాలా • ఘాటైన రుచి"
    )
)

/**
 * StepByStepAgeingConcierge
 *
 * Dedicated senior-friendly guided ordering screen addressing ageing barriers:
 * - Large high-contrast typography (22sp-24sp bold)
 * - Huge tactile touch targets (64dp height buttons)
 * - Progressive bite-sized 4-step wizard
 * - Integrated 0.8x slow rate TTS guide button & repetition controls
 * - Multilingual Indic localization (English, हिन्दी, తెలుగు)
 * - Neomorphic White high-contrast tactile design
 * - Immediate cart persistence, haptic feedback, and success earcon
 */
@Composable
fun StepByStepAgeingConcierge(
    cartDao: CartDao,
    hapticEngine: HapticFeedbackEngine,
    earconEngine: EarconEngine,
    selectedLanguage: String = "en",
    onSpeakNarration: ((String) -> Unit)? = null,
    onSpeakSlowly: ((String) -> Unit)? = null,
    onStopNarration: (() -> Unit)? = null,
    onNavigateToCart: (() -> Unit)? = null,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()

    var currentLang by remember { mutableStateOf(selectedLanguage.ifBlank { "en" }) }
    var currentStep by remember { mutableStateOf(ConciergeStep.DIETARY_PREFERENCE) }

    // User Selections across steps
    var isPureVegSelected by remember { mutableStateOf<Boolean?>(null) } // true = Pure Veg, false = Non-Veg
    var selectedSpiceLevel by remember { mutableStateOf<Int?>(1) } // 1 = Gentle, 2 = Medium, 3 = Spicy
    var selectedDish by remember { mutableStateOf<ConciergeDish?>(null) }
    var orderQuantity by remember { mutableStateOf(1) }
    var confirmedCartItem by remember { mutableStateOf<CartItemEntity?>(null) }

    // Native Local Text-To-Speech fallback for reliable 0.8x slow rate audio
    var nativeTts by remember { mutableStateOf<TextToSpeech?>(null) }
    var isNativeTtsReady by remember { mutableStateOf(false) }

    DisposableEffect(context) {
        var ttsInstance: TextToSpeech? = null
        try {
            ttsInstance = TextToSpeech(context) { status ->
                if (status == TextToSpeech.SUCCESS) {
                    ttsInstance?.setSpeechRate(0.8f) // 0.8x slow comforting rate
                    val loc = when (currentLang.lowercase()) {
                        "te" -> Locale("te", "IN")
                        "hi" -> Locale("hi", "IN")
                        else -> Locale.ENGLISH
                    }
                    ttsInstance?.language = loc
                    nativeTts = ttsInstance
                    isNativeTtsReady = true
                }
            }
        } catch (t: Throwable) {
            Log.w("AgeingConcierge", "TextToSpeech init warning: ${t.message}")
        }

        onDispose {
            try {
                ttsInstance?.stop()
                ttsInstance?.shutdown()
            } catch (_: Throwable) {}
            onStopNarration?.invoke()
        }
    }

    // Function to speak aloud instructions slowly (0.8x)
    fun speakGuide(text: String, rateSlow: Boolean = true) {
        val clean = text.trim()
        if (clean.isBlank()) return

        hapticEngine.trigger(HapticPattern.CLICK)

        if (onSpeakSlowly != null && rateSlow) {
            onSpeakSlowly.invoke(clean)
        } else if (onSpeakNarration != null) {
            onSpeakNarration.invoke(clean)
        } else {
            // Local native Android TTS fallback
            nativeTts?.let { tts ->
                tts.setSpeechRate(if (rateSlow) 0.8f else 1.0f)
                val loc = when (currentLang.lowercase()) {
                    "te" -> Locale("te", "IN")
                    "hi" -> Locale("hi", "IN")
                    else -> Locale.ENGLISH
                }
                tts.language = loc
                tts.speak(clean, TextToSpeech.QUEUE_FLUSH, null, "concierge_tts_${System.currentTimeMillis()}")
            }
        }
    }

    // Helper text for each step guide
    fun getStepSpokenGuide(step: ConciergeStep): String {
        return when (step) {
            ConciergeStep.DIETARY_PREFERENCE -> when (currentLang.lowercase()) {
                "hi" -> "पहला कदम: आज आप किस प्रकार का भोजन पसंद करेंगे? कृपया शुद्ध शाकाहारी या मांसाहारी में से एक चुनें।"
                "te" -> "మొదటి దశ: ఈ రోజు మీరు ఏ రకమైన ఆహారాన్ని ఇష్టపడతారు? దయచేసి శాకాహారం లేదా మాంసాహారం ఎంచుకోండి."
                else -> "Step one: What type of food would you like today? Please choose Pure Vegetarian or Non-Vegetarian."
            }
            ConciergeStep.COMFORT_AND_SPICE -> when (currentLang.lowercase()) {
                "hi" -> "दूसरा कदम: आप अपना खाना कितना तीखा पसंद करेंगे? वरिष्ठ नागरिकों के लिए हम हल्का व सादा भोजन सुझाते हैं जो पचने में बहुत आसान होता है।"
                "te" -> "రెండవ దశ: మీ ఆహారం ఎంత కారంగా ఉండాలనుకుంటున్నారు? సీనియర్లకు మేము తేలికపాటి మరియు మృదువైన ఆహారాన్ని సిఫార్సు చేస్తున్నాము."
                else -> "Step two: How spicy would you like your food? We recommend Gentle and Mild food for seniors, which is easy to digest."
            }
            ConciergeStep.DISH_SELECTION -> {
                val dishA = selectedDish?.getName(currentLang) ?: "व्यंजन"
                when (currentLang.lowercase()) {
                    "hi" -> "तीसरा कदम: अपना पसंदीदा सुपाच्य भोजन चुनें। हमने आपके लिए दो बहुत हल्के व नरम व्यंजन तैयार किए हैं। किसी एक व्यंजन पर टैप करें।"
                    "te" -> "మూడవ దశ: మీ సౌకర్యవంతమైన భోజనాన్ని ఎంచుకోండి. మీ కోసం రెండు ఎంతో మెత్తని, రుచికరమైన వంటకాలను ఉంచాము."
                    else -> "Step three: Choose your comforting meal. We have prepared two gentle, easy-to-chew dishes tailored for you. Tap Select This Dish to proceed."
                }
            }
            ConciergeStep.REVIEW_AND_ORDER -> {
                val dish = selectedDish
                if (dish != null) {
                    val dishName = dish.getName(currentLang)
                    val priceVerbal = NumberVerbalizer.verbalizePrice(dish.priceRupees * orderQuantity, currentLang)
                    when (currentLang.lowercase()) {
                        "hi" -> "चौथा कदम: अपने ऑर्डर की समीक्षा करें। आपने चुना है ${dish.nameHindi}, कुल राशि $priceVerbal। ऑर्डर देने के लिए नीचे दिए गए हरे बटन को दबाएं।"
                        "te" -> "నాల్గవ దశ: మీ ఆర్డర్‌ను సరిచూసుకోండి. మీరు ఎంచుకున్న వంటకం ${dish.nameTelugu}, మొత్తం ధర $priceVerbal. ఆర్డర్ ఖరారు చేయడానికి క్రింది ఆకుపచ్చ బటన్‌ను నొక్కండి."
                        else -> "Step four: Review and place your order. You have selected ${dish.nameEnglish}, total amount $priceVerbal. Press the large green button below to place your order."
                    }
                } else {
                    when (currentLang.lowercase()) {
                        "hi" -> "कृपया अपना व्यंजन चुनें।"
                        "te" -> "దయచేసి మీ వంటకాన్ని ఎంచుకోండి."
                        else -> "Please review your selected dish."
                    }
                }
            }
            ConciergeStep.ORDER_CONFIRMED_SUCCESS -> {
                val dishName = selectedDish?.getName(currentLang) ?: "Order"
                when (currentLang.lowercase()) {
                    "hi" -> "$dishName के लिए आपका ऑर्डर कन्फर्म हो गया है! रसोई में इसे नरमी और प्यार से तैयार किया जा रहा है।"
                    "te" -> "$dishName కోసం మీ ఆర్డర్ ఖరారైంది! వంటగదిలో ఇది ఎంతో మృదువుగా సిద్ధం చేయబడుతోంది."
                    else -> "Your order for $dishName is confirmed! The kitchen is preparing it gently."
                }
            }
        }
    }

    // Curated dishes dynamically filtered based on Step 1 (Veg/Non-Veg) and Step 2 (Spice level)
    val curatedDishes = remember(isPureVegSelected, selectedSpiceLevel) {
        val isVeg = isPureVegSelected ?: true
        val targetSpice = selectedSpiceLevel ?: 1

        val matching = SENIOR_CURATED_DISHES.filter { it.isVegetarian == isVeg }
        val exactSpiceMatches = matching.filter { it.spiceLevel == targetSpice }

        if (exactSpiceMatches.size >= 2) {
            exactSpiceMatches.take(2)
        } else {
            // Guarantee exactly 2 senior-comfort dishes
            val combined = (exactSpiceMatches + matching.filter { it.spiceLevel != targetSpice }).distinctBy { it.id }
            combined.take(2)
        }
    }

    // Auto-select first matching dish if none selected yet or changed filters
    LaunchedEffect(curatedDishes) {
        if (selectedDish == null || curatedDishes.none { it.id == selectedDish?.id }) {
            selectedDish = curatedDishes.firstOrNull()
        }
    }

    // Safe Back Navigation
    BackHandler {
        onStopNarration?.invoke()
        if (currentStep.stepNumber > 1 && currentStep != ConciergeStep.ORDER_CONFIRMED_SUCCESS) {
            currentStep = ConciergeStep.entries[currentStep.ordinal - 1]
        } else {
            onClose()
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(NeoBackground)
    ) {
        // ====================================================================
        // Accessible Senior Header Bar
        // ====================================================================
        SeniorHeaderBar(
            currentLang = currentLang,
            onClose = {
                hapticEngine.trigger(HapticPattern.CLICK)
                onStopNarration?.invoke()
                onClose()
            },
            onHearGuideSlowly = {
                val guide = getStepSpokenGuide(currentStep)
                speakGuide(guide, rateSlow = true)
            }
        )

        // ====================================================================
        // Language Selector Switcher (English, हिन्दी, తెలుగు)
        // ====================================================================
        NeomorphicLanguageSelector(
            selectedLanguage = currentLang,
            onLanguageSelected = { lang ->
                hapticEngine.trigger(HapticPattern.CLICK)
                currentLang = lang
                // Announce language change
                val announcement = when (lang) {
                    "hi" -> "हिंदी भाषा चुनी गई"
                    "te" -> "తెలుగు భాష ఎంచుకోబడింది"
                    else -> "English language selected"
                }
                speakGuide(announcement, rateSlow = true)
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp)
        )

        // ====================================================================
        // Step Progress Indicator Bar (Steps 1 to 4)
        // ====================================================================
        if (currentStep != ConciergeStep.ORDER_CONFIRMED_SUCCESS) {
            SeniorStepProgressIndicator(
                currentStep = currentStep,
                currentLang = currentLang,
                onStepClicked = { target ->
                    // Allow jumping back to previously completed steps
                    if (target.stepNumber <= currentStep.stepNumber) {
                        hapticEngine.trigger(HapticPattern.CLICK)
                        currentStep = target
                        speakGuide(getStepSpokenGuide(target), rateSlow = true)
                    }
                }
            )
        }

        // ====================================================================
        // Main Step Content Area (Scrollable with large tactile widgets)
        // ====================================================================
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                when (currentStep) {
                    ConciergeStep.DIETARY_PREFERENCE -> {
                        Step1DietaryPreference(
                            currentLang = currentLang,
                            selectedDiet = isPureVegSelected,
                            hapticEngine = hapticEngine,
                            onDietSelected = { isVeg ->
                                isPureVegSelected = isVeg
                                val text = if (isVeg) {
                                    when (currentLang) {
                                        "hi" -> "शुद्ध शाकाहारी चुना गया"
                                        "te" -> "శాకాహారం ఎంచుకోబడింది"
                                        else -> "Pure Vegetarian selected"
                                    }
                                } else {
                                    when (currentLang) {
                                        "hi" -> "मांसाहारी चुना गया"
                                        "te" -> "మాంసాహారం ఎంచుకోబడింది"
                                        else -> "Non-Vegetarian selected"
                                    }
                                }
                                speakGuide(text, rateSlow = true)
                            }
                        )
                    }

                    ConciergeStep.COMFORT_AND_SPICE -> {
                        Step2ComfortAndSpice(
                            currentLang = currentLang,
                            selectedSpice = selectedSpiceLevel ?: 1,
                            hapticEngine = hapticEngine,
                            onSpiceSelected = { spice ->
                                selectedSpiceLevel = spice
                                val text = when (spice) {
                                    1 -> when (currentLang) {
                                        "hi" -> "हल्का व सादा तीखापन चुना गया, यह पेट के लिए बहुत आरामदायक है"
                                        "te" -> "మృదువైన తక్కువ కారం ఎంచుకోబడింది"
                                        else -> "Gentle and Mild selected, recommended for comfort"
                                    }
                                    2 -> when (currentLang) {
                                        "hi" -> "मध्यम तीखा चुना गया"
                                        "te" -> "మధ్యస్థ కారం ఎంచుకోబడింది"
                                        else -> "Medium Spice selected"
                                    }
                                    else -> when (currentLang) {
                                        "hi" -> "तीखा भोजन चुना गया"
                                        "te" -> "ఎక్కువ కారం ఎంచుకోబడింది"
                                        else -> "Spicy food selected"
                                    }
                                }
                                speakGuide(text, rateSlow = true)
                            }
                        )
                    }

                    ConciergeStep.DISH_SELECTION -> {
                        Step3GentleDishSelection(
                            currentLang = currentLang,
                            dishes = curatedDishes,
                            selectedDish = selectedDish,
                            hapticEngine = hapticEngine,
                            onDishSelected = { dish ->
                                selectedDish = dish
                                val priceWords = NumberVerbalizer.verbalizePrice(dish.priceRupees, currentLang)
                                val announce = "${dish.getName(currentLang)}. $priceWords."
                                speakGuide(announce, rateSlow = true)
                            }
                        )
                    }

                    ConciergeStep.REVIEW_AND_ORDER -> {
                        Step4ReviewAndPlaceOrder(
                            currentLang = currentLang,
                            dish = selectedDish ?: curatedDishes.first(),
                            quantity = orderQuantity,
                            hapticEngine = hapticEngine,
                            onQuantityChanged = { newQty ->
                                if (newQty in 1..9) {
                                    orderQuantity = newQty
                                    val qtyWords = NumberVerbalizer.verbalizeQuantity(newQty, currentLang)
                                    speakGuide(qtyWords, rateSlow = true)
                                }
                            },
                            onConfirmOrder = {
                                val dishToOrder = selectedDish ?: curatedDishes.first()
                                val cartItem = CartItemEntity(
                                    cartItemId = UUID.randomUUID().toString(),
                                    itemId = dishToOrder.id,
                                    name = dishToOrder.getName(currentLang),
                                    unitPricePaise = dishToOrder.pricePaise,
                                    quantity = orderQuantity,
                                    specialNotes = "Senior Care: Extra soft, mild seasoning, gentle preparation",
                                    addedTimestamp = System.currentTimeMillis()
                                )

                                scope.launch(Dispatchers.IO) {
                                    cartDao.insertOrUpdate(cartItem)
                                }

                                confirmedCartItem = cartItem
                                earconEngine.play(EarconType.ORDER_CONFIRMED)
                                hapticEngine.trigger(HapticPattern.CONFIRMATION)

                                val confirmationText = when (currentLang.lowercase()) {
                                    "hi" -> "${dishToOrder.nameHindi} के लिए आपका ऑर्डर कन्फर्म हो गया है! रसोई में इसे नरमी और प्यार से तैयार किया जा रहा है।"
                                    "te" -> "${dishToOrder.nameTelugu} కోసం మీ ఆర్డర్ ఖరారైంది! వంటగదిలో ఇది ఎంతో మృదువుగా సిద్ధం చేయబడుతోంది."
                                    else -> "Your order for ${dishToOrder.nameEnglish} is confirmed! The kitchen is preparing it gently."
                                }
                                speakGuide(confirmationText, rateSlow = true)
                                currentStep = ConciergeStep.ORDER_CONFIRMED_SUCCESS
                            }
                        )
                    }

                    ConciergeStep.ORDER_CONFIRMED_SUCCESS -> {
                        Step5OrderConfirmedSuccess(
                            currentLang = currentLang,
                            cartItem = confirmedCartItem,
                            hapticEngine = hapticEngine,
                            onViewCart = {
                                hapticEngine.trigger(HapticPattern.CLICK)
                                onNavigateToCart?.invoke() ?: onClose()
                            },
                            onOrderAnother = {
                                hapticEngine.trigger(HapticPattern.CLICK)
                                isPureVegSelected = null
                                selectedSpiceLevel = 1
                                selectedDish = null
                                orderQuantity = 1
                                confirmedCartItem = null
                                currentStep = ConciergeStep.DIETARY_PREFERENCE
                                speakGuide(getStepSpokenGuide(ConciergeStep.DIETARY_PREFERENCE), rateSlow = true)
                            },
                            onClose = onClose
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
            }
        }

        // ====================================================================
        // Bottom Navigation Bar with tactile navigation controls
        // ====================================================================
        if (currentStep != ConciergeStep.ORDER_CONFIRMED_SUCCESS) {
            SeniorBottomNavigationBar(
                currentStep = currentStep,
                currentLang = currentLang,
                canProceed = when (currentStep) {
                    ConciergeStep.DIETARY_PREFERENCE -> isPureVegSelected != null
                    ConciergeStep.COMFORT_AND_SPICE -> selectedSpiceLevel != null
                    ConciergeStep.DISH_SELECTION -> selectedDish != null
                    ConciergeStep.REVIEW_AND_ORDER -> selectedDish != null
                    else -> true
                },
                onBack = {
                    hapticEngine.trigger(HapticPattern.CLICK)
                    if (currentStep.stepNumber > 1) {
                        currentStep = ConciergeStep.entries[currentStep.ordinal - 1]
                        speakGuide(getStepSpokenGuide(currentStep), rateSlow = true)
                    } else {
                        onClose()
                    }
                },
                onNext = {
                    hapticEngine.trigger(HapticPattern.CLICK)
                    if (currentStep.stepNumber < ConciergeStep.REVIEW_AND_ORDER.stepNumber) {
                        currentStep = ConciergeStep.entries[currentStep.ordinal + 1]
                        speakGuide(getStepSpokenGuide(currentStep), rateSlow = true)
                    }
                },
                onSpeakAgainSlowly = {
                    val guide = getStepSpokenGuide(currentStep)
                    speakGuide(guide, rateSlow = true)
                }
            )
        }
    }
}

/**
 * Top Header bar for senior assistance with large bold typography and 0.8x speech button.
 */
@Composable
private fun SeniorHeaderBar(
    currentLang: String,
    onClose: () -> Unit,
    onHearGuideSlowly: () -> Unit
) {
    val titleText = when (currentLang.lowercase()) {
        "hi" -> "वरिष्ठ नागरिक सहायता • सरल ऑर्डर"
        "te" -> "సీనియర్ సహాయం • దశలవారీ ఆర్డరింగ్"
        else -> "Senior Assistance • Step-by-Step Ordering"
    }

    val hearGuideLabel = when (currentLang.lowercase()) {
        "hi" -> "गाइड सुनें (0.8x)"
        "te" -> "గైడ్ వినండి (0.8x)"
        else -> "Hear Guide (0.8x)"
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(NeoSurface)
            .border(width = 1.dp, color = NeoBorder)
            .padding(horizontal = 16.dp, vertical = 14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(46.dp)
                        .clip(CircleShape)
                        .background(NeoPrimaryContainer)
                ) {
                    Icon(
                        imageVector = Icons.Default.Hearing,
                        contentDescription = null,
                        tint = NeoPrimary,
                        modifier = Modifier.size(26.dp)
                    )
                }

                Text(
                    text = titleText,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = NeoTextPrimary,
                    lineHeight = 24.sp,
                    modifier = Modifier.semantics { heading() }
                )
            }

            // Close Button (50dp touch target)
            val closeInteraction = remember { MutableInteractionSource() }
            val isClosePressed by closeInteraction.collectIsPressedAsState()
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(50.dp)
                    .clip(CircleShape)
                    .background(if (isClosePressed) NeoSurfacePressed else NeoSurface)
                    .border(1.5.dp, NeoBorder, CircleShape)
                    .clickable(
                        interactionSource = closeInteraction,
                        indication = null,
                        onClick = onClose
                    )
                    .semantics {
                        contentDescription = "Close Senior Ordering Guide"
                        role = Role.Button
                    }
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = null,
                    tint = NeoTextPrimary,
                    modifier = Modifier.size(28.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // "Hear Guide (0.8x)" TTS tactile button
        val guideInteraction = remember { MutableInteractionSource() }
        val isGuidePressed by guideInteraction.collectIsPressedAsState()
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .neomorphicSurface(
                    shape = RoundedCornerShape(12.dp),
                    surfaceColor = if (isGuidePressed) NeoPrimary.copy(alpha = 0.1f) else NeoSurface,
                    borderColor = NeoPrimary,
                    borderWidth = 1.5.dp,
                    elevation = 3.dp,
                    isPressed = isGuidePressed,
                    cornerRadius = 12.dp
                )
                .clickable(
                    interactionSource = guideInteraction,
                    indication = null,
                    onClick = onHearGuideSlowly
                )
                .padding(horizontal = 14.dp)
                .semantics {
                    contentDescription = "$hearGuideLabel. Speaks instructions slowly at 0.8x speed."
                    role = Role.Button
                }
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                    contentDescription = null,
                    tint = NeoPrimary,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = hearGuideLabel,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = NeoPrimary
                )
            }
        }
    }
}

/**
 * Step progress tracker (Steps 1 of 4) with high contrast indicators.
 */
@Composable
private fun SeniorStepProgressIndicator(
    currentStep: ConciergeStep,
    currentLang: String,
    onStepClicked: (ConciergeStep) -> Unit
) {
    val steps = listOf(
        ConciergeStep.DIETARY_PREFERENCE,
        ConciergeStep.COMFORT_AND_SPICE,
        ConciergeStep.DISH_SELECTION,
        ConciergeStep.REVIEW_AND_ORDER
    )

    val stepLabel = when (currentLang.lowercase()) {
        "hi" -> "चरण ${currentStep.stepNumber} / 4"
        "te" -> "దశ ${currentStep.stepNumber} / 4"
        else -> "Step ${currentStep.stepNumber} of 4"
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stepLabel,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = NeoPrimary
            )
            Text(
                text = when (currentStep) {
                    ConciergeStep.DIETARY_PREFERENCE -> when (currentLang) {
                        "hi" -> "भोजन प्रकार"
                        "te" -> "ఆహార ప్రాధాన్యత"
                        else -> "Food Type"
                    }
                    ConciergeStep.COMFORT_AND_SPICE -> when (currentLang) {
                        "hi" -> "तीखापन"
                        "te" -> "కారం & సౌకర్యం"
                        else -> "Spice & Comfort"
                    }
                    ConciergeStep.DISH_SELECTION -> when (currentLang) {
                        "hi" -> "व्यंजन चयन"
                        "te" -> "వంటకం ఎంపిక"
                        else -> "Dish Selection"
                    }
                    ConciergeStep.REVIEW_AND_ORDER -> when (currentLang) {
                        "hi" -> "समीक्षा और ऑर्डर"
                        "te" -> "ఆర్డర్ సమీక్ష"
                        else -> "Review & Order"
                    }
                    else -> ""
                },
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = NeoTextSecondary
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            steps.forEach { step ->
                val isActive = step == currentStep
                val isCompleted = step.stepNumber < currentStep.stepNumber

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(
                            when {
                                isActive -> NeoPrimary
                                isCompleted -> VegGreen
                                else -> NeoBorder
                            }
                        )
                        .clickable { onStepClicked(step) }
                )
            }
        }
    }
}

/**
 * Step 1: Dietary Preference with 64dp high-contrast touch buttons and Indic translations.
 */
@Composable
private fun Step1DietaryPreference(
    currentLang: String,
    selectedDiet: Boolean?,
    hapticEngine: HapticFeedbackEngine,
    onDietSelected: (Boolean) -> Unit
) {
    val questionTitle = when (currentLang.lowercase()) {
        "hi" -> "आज आप किस प्रकार का भोजन पसंद करेंगे?"
        "te" -> "ఈ రోజు మీరు ఏ రకమైన ఆహారాన్ని ఇష్టపడతారు?"
        else -> "What type of food would you like today?"
    }

    val subtitle = when (currentLang.lowercase()) {
        "hi" -> "कृपया अपनी पसंद चुनें। हम उसी अनुसार सुपाच्य भोजन दिखाएंगे।"
        "te" -> "దయచేసి మీ ఎంపికను తాకండి. మీకు అనువైన వంటకాలను మాత్రమే చూపిస్తాము."
        else -> "Please touch your choice below. We will show gentle dishes matched for you."
    }

    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = questionTitle,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            color = NeoTextPrimary,
            lineHeight = 28.sp,
            modifier = Modifier.semantics { heading() }
        )

        Text(
            text = subtitle,
            fontSize = 16.sp,
            fontWeight = FontWeight.Normal,
            color = NeoTextSecondary,
            lineHeight = 22.sp
        )

        Spacer(modifier = Modifier.height(4.dp))

        // Large 64dp Option 1: Pure Vegetarian
        SeniorOptionButton(
            title = "Pure Vegetarian",
            indicSubtitle = when (currentLang.lowercase()) {
                "te" -> "శాకాహారం"
                else -> "शाकाहारी"
            },
            secondaryIndic = if (currentLang.lowercase() != "te") "శాకాహారం" else "शाकाहारी",
            isSelected = selectedDiet == true,
            leadingBadge = {
                DietaryBadge(isVegetarian = true)
            },
            accentColor = VegGreen,
            onClick = {
                hapticEngine.trigger(HapticPattern.CLICK)
                onDietSelected(true)
            }
        )

        // Large 64dp Option 2: Non-Vegetarian
        SeniorOptionButton(
            title = "Non-Vegetarian",
            indicSubtitle = when (currentLang.lowercase()) {
                "te" -> "మాంసాహారం"
                else -> "मांसाहारी"
            },
            secondaryIndic = if (currentLang.lowercase() != "te") "మాంసాహారం" else "मांसाहारी",
            isSelected = selectedDiet == false,
            leadingBadge = {
                DietaryBadge(isVegetarian = false)
            },
            accentColor = NonVegRed,
            onClick = {
                hapticEngine.trigger(HapticPattern.CLICK)
                onDietSelected(false)
            }
        )
    }
}

/**
 * Step 2: Comfort & Spice selection with large 64dp buttons and "Recommended for seniors" badge.
 */
@Composable
private fun Step2ComfortAndSpice(
    currentLang: String,
    selectedSpice: Int,
    hapticEngine: HapticFeedbackEngine,
    onSpiceSelected: (Int) -> Unit
) {
    val questionTitle = when (currentLang.lowercase()) {
        "hi" -> "आप अपना खाना कितना तीखा पसंद करेंगे?"
        "te" -> "మీ ఆహారం ఎంత కారంగా ఉండాలనుకుంటున్నారు?"
        else -> "How spicy would you like your food?"
    }

    val subtitle = when (currentLang.lowercase()) {
        "hi" -> "वरिष्ठ नागरिकों के लिए हम हल्का व सुपाच्य भोजन लेने की सलाह देते हैं।"
        "te" -> "జీర్ణ సమస్యలు రాకుండా తేలికపాటి, మృదువైన ఆహారాన్ని సిఫార్సు చేస్తున్నాము."
        else -> "We recommend gentle and mild food prepared with soothing spices for easy digestion."
    }

    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = questionTitle,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            color = NeoTextPrimary,
            lineHeight = 28.sp,
            modifier = Modifier.semantics { heading() }
        )

        Text(
            text = subtitle,
            fontSize = 16.sp,
            fontWeight = FontWeight.Normal,
            color = NeoTextSecondary,
            lineHeight = 22.sp
        )

        Spacer(modifier = Modifier.height(4.dp))

        // Option 1: Gentle & Mild (Recommended for seniors)
        SeniorOptionButton(
            title = when (currentLang.lowercase()) {
                "hi" -> "हल्का व सादा (वरिष्ठ नागरिकों के लिए उत्तम)"
                "te" -> "మృదువైనది & తక్కువ కారం (సీనియర్లకు సిఫార్సు)"
                else -> "Gentle & Mild (Recommended for seniors)"
            },
            indicSubtitle = when (currentLang.lowercase()) {
                "hi" -> "पेट के लिए बहुत आरामदायक • सुपाच्य"
                "te" -> "కడుపుకు హాయిగా ఉండే తేలికపాటి మసాలా"
                else -> "Soothing seasoning • Soft on stomach"
            },
            isSelected = selectedSpice == 1,
            isRecommended = true,
            leadingBadge = {
                Icon(
                    imageVector = Icons.Default.Spa,
                    contentDescription = null,
                    tint = VegGreen,
                    modifier = Modifier.size(26.dp)
                )
            },
            accentColor = VegGreen,
            onClick = {
                hapticEngine.trigger(HapticPattern.CLICK)
                onSpiceSelected(1)
            }
        )

        // Option 2: Medium Spice
        SeniorOptionButton(
            title = when (currentLang.lowercase()) {
                "hi" -> "मध्यम तीखा"
                "te" -> "మధ్యస్థ కారం"
                else -> "Medium Spice"
            },
            indicSubtitle = when (currentLang.lowercase()) {
                "hi" -> "संतुलित घरेलू मसाले"
                "te" -> "సమతుల్యమైన ఇంటి మసాలాలు"
                else -> "Balanced everyday spices"
            },
            isSelected = selectedSpice == 2,
            leadingBadge = {
                Icon(
                    imageVector = Icons.Default.Restaurant,
                    contentDescription = null,
                    tint = JainYellow,
                    modifier = Modifier.size(26.dp)
                )
            },
            accentColor = JainYellow,
            onClick = {
                hapticEngine.trigger(HapticPattern.CLICK)
                onSpiceSelected(2)
            }
        )

        // Option 3: Spicy
        SeniorOptionButton(
            title = when (currentLang.lowercase()) {
                "hi" -> "तीखा"
                "te" -> "ఎక్కువ కారం"
                else -> "Spicy"
            },
            indicSubtitle = when (currentLang.lowercase()) {
                "hi" -> "पारंपरिक तीखा स्वाद"
                "te" -> "ఘాటైన ఆంధ్ర మసాలాలు"
                else -> "Traditional bold chillies"
            },
            isSelected = selectedSpice == 3,
            leadingBadge = {
                Icon(
                    imageVector = Icons.Default.LocalFireDepartment,
                    contentDescription = null,
                    tint = NonVegRed,
                    modifier = Modifier.size(26.dp)
                )
            },
            accentColor = NonVegRed,
            onClick = {
                hapticEngine.trigger(HapticPattern.CLICK)
                onSpiceSelected(3)
            }
        )
    }
}

/**
 * Step 3: Displays 2 gentle, soft, comforting dishes with verbalized price, gentle spice badge, and large selection button.
 */
@Composable
private fun Step3GentleDishSelection(
    currentLang: String,
    dishes: List<ConciergeDish>,
    selectedDish: ConciergeDish?,
    hapticEngine: HapticFeedbackEngine,
    onDishSelected: (ConciergeDish) -> Unit
) {
    val title = when (currentLang.lowercase()) {
        "hi" -> "अपना पसंदीदा सुपाच्य भोजन चुनें"
        "te" -> "మీకు నచ్చిన సౌకర్యవంతమైన భోజనాన్ని ఎంచుకోండి"
        else -> "Choose your comforting meal"
    }

    val subtitle = when (currentLang.lowercase()) {
        "hi" -> "चबाने में आसान और पेट के लिए बिल्कुल हल्का भोजन (2 चुनिंदा व्यंजन):"
        "te" -> "నమలడానికి తేలికగా, కడుపుకు హాయిగా ఉండే 2 ప్రత్యేక వంటకాలు:"
        else -> "Specially curated dishes, soft to chew and gentle on the stomach (2 choices):"
    }

    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = title,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            color = NeoTextPrimary,
            lineHeight = 28.sp,
            modifier = Modifier.semantics { heading() }
        )

        Text(
            text = subtitle,
            fontSize = 16.sp,
            fontWeight = FontWeight.Normal,
            color = NeoTextSecondary,
            lineHeight = 22.sp
        )

        dishes.forEach { dish ->
            val isSelected = selectedDish?.id == dish.id

            SeniorDishCard(
                dish = dish,
                currentLang = currentLang,
                isSelected = isSelected,
                hapticEngine = hapticEngine,
                onSelect = { onDishSelected(dish) }
            )
        }
    }
}

/**
 * Large senior dish card with 20sp bold name, verbalized price, tactile selection button.
 */
@Composable
private fun SeniorDishCard(
    dish: ConciergeDish,
    currentLang: String,
    isSelected: Boolean,
    hapticEngine: HapticFeedbackEngine,
    onSelect: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.98f else 1.0f,
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
        label = "dish_card_scale"
    )

    val verbalizedPrice = remember(dish.priceRupees, currentLang) {
        NumberVerbalizer.verbalizePrice(dish.priceRupees, currentLang)
    }

    val selectButtonText = when (currentLang.lowercase()) {
        "hi" -> if (isSelected) "यह व्यंजन चुना गया ✓" else "यह व्यंजन चुनें"
        "te" -> if (isSelected) "ఈ వంటకం ఎంచుకోబడింది ✓" else "ఈ వంటకాన్ని ఎంచుకోండి"
        else -> if (isSelected) "Selected this Dish ✓" else "Select this Dish"
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .scale(scale)
            .neomorphicSurface(
                shape = RoundedCornerShape(18.dp),
                surfaceColor = if (isSelected) Color(0xFFF0FDF4) else NeoSurface,
                borderColor = if (isSelected) VegGreen else NeoBorder,
                borderWidth = if (isSelected) 2.dp else 1.dp,
                elevation = if (isSelected) 5.dp else 3.dp,
                isPressed = isPressed,
                cornerRadius = 18.dp
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = {
                    hapticEngine.trigger(HapticPattern.CLICK)
                    onSelect()
                }
            )
            .padding(18.dp)
            .semantics(mergeDescendants = true) {
                contentDescription = "${dish.getName(currentLang)}, price ₹${dish.priceRupees}, $verbalizedPrice. ${dish.getDescription(currentLang)}"
                role = Role.Button
            }
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            // Header Row: Dietary Badge + Spice Badge + Price
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    DietaryBadge(isVegetarian = dish.isVegetarian)

                    // Senior Gentle Badge
                    Box(
                        modifier = Modifier
                            .background(
                                color = if (dish.spiceLevel == 1) VegGreenContainer else JainYellowContainer,
                                shape = RoundedCornerShape(6.dp)
                            )
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = when (dish.spiceLevel) {
                                1 -> when (currentLang) {
                                    "hi" -> "हल्का • सुपाच्य"
                                    "te" -> "మృదువైనది • సులువు"
                                    else -> "Gentle Spice • Mild"
                                }
                                2 -> when (currentLang) {
                                    "hi" -> "मध्यम मसाला"
                                    "te" -> "మధ్యస్థ కారం"
                                    else -> "Medium Spice"
                                }
                                else -> when (currentLang) {
                                    "hi" -> "तीखा"
                                    "te" -> "కారమైనది"
                                    else -> "Spicy"
                                }
                            },
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (dish.spiceLevel == 1) VegGreenOnContainer else JainYellowOnContainer
                        )
                    }
                }

                // Visual Price Tag
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "₹${dish.priceRupees}",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = VegGreen
                    )
                }
            }

            // Dish Name in 20sp bold
            Text(
                text = dish.getName(currentLang),
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = NeoTextPrimary,
                lineHeight = 26.sp
            )

            // Price verbalized for seniors
            Text(
                text = "($verbalizedPrice)",
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = NeoTextSecondary
            )

            // Digestion & Senior Comfort Tag
            Box(
                modifier = Modifier
                    .background(Color(0xFFE0F2FE), RoundedCornerShape(6.dp))
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(
                    text = dish.getDigestionTag(currentLang),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF0369A1)
                )
            }

            // Description in 15sp
            Text(
                text = dish.getDescription(currentLang),
                fontSize = 15.sp,
                color = NeoTextSecondary,
                lineHeight = 22.sp
            )

            Spacer(modifier = Modifier.height(4.dp))

            // Large "Select this Dish" button
            val btnInteraction = remember { MutableInteractionSource() }
            val isBtnPressed by btnInteraction.collectIsPressedAsState()
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .neomorphicSurface(
                        shape = RoundedCornerShape(14.dp),
                        surfaceColor = if (isSelected) VegGreen else NeoSurface,
                        lightColor = if (isSelected) Color(0x6686EFAC) else NeoLightShadow,
                        darkColor = if (isSelected) Color(0x6614532D) else NeoDarkShadow,
                        borderColor = if (isSelected) VegGreen else NeoBorder,
                        elevation = if (isSelected) 4.dp else 2.dp,
                        isPressed = isBtnPressed,
                        cornerRadius = 14.dp
                    )
                    .clickable(
                        interactionSource = btnInteraction,
                        indication = null,
                        onClick = {
                            hapticEngine.trigger(HapticPattern.CLICK)
                            onSelect()
                        }
                    )
                    .padding(horizontal = 16.dp)
                    .semantics {
                        role = Role.Button
                        contentDescription = selectButtonText
                    }
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = if (isSelected) Icons.Default.CheckCircle else Icons.Default.Add,
                        contentDescription = null,
                        tint = if (isSelected) Color.White else NeoPrimary,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = selectButtonText,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isSelected) Color.White else NeoPrimary
                    )
                }
            }
        }
    }
}

/**
 * Step 4: Clear summary of chosen dish, total amount in 24sp bold text,
 * and 64dp green "Confirm & Place Order" button that writes to cartDao.
 */
@Composable
private fun Step4ReviewAndPlaceOrder(
    currentLang: String,
    dish: ConciergeDish,
    quantity: Int,
    hapticEngine: HapticFeedbackEngine,
    onQuantityChanged: (Int) -> Unit,
    onConfirmOrder: () -> Unit
) {
    val totalRupees = dish.priceRupees * quantity
    val totalVerbalized = remember(totalRupees, currentLang) {
        NumberVerbalizer.verbalizePrice(totalRupees, currentLang)
    }

    val summaryTitle = when (currentLang.lowercase()) {
        "hi" -> "अपने ऑर्डर की समीक्षा करें"
        "te" -> "మీ ఆర్డర్‌ను సమీక్షించండి"
        else -> "Review & Place Order"
    }

    val confirmButtonText = when (currentLang.lowercase()) {
        "hi" -> "ऑर्डर कन्फर्म करें (₹$totalRupees)"
        "te" -> "ఆర్డర్ ఖరారు చేయండి (₹$totalRupees)"
        else -> "Confirm & Place Order (₹$totalRupees)"
    }

    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = summaryTitle,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            color = NeoTextPrimary,
            lineHeight = 28.sp,
            modifier = Modifier.semantics { heading() }
        )

        // Summary Card
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .neomorphicSurface(
                    shape = RoundedCornerShape(18.dp),
                    surfaceColor = NeoSurface,
                    borderColor = NeoBorder,
                    elevation = 4.dp,
                    cornerRadius = 18.dp
                )
                .padding(20.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = when (currentLang) {
                                "hi" -> "चुना गया व्यंजन:"
                                "te" -> "ఎంచుకున్న వంటకం:"
                                else -> "Selected Dish:"
                            },
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = NeoTextSecondary
                        )

                        Text(
                            text = dish.getName(currentLang),
                            fontSize = 22.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = NeoTextPrimary,
                            lineHeight = 28.sp
                        )
                    }

                    DietaryBadge(isVegetarian = dish.isVegetarian)
                }

                HorizontalDivider(color = NeoBorder, thickness = 1.dp)

                // Quantity Row with large 48dp +/- buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = when (currentLang) {
                            "hi" -> "मात्रा (प्लेट):"
                            "te" -> "పరిమాణం (ప్లేట్లు):"
                            else -> "Portions / Quantity:"
                        },
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = NeoTextPrimary
                    )

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Minus Button
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(NeoSurfacePressed)
                                .border(1.dp, NeoBorder, CircleShape)
                                .clickable(enabled = quantity > 1) {
                                    hapticEngine.trigger(HapticPattern.CLICK)
                                    onQuantityChanged(quantity - 1)
                                }
                                .semantics {
                                    contentDescription = "Decrease quantity"
                                    role = Role.Button
                                }
                        ) {
                            Icon(
                                imageVector = Icons.Default.Remove,
                                contentDescription = null,
                                tint = if (quantity > 1) NeoTextPrimary else NeoTextMuted
                            )
                        }

                        Text(
                            text = "$quantity",
                            fontSize = 22.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = NeoTextPrimary
                        )

                        // Plus Button
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(NeoSurfacePressed)
                                .border(1.dp, NeoBorder, CircleShape)
                                .clickable {
                                    hapticEngine.trigger(HapticPattern.CLICK)
                                    onQuantityChanged(quantity + 1)
                                }
                                .semantics {
                                    contentDescription = "Increase quantity"
                                    role = Role.Button
                                }
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = null,
                                tint = NeoTextPrimary
                            )
                        }
                    }
                }

                HorizontalDivider(color = NeoBorder, thickness = 1.dp)

                // Senior Kitchen Special Instructions Note
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFFFEF9C3), RoundedCornerShape(10.dp))
                        .border(1.dp, Color(0xFFFACC15), RoundedCornerShape(10.dp))
                        .padding(12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Favorite,
                            contentDescription = null,
                            tint = Color(0xFFA16207),
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = when (currentLang) {
                                "hi" -> "रसोई निर्देश: वरिष्ठ नागरिकों के लिए कम तेल, नरम और हल्का तैयार किया जाएगा।"
                                "te" -> "వంటగది సూచన: తక్కువ నూనె, మెత్తగా మరియు తేలికగా తయారు చేయబడుతుంది."
                                else -> "Kitchen Note: Prepared extra soft, low oil, and gentle seasoning for seniors."
                            },
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF854D0E)
                        )
                    }
                }

                // Total Amount in large 24sp bold text
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = when (currentLang) {
                            "hi" -> "कुल देय राशि:"
                            "te" -> "మొత్తం చెల్లించవలసినది:"
                            else -> "Total Bill Amount:"
                        },
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = NeoTextSecondary
                    )

                    Row(
                        verticalAlignment = Alignment.Bottom,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "₹$totalRupees",
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Black,
                            color = VegGreen
                        )
                        Text(
                            text = "($totalVerbalized)",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = NeoTextSecondary,
                            modifier = Modifier.padding(bottom = 4.dp)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Large 64dp Green "Confirm & Place Order" Button
        val confirmInteraction = remember { MutableInteractionSource() }
        val isConfirmPressed by confirmInteraction.collectIsPressedAsState()
        val confirmScale by animateFloatAsState(
            targetValue = if (isConfirmPressed) 0.96f else 1.0f,
            animationSpec = spring(stiffness = Spring.StiffnessMedium),
            label = "confirm_btn_scale"
        )

        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .scale(confirmScale)
                .neomorphicSurface(
                    shape = RoundedCornerShape(16.dp),
                    surfaceColor = VegGreen,
                    lightColor = Color(0x8086EFAC),
                    darkColor = Color(0x8014532D),
                    borderColor = VegGreen,
                    elevation = 6.dp,
                    isPressed = isConfirmPressed,
                    cornerRadius = 16.dp
                )
                .clickable(
                    interactionSource = confirmInteraction,
                    indication = null,
                    onClick = onConfirmOrder
                )
                .padding(horizontal = 20.dp)
                .semantics {
                    contentDescription = "$confirmButtonText. Writes to cart and triggers voice confirmation."
                    role = Role.Button
                }
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(28.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = confirmButtonText,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
        }
    }
}

/**
 * Step 5: Calming and reassuring Success Screen after order is confirmed.
 */
@Composable
private fun Step5OrderConfirmedSuccess(
    currentLang: String,
    cartItem: CartItemEntity?,
    hapticEngine: HapticFeedbackEngine,
    onViewCart: () -> Unit,
    onOrderAnother: () -> Unit,
    onClose: () -> Unit
) {
    val dishName = cartItem?.name ?: "Meal"
    val qty = cartItem?.quantity ?: 1
    val total = (cartItem?.totalPriceRupees ?: 0L)

    val headline = when (currentLang.lowercase()) {
        "hi" -> "आपका ऑर्डर कन्फर्म हो गया है!"
        "te" -> "మీ ఆర్డర్ ఖరారైంది!"
        else -> "Order Confirmed & Placed!"
    }

    val gentleNotice = when (currentLang.lowercase()) {
        "hi" -> "रसोई में $dishName को बहुत नरमी और ताज़गी के साथ तैयार किया जा रहा है। कृपया आराम से बैठें।"
        "te" -> "వంటగదిలో $dishName ఎంతో మృదువుగా, ప్రేమతో సిద్ధం చేయబడుతోంది. దయచేసి విశ్రాంతి తీసుకోండి."
        else -> "The kitchen is preparing $dishName gently and fresh for you. Please relax at your table."
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        Spacer(modifier = Modifier.height(10.dp))

        // Big Green Success Circle
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(90.dp)
                .clip(CircleShape)
                .background(VegGreenContainer)
                .border(2.5.dp, VegGreen, CircleShape)
        ) {
            Icon(
                imageVector = Icons.Default.CheckCircle,
                contentDescription = null,
                tint = VegGreen,
                modifier = Modifier.size(54.dp)
            )
        }

        Text(
            text = headline,
            fontSize = 24.sp,
            fontWeight = FontWeight.ExtraBold,
            color = VegGreen,
            textAlign = TextAlign.Center
        )

        // Summary Card
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .neomorphicSurface(
                    shape = RoundedCornerShape(16.dp),
                    surfaceColor = NeoSurface,
                    borderColor = VegGreen,
                    borderWidth = 1.5.dp,
                    elevation = 4.dp,
                    cornerRadius = 16.dp
                )
                .padding(18.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "$dishName ($qty portion${if (qty > 1) "s" else ""})",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = NeoTextPrimary
                )

                Text(
                    text = "Total: ₹$total",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = VegGreen
                )

                HorizontalDivider(color = NeoBorder, thickness = 1.dp)

                Text(
                    text = gentleNotice,
                    fontSize = 16.sp,
                    color = NeoTextSecondary,
                    lineHeight = 22.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Action 1: View Order in Cart (64dp button)
        SeniorOptionButton(
            title = when (currentLang) {
                "hi" -> "कार्ट में ऑर्डर देखें"
                "te" -> "కార్ట్‌లో ఆర్డర్ చూడండి"
                else -> "View Order in Cart"
            },
            indicSubtitle = when (currentLang) {
                "hi" -> "बिल और आइटम विवरण"
                "te" -> "ఆర్డర్ వివరాలు"
                else -> "Check items and billing"
            },
            isSelected = true,
            accentColor = NeoPrimary,
            onClick = onViewCart
        )

        // Action 2: Order Another Item (64dp button)
        SeniorOptionButton(
            title = when (currentLang) {
                "hi" -> "एक और व्यंजन जोड़ें"
                "te" -> "మరొక వంటకాన్ని ఎంచుకోండి"
                else -> "Order Another Meal"
            },
            indicSubtitle = when (currentLang) {
                "hi" -> "वापस पहले कदम पर जाएं"
                "te" -> "మొదటి దశకు వెళ్ళండి"
                else -> "Return to Step 1"
            },
            isSelected = false,
            accentColor = JainYellow,
            onClick = onOrderAnother
        )

        // Action 3: Done & Close
        TextButton(
            onClick = onClose,
            modifier = Modifier.padding(top = 8.dp)
        ) {
            Text(
                text = when (currentLang) {
                    "hi" -> "समाप्त करें / बंद करें"
                    "te" -> "పూర్తయింది / మూసివేయండి"
                    else -> "Done / Close"
                },
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = NeoTextSecondary
            )
        }
    }
}

/**
 * Reusable 64dp Senior Option Button with Neomorphic tactile elevation and large text.
 */
@Composable
private fun SeniorOptionButton(
    title: String,
    indicSubtitle: String,
    secondaryIndic: String? = null,
    isSelected: Boolean,
    isRecommended: Boolean = false,
    leadingBadge: (@Composable () -> Unit)? = null,
    accentColor: Color = NeoPrimary,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.97f else 1.0f,
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
        label = "option_scale"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .scale(scale)
            .neomorphicSurface(
                shape = RoundedCornerShape(16.dp),
                surfaceColor = if (isSelected) Color(0xFFF8FAFC) else NeoSurface,
                borderColor = if (isSelected) accentColor else NeoBorder,
                borderWidth = if (isSelected) 2.2.dp else 1.dp,
                elevation = if (isSelected) 5.dp else 3.dp,
                isPressed = isPressed,
                cornerRadius = 16.dp
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 16.dp, vertical = 14.dp)
            .semantics {
                contentDescription = "$title. $indicSubtitle${if (isRecommended) ". Recommended for seniors" else ""}"
                role = Role.RadioButton
            }
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier.weight(1f)
            ) {
                if (leadingBadge != null) {
                    leadingBadge()
                }

                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = title,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isSelected) accentColor else NeoTextPrimary
                        )

                        if (isRecommended) {
                            Box(
                                modifier = Modifier
                                    .background(VegGreenContainer, RoundedCornerShape(4.dp))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = "★ Best for Seniors",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = VegGreenOnContainer
                                )
                            }
                        }
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = indicSubtitle,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = if (isSelected) accentColor.copy(alpha = 0.85f) else NeoTextSecondary
                        )

                        if (secondaryIndic != null) {
                            Text(
                                text = "•  $secondaryIndic",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                                color = NeoTextMuted
                            )
                        }
                    }
                }
            }

            // Radio / Check Indicator
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(if (isSelected) accentColor else NeoSurfacePressed)
                    .border(1.5.dp, if (isSelected) accentColor else NeoBorder, CircleShape)
            ) {
                if (isSelected) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

/**
 * Bottom navigation controls: "Back to previous step", "Next step", "Speak again slowly (0.8x)".
 */
@Composable
private fun SeniorBottomNavigationBar(
    currentStep: ConciergeStep,
    currentLang: String,
    canProceed: Boolean,
    onBack: () -> Unit,
    onNext: () -> Unit,
    onSpeakAgainSlowly: () -> Unit
) {
    val backLabel = when (currentLang.lowercase()) {
        "hi" -> "पिछला कदम"
        "te" -> "వెనుకకు"
        else -> "Back"
    }

    val nextLabel = when (currentLang.lowercase()) {
        "hi" -> "अगला कदम"
        "te" -> "తరువాతి దశ"
        else -> "Next step"
    }

    val speakAgainLabel = when (currentLang.lowercase()) {
        "hi" -> "धीरे से दोबारा सुनें (0.8x)"
        "te" -> "మళ్ళీ నెమ్మదిగా వినండి (0.8x)"
        else -> "Speak again slowly (0.8x)"
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(NeoSurface)
            .border(width = 1.dp, color = NeoBorder)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // "Speak again slowly" button across entire width
        val speakInteraction = remember { MutableInteractionSource() }
        val isSpeakPressed by speakInteraction.collectIsPressedAsState()
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .neomorphicSurface(
                    shape = RoundedCornerShape(12.dp),
                    surfaceColor = if (isSpeakPressed) NeoSurfacePressed else NeoSurface,
                    borderColor = NeoBorder,
                    elevation = 2.dp,
                    isPressed = isSpeakPressed,
                    cornerRadius = 12.dp
                )
                .clickable(
                    interactionSource = speakInteraction,
                    indication = null,
                    onClick = onSpeakAgainSlowly
                )
                .semantics {
                    contentDescription = "$speakAgainLabel. Repeats instructions slowly."
                    role = Role.Button
                }
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                    contentDescription = null,
                    tint = NeoPrimary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = speakAgainLabel,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = NeoPrimary
                )
            }
        }

        // Row of Back and Next Buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Back Button (56dp height)
            val backInteraction = remember { MutableInteractionSource() }
            val isBackPressed by backInteraction.collectIsPressedAsState()
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .weight(1f)
                    .height(56.dp)
                    .neomorphicSurface(
                        shape = RoundedCornerShape(14.dp),
                        surfaceColor = if (isBackPressed) NeoSurfacePressed else NeoSurface,
                        borderColor = NeoBorder,
                        elevation = 3.dp,
                        isPressed = isBackPressed,
                        cornerRadius = 14.dp
                    )
                    .clickable(
                        interactionSource = backInteraction,
                        indication = null,
                        onClick = onBack
                    )
                    .padding(horizontal = 14.dp)
                    .semantics {
                        contentDescription = "Back to previous step"
                        role = Role.Button
                    }
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = null,
                        tint = NeoTextPrimary,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = backLabel,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = NeoTextPrimary
                    )
                }
            }

            // Next Button (56dp height) - only if not already on review step
            if (currentStep != ConciergeStep.REVIEW_AND_ORDER) {
                val nextInteraction = remember { MutableInteractionSource() }
                val isNextPressed by nextInteraction.collectIsPressedAsState()
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .weight(1f)
                        .height(56.dp)
                        .neomorphicSurface(
                            shape = RoundedCornerShape(14.dp),
                            surfaceColor = if (!canProceed) NeoSurfacePressed else NeoPrimary,
                            lightColor = if (canProceed) Color(0x6693C5FD) else NeoLightShadow,
                            darkColor = if (canProceed) Color(0x661E3A8A) else NeoDarkShadow,
                            borderColor = if (canProceed) NeoPrimary else NeoBorder,
                            elevation = if (canProceed) 4.dp else 1.dp,
                            isPressed = isNextPressed && canProceed,
                            cornerRadius = 14.dp
                        )
                        .clickable(
                            enabled = canProceed,
                            interactionSource = nextInteraction,
                            indication = null,
                            onClick = onNext
                        )
                        .padding(horizontal = 14.dp)
                        .semantics {
                            contentDescription = "Go to next step"
                            role = Role.Button
                        }
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = nextLabel,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (canProceed) NeoOnPrimary else NeoTextMuted
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = null,
                            tint = if (canProceed) NeoOnPrimary else NeoTextMuted,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }
        }
    }
}
