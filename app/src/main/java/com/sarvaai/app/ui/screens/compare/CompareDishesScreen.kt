package com.sarvaai.app.ui.screens.compare

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.CompareArrows
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.sarvaai.app.adapt.verbalizer.NumberVerbalizer
import com.sarvaai.app.audio.earcons.EarconEngine
import com.sarvaai.app.audio.earcons.EarconType
import com.sarvaai.app.audio.haptics.HapticFeedbackEngine
import com.sarvaai.app.audio.haptics.HapticPattern
import com.sarvaai.app.data.dao.CartDao
import com.sarvaai.app.data.entities.CartItemEntity
import com.sarvaai.app.data.entities.MenuItemEntity
import com.sarvaai.app.ui.components.DietaryBadge
import com.sarvaai.app.ui.components.NeomorphicButton
import com.sarvaai.app.ui.components.NeomorphicCard
import com.sarvaai.app.ui.components.NeomorphicIconButton
import com.sarvaai.app.ui.components.NeomorphicPill
import com.sarvaai.app.ui.components.neomorphicSurface
import com.sarvaai.app.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.UUID
import kotlin.math.abs

/**
 * Preloaded Rich Sample Menu Dishes used when no camera-scanned dishes are present.
 */
val SAMPLE_DISHES = listOf(
    MenuItemEntity(
        itemId = "sample_masala_dosa",
        sectionId = "sample_tiffin",
        nameEnglish = "Masala Dosa",
        nameHindi = "मसाला डोसा",
        nameTelugu = "మసాలా దోశ",
        transliteration = "Masala Dosa",
        description = "Crispy golden fermented crepe made from rice and lentils, stuffed with spiced potato masala and served with coconut chutney and sambar.",
        pricePaise = 9000L,
        isVegetarian = true,
        isJain = true,
        isVegan = false,
        isNutFree = true,
        spiceLevel = 1,
        isChefSpecial = false
    ),
    MenuItemEntity(
        itemId = "sample_paneer_butter_masala",
        sectionId = "sample_curries",
        nameEnglish = "Paneer Butter Masala",
        nameHindi = "पनीर बटर मसाला",
        nameTelugu = "పనీర్ బటర్ మసాలా",
        transliteration = "Paneer Butter Masala",
        description = "Rich, creamy cottage cheese cubes simmered in a mildly spiced, velvety tomato, butter, and cashew nut gravy.",
        pricePaise = 22000L,
        isVegetarian = true,
        isJain = false,
        isVegan = false,
        isNutFree = false,
        spiceLevel = 2,
        isChefSpecial = true
    ),
    MenuItemEntity(
        itemId = "sample_chilli_chicken",
        sectionId = "sample_starters",
        nameEnglish = "Chilli Chicken",
        nameHindi = "चिली चिकन",
        nameTelugu = "చిల్లీ చికెన్",
        transliteration = "Chilli Chicken",
        description = "Crisp batter-fried chicken chunks tossed in spicy Indo-Chinese soya sauce, green chillies, garlic, and bell peppers.",
        pricePaise = 24000L,
        isVegetarian = false,
        isJain = false,
        isVegan = false,
        isNutFree = true,
        spiceLevel = 3,
        isChefSpecial = false
    ),
    MenuItemEntity(
        itemId = "sample_hyderabadi_biryani",
        sectionId = "sample_biryanis",
        nameEnglish = "Hyderabadi Dum Biryani",
        nameHindi = "हैदराबादी दम बिरयानी",
        nameTelugu = "హైదరాబాదీ దమ్ బిర్యానీ",
        transliteration = "Hyderabadi Dum Biryani",
        description = "Fragrant long-grain basmati rice and marinated chicken cooked on dum slow fire with royal aromatic spices, saffron, and fried onions.",
        pricePaise = 26000L,
        isVegetarian = false,
        isJain = false,
        isVegan = false,
        isNutFree = true,
        spiceLevel = 3,
        isChefSpecial = true
    ),
    MenuItemEntity(
        itemId = "sample_dal_makhani",
        sectionId = "sample_curries",
        nameEnglish = "Dal Makhani",
        nameHindi = "दाल मखनी",
        nameTelugu = "దాల్ మఖ్నీ",
        transliteration = "Dal Makhani",
        description = "Slow-simmered whole black lentils and red kidney beans cooked overnight with butter, cream, and gentle whole spices.",
        pricePaise = 18000L,
        isVegetarian = true,
        isJain = true,
        isVegan = false,
        isNutFree = true,
        spiceLevel = 1,
        isChefSpecial = false
    ),
    MenuItemEntity(
        itemId = "sample_hakka_noodles",
        sectionId = "sample_mains",
        nameEnglish = "Veg Hakka Noodles",
        nameHindi = "वेज हक्का नूडल्स",
        nameTelugu = "వెజ్ హక్కా నూడుల్స్",
        transliteration = "Veg Hakka Noodles",
        description = "Wok-tossed stir-fried noodles tossed with crunchy shredded cabbage, carrots, bell peppers, spring onion, and aromatic sauces.",
        pricePaise = 15000L,
        isVegetarian = true,
        isJain = false,
        isVegan = true,
        isNutFree = true,
        spiceLevel = 2,
        isChefSpecial = false
    ),
    MenuItemEntity(
        itemId = "sample_fish_fry",
        sectionId = "sample_seafood",
        nameEnglish = "Fish Fry",
        nameHindi = "फिश फ्राई",
        nameTelugu = "ఫిష్ ఫ్రై",
        transliteration = "Fish Fry",
        description = "Fresh fish fillets marinated in fiery red chilli masala, curry leaves, and lemon juice, shallow-fried to crispy perfection.",
        pricePaise = 28000L,
        isVegetarian = false,
        isJain = false,
        isVegan = false,
        isNutFree = true,
        spiceLevel = 3,
        isChefSpecial = false
    ),
    MenuItemEntity(
        itemId = "sample_filter_coffee",
        sectionId = "sample_beverages",
        nameEnglish = "Filter Coffee",
        nameHindi = "फ़िल्टर कॉफ़ी",
        nameTelugu = "ఫిల్టర్ కాఫీ",
        transliteration = "Filter Coffee",
        description = "Authentic South Indian decoction brewed from dark roasted chicory coffee beans, frothed with hot creamy milk in a traditional dabarah.",
        pricePaise = 4000L,
        isVegetarian = true,
        isJain = true,
        isVegan = false,
        isNutFree = true,
        spiceLevel = 0,
        isChefSpecial = false
    )
)

enum class ComparisonViewMode {
    SIDE_BY_SIDE,
    LIST_VIEW
}

enum class DietaryFilter {
    ALL,
    VEG,
    NON_VEG,
    JAIN,
    NUT_FREE
}

enum class SpiceFilter {
    ALL,
    MILD,
    MEDIUM,
    SPICY
}

enum class PriceSortOrder {
    DEFAULT,
    LOW_TO_HIGH,
    HIGH_TO_LOW
}

/**
 * Interactive Neomorphic Dish Comparison Screen.
 * Compares dishes side-by-side or in an exploratory filtered catalog with instant Room cart addition.
 */
@Composable
fun CompareDishesScreen(
    menuItems: List<MenuItemEntity>,
    cartDao: CartDao,
    hapticEngine: HapticFeedbackEngine,
    earconEngine: EarconEngine,
    selectedLanguage: String,
    onSpeakNarration: (String) -> Unit,
    onStopNarration: () -> Unit = {},
    onNavigateToOrderHub: () -> Unit = {},
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    val isUsingScannedMenu = menuItems.isNotEmpty()
    val effectiveDishes = remember(menuItems) {
        if (isUsingScannedMenu) menuItems else SAMPLE_DISHES
    }

    val cartItems by cartDao.getCartItems().collectAsState(initial = emptyList())
    val totalCartCount = cartItems.sumOf { it.quantity }
    val totalCartPaise = cartItems.sumOf { it.totalPricePaise }
    val totalCartRupees = totalCartPaise / 100

    var currentViewMode by remember { mutableStateOf(ComparisonViewMode.SIDE_BY_SIDE) }
    var selectedDishAId by remember(effectiveDishes) {
        mutableStateOf(effectiveDishes.firstOrNull()?.itemId ?: "")
    }
    var selectedDishBId by remember(effectiveDishes) {
        mutableStateOf(
            effectiveDishes.getOrNull(1)?.itemId
                ?: effectiveDishes.firstOrNull()?.itemId
                ?: ""
        )
    }

    // Modal pickers for Side-by-Side mode
    var showPickerForDishSlot by remember { mutableStateOf<String?>(null) } // "A" or "B" or null

    // Comprehensive list view filters
    var dietaryFilter by remember { mutableStateOf(DietaryFilter.ALL) }
    var spiceFilter by remember { mutableStateOf(SpiceFilter.ALL) }
    var priceSortOrder by remember { mutableStateOf(PriceSortOrder.DEFAULT) }

    // Instant feedback for added items
    var recentlyAddedIds by remember { mutableStateOf(setOf<String>()) }

    // Bottom popup card visibility (auto-dismisses after 2 seconds upon opening compare section)
    var isBottomPopupVisible by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        delay(2000L)
        isBottomPopupVisible = false
    }

    // Safe Back navigation
    BackHandler {
        onStopNarration()
        onClose()
    }

    fun addItemToCart(dish: MenuItemEntity) {
        hapticEngine.trigger(HapticPattern.CONFIRMATION)
        earconEngine.play(EarconType.ORDER_CONFIRMED)

        // Add to temporary visual animation state
        recentlyAddedIds = recentlyAddedIds + dish.itemId
        scope.launch {
            delay(1600)
            recentlyAddedIds = recentlyAddedIds - dish.itemId
        }

        // Room DB insertion
        scope.launch(Dispatchers.IO) {
            val existing = cartDao.getCartItemByItemId(dish.itemId)
            if (existing != null) {
                cartDao.updateQuantity(existing.cartItemId, existing.quantity + 1)
            } else {
                val newCartItem = CartItemEntity(
                    cartItemId = "cart_${UUID.randomUUID().toString().take(8)}",
                    itemId = dish.itemId,
                    name = dish.nameEnglish,
                    unitPricePaise = dish.pricePaise,
                    quantity = 1,
                    specialNotes = null,
                    addedTimestamp = System.currentTimeMillis()
                )
                cartDao.insertOrUpdate(newCartItem)
            }
        }
    }

    val dishA = effectiveDishes.firstOrNull { it.itemId == selectedDishAId } ?: effectiveDishes.first()
    val dishB = effectiveDishes.firstOrNull { it.itemId == selectedDishBId }
        ?: effectiveDishes.getOrNull(1)
        ?: dishA

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(NeoBackground)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = if (isBottomPopupVisible) 76.dp else 0.dp)
        ) {
            // ================================================================
            // HEADER BAR
            // ================================================================
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(NeoBackground)
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        NeomorphicIconButton(
                            icon = Icons.AutoMirrored.Filled.ArrowBack,
                            onClick = {
                                onStopNarration()
                                onClose()
                            },
                            contentDescription = "Return to previous screen",
                            size = 44.dp
                        )

                        Column {
                            Text(
                                text = "Compare Dishes",
                                fontSize = 21.sp,
                                fontWeight = FontWeight.Bold,
                                color = NeoTextPrimary,
                                modifier = Modifier.semantics { heading() }
                            )
                            Text(
                                text = "Compare dishes by spice level and price, then order",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Normal,
                                color = NeoTextSecondary
                            )
                        }
                    }

                    // Cart / Order Hub Shortcut
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(44.dp)
                            .neomorphicSurface(
                                shape = CircleShape,
                                surfaceColor = if (totalCartCount > 0) NeoPrimaryContainer else NeoSurface,
                                borderColor = if (totalCartCount > 0) NeoPrimary else NeoBorder,
                                elevation = 3.dp,
                                cornerRadius = 22.dp
                            )
                            .clickable {
                                hapticEngine.trigger(HapticPattern.CLICK)
                                onNavigateToOrderHub()
                            }
                            .semantics {
                                contentDescription = "Order Hub, $totalCartCount items in cart"
                                role = Role.Button
                            }
                    ) {
                        Icon(
                            imageVector = Icons.Default.ShoppingCart,
                            contentDescription = null,
                            tint = if (totalCartCount > 0) NeoPrimary else NeoTextPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                        if (totalCartCount > 0) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .offset(x = 2.dp, y = (-2).dp)
                                    .size(17.dp)
                                    .clip(CircleShape)
                                    .background(NeoPrimary),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "$totalCartCount",
                                    color = NeoOnPrimary,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Source Indicator Banner & Audio Overview
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // Source Indicator Pill
                    val sourceIndicatorText = if (isUsingScannedMenu) {
                        "Scanned Menu Dishes"
                    } else {
                        "Sample Menu Dishes (Preloaded)"
                    }
                    val sourceIcon = if (isUsingScannedMenu) {
                        Icons.Default.DocumentScanner
                    } else {
                        Icons.Default.RestaurantMenu
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier
                            .neomorphicSurface(
                                shape = RoundedCornerShape(12.dp),
                                surfaceColor = NeoSurfacePressed,
                                borderColor = NeoBorder,
                                elevation = 1.dp,
                                cornerRadius = 12.dp
                            )
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Icon(
                            imageVector = sourceIcon,
                            contentDescription = null,
                            tint = NeoPrimary,
                            modifier = Modifier.size(15.dp)
                        )
                        Text(
                            text = sourceIndicatorText,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = NeoTextPrimary
                        )
                    }

                    // Narrate Screen Button
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier
                            .neomorphicSurface(
                                shape = RoundedCornerShape(12.dp),
                                surfaceColor = NeoSurface,
                                borderColor = NeoBorder,
                                elevation = 2.dp,
                                cornerRadius = 12.dp
                            )
                            .clickable {
                                hapticEngine.trigger(HapticPattern.CLICK)
                                val speech = if (currentViewMode == ComparisonViewMode.SIDE_BY_SIDE) {
                                    val priceDiff = abs(dishA.priceRupees - dishB.priceRupees)
                                    val verbalPriceA = NumberVerbalizer.verbalizePrice(dishA.priceRupees, selectedLanguage)
                                    val verbalPriceB = NumberVerbalizer.verbalizePrice(dishB.priceRupees, selectedLanguage)
                                    val comparisonText = when {
                                        dishA.priceRupees < dishB.priceRupees -> "${dishA.nameEnglish} is ${dishA.priceRupees} rupees, saving you $priceDiff rupees compared to ${dishB.nameEnglish}."
                                        dishA.priceRupees > dishB.priceRupees -> "${dishB.nameEnglish} is cheaper by $priceDiff rupees."
                                        else -> "Both dishes cost the same, ${dishA.priceRupees} rupees."
                                    }
                                    "Comparing ${dishA.nameEnglish} and ${dishB.nameEnglish}. ${dishA.nameEnglish} costs $verbalPriceA with spice level ${getSpiceName(dishA.spiceLevel)}. ${dishB.nameEnglish} costs $verbalPriceB with spice level ${getSpiceName(dishB.spiceLevel)}. $comparisonText"
                                } else {
                                    "Showing all ${effectiveDishes.size} available menu dishes. You can filter by spice level, dietary preferences, or sort by price."
                                }
                                onSpeakNarration(speech)
                            }
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                            .semantics {
                                contentDescription = "Listen to audio overview"
                                role = Role.Button
                            }
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                            contentDescription = null,
                            tint = NeoPrimary,
                            modifier = Modifier.size(15.dp)
                        )
                        Text(
                            text = "Hear Overview",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = NeoPrimary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // VIEW MODE SWITCHER TABS
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .neomorphicSurface(
                            shape = RoundedCornerShape(14.dp),
                            surfaceColor = NeoSurfacePressed,
                            borderColor = NeoBorder,
                            elevation = 2.dp,
                            isPressed = true,
                            cornerRadius = 14.dp
                        )
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val isSideBySide = currentViewMode == ComparisonViewMode.SIDE_BY_SIDE
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .weight(1f)
                            .height(40.dp)
                            .neomorphicSurface(
                                shape = RoundedCornerShape(10.dp),
                                surfaceColor = if (isSideBySide) NeoPrimary else NeoSurface,
                                borderColor = if (isSideBySide) NeoPrimary else NeoBorder,
                                elevation = if (isSideBySide) 3.dp else 1.dp,
                                cornerRadius = 10.dp
                            )
                            .clickable {
                                hapticEngine.trigger(HapticPattern.CLICK)
                                currentViewMode = ComparisonViewMode.SIDE_BY_SIDE
                            }
                            .semantics {
                                contentDescription = "Side-by-side two dish comparison view"
                                role = Role.Tab
                            }
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.CompareArrows,
                                contentDescription = null,
                                tint = if (isSideBySide) NeoOnPrimary else NeoTextPrimary,
                                modifier = Modifier.size(17.dp)
                            )
                            Text(
                                text = "Compare 2 Dishes",
                                fontSize = 13.sp,
                                fontWeight = if (isSideBySide) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSideBySide) NeoOnPrimary else NeoTextPrimary
                            )
                        }
                    }

                    val isListView = currentViewMode == ComparisonViewMode.LIST_VIEW
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .weight(1f)
                            .height(40.dp)
                            .neomorphicSurface(
                                shape = RoundedCornerShape(10.dp),
                                surfaceColor = if (isListView) NeoPrimary else NeoSurface,
                                borderColor = if (isListView) NeoPrimary else NeoBorder,
                                elevation = if (isListView) 3.dp else 1.dp,
                                cornerRadius = 10.dp
                            )
                            .clickable {
                                hapticEngine.trigger(HapticPattern.CLICK)
                                currentViewMode = ComparisonViewMode.LIST_VIEW
                            }
                            .semantics {
                                contentDescription = "Comprehensive list catalog view"
                                role = Role.Tab
                            }
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.RestaurantMenu,
                                contentDescription = null,
                                tint = if (isListView) NeoOnPrimary else NeoTextPrimary,
                                modifier = Modifier.size(17.dp)
                            )
                            Text(
                                text = "All Dishes List",
                                fontSize = 13.sp,
                                fontWeight = if (isListView) FontWeight.Bold else FontWeight.Medium,
                                color = if (isListView) NeoOnPrimary else NeoTextPrimary
                            )
                        }
                    }
                }
            }

            // ================================================================
            // MAIN VIEW CONTENT
            // ================================================================
            when (currentViewMode) {
                ComparisonViewMode.SIDE_BY_SIDE -> {
                    SideBySideComparisonContent(
                        dishA = dishA,
                        dishB = dishB,
                        allDishes = effectiveDishes,
                        selectedLanguage = selectedLanguage,
                        recentlyAddedIds = recentlyAddedIds,
                        onSelectDishA = { selectedDishAId = it },
                        onSelectDishB = { selectedDishBId = it },
                        onSwapDishes = {
                            val temp = selectedDishAId
                            selectedDishAId = selectedDishBId
                            selectedDishBId = temp
                            hapticEngine.trigger(HapticPattern.CLICK)
                        },
                        onOpenPicker = { slot -> showPickerForDishSlot = slot },
                        onAddToCart = { addItemToCart(it) },
                        onSpeakNarration = onSpeakNarration
                    )
                }
                ComparisonViewMode.LIST_VIEW -> {
                    ComprehensiveListViewContent(
                        dishes = effectiveDishes,
                        selectedLanguage = selectedLanguage,
                        dietaryFilter = dietaryFilter,
                        spiceFilter = spiceFilter,
                        priceSortOrder = priceSortOrder,
                        recentlyAddedIds = recentlyAddedIds,
                        onDietaryFilterChanged = { dietaryFilter = it },
                        onSpiceFilterChanged = { spiceFilter = it },
                        onPriceSortOrderChanged = { priceSortOrder = it },
                        onAddToCart = { addItemToCart(it) },
                        onSelectForComparison = { dish ->
                            selectedDishAId = dish.itemId
                            currentViewMode = ComparisonViewMode.SIDE_BY_SIDE
                            hapticEngine.trigger(HapticPattern.CLICK)
                        },
                        onSpeakNarration = onSpeakNarration
                    )
                }
            }
        }

        // ================================================================
        // BOTTOM POPUP CARD (Cart Overview or Comparison Tip Popup)
        // Automatically visible for only 2 seconds after opening compare section
        // ================================================================
        AnimatedVisibility(
            visible = isBottomPopupVisible,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            if (totalCartCount > 0) {
                // Cart Overview Popup Card
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .neomorphicSurface(
                            shape = RoundedCornerShape(16.dp),
                            surfaceColor = NeoSurface,
                            borderColor = NeoPrimary,
                            borderWidth = 1.5.dp,
                            elevation = 6.dp,
                            cornerRadius = 16.dp
                        )
                        .clickable {
                            hapticEngine.trigger(HapticPattern.CLICK)
                            onNavigateToOrderHub()
                        }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(NeoPrimaryContainer)
                        ) {
                            Icon(
                                imageVector = Icons.Default.ShoppingCart,
                                contentDescription = null,
                                tint = NeoPrimary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Column {
                            Text(
                                text = "$totalCartCount item${if (totalCartCount > 1) "s" else ""} added",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = NeoTextPrimary
                            )
                            Text(
                                text = "Total: ₹$totalCartRupees (${NumberVerbalizer.verbalizePrice(totalCartRupees, selectedLanguage)})",
                                fontSize = 12.sp,
                                color = NeoTextSecondary
                            )
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "View Cart",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = NeoPrimary
                        )
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = null,
                            tint = NeoPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            } else {
                // Comparison Tip Popup Card
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .neomorphicSurface(
                            shape = RoundedCornerShape(16.dp),
                            surfaceColor = NeoSurface,
                            borderColor = NeoPrimary.copy(alpha = 0.5f),
                            borderWidth = 1.dp,
                            elevation = 6.dp,
                            cornerRadius = 16.dp
                        )
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(NeoPrimaryContainer)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null,
                            tint = NeoPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Column {
                        Text(
                            text = "Comparison Tip",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = NeoTextPrimary
                        )
                        Text(
                            text = "Compare dishes side-by-side or switch to catalog list view",
                            fontSize = 11.sp,
                            color = NeoTextSecondary
                        )
                    }
                }
            }
        }

        // ================================================================
        // DISH PICKER DIALOG
        // ================================================================
        showPickerForDishSlot?.let { slot ->
            DishSelectionDialog(
                title = if (slot == "A") "Select Dish A" else "Select Dish B",
                dishes = effectiveDishes,
                selectedDishId = if (slot == "A") selectedDishAId else selectedDishBId,
                selectedLanguage = selectedLanguage,
                onDismiss = { showPickerForDishSlot = null },
                onSelectDish = { dish ->
                    if (slot == "A") selectedDishAId = dish.itemId else selectedDishBId = dish.itemId
                    showPickerForDishSlot = null
                    hapticEngine.trigger(HapticPattern.CLICK)
                }
            )
        }
    }
}

/**
 * Side-by-Side 2-Dish Interactive Comparison Mode.
 */
@Composable
private fun SideBySideComparisonContent(
    dishA: MenuItemEntity,
    dishB: MenuItemEntity,
    allDishes: List<MenuItemEntity>,
    selectedLanguage: String,
    recentlyAddedIds: Set<String>,
    onSelectDishA: (String) -> Unit,
    onSelectDishB: (String) -> Unit,
    onSwapDishes: () -> Unit,
    onOpenPicker: (String) -> Unit,
    onAddToCart: (MenuItemEntity) -> Unit,
    onSpeakNarration: (String) -> Unit
) {
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // ================================================================
        // SELECTION CONTROLS BAR WITH SWAP ACTION
        // ================================================================
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Dish A Picker Button
            Box(
                modifier = Modifier
                    .weight(1f)
                    .neomorphicSurface(
                        shape = RoundedCornerShape(12.dp),
                        surfaceColor = NeoSurface,
                        borderColor = NeoBorder,
                        elevation = 3.dp,
                        cornerRadius = 12.dp
                    )
                    .clickable { onOpenPicker("A") }
                    .padding(horizontal = 12.dp, vertical = 10.dp)
            ) {
                Column {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(NeoPrimary)
                        )
                        Text(
                            text = "Dish A",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = NeoPrimary
                        )
                    }
                    Text(
                        text = dishA.nameEnglish,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = NeoTextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // Swap Button
            NeomorphicIconButton(
                icon = Icons.Default.SwapHoriz,
                onClick = onSwapDishes,
                contentDescription = "Swap Dish A and Dish B",
                size = 40.dp
            )

            // Dish B Picker Button
            Box(
                modifier = Modifier
                    .weight(1f)
                    .neomorphicSurface(
                        shape = RoundedCornerShape(12.dp),
                        surfaceColor = NeoSurface,
                        borderColor = NeoBorder,
                        elevation = 3.dp,
                        cornerRadius = 12.dp
                    )
                    .clickable { onOpenPicker("B") }
                    .padding(horizontal = 12.dp, vertical = 10.dp)
            ) {
                Column {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(NeoTextSecondary)
                        )
                        Text(
                            text = "Dish B",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = NeoTextSecondary
                        )
                    }
                    Text(
                        text = dishB.nameEnglish,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = NeoTextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        // ================================================================
        // QUICK PICK CHIP ROWS
        // ================================================================
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = "Quick Pick Dish A:",
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = NeoTextSecondary
            )
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(horizontal = 2.dp)
            ) {
                items(allDishes) { dish ->
                    val isSelected = dish.itemId == dishA.itemId
                    NeomorphicPill(
                        text = "${dish.nameEnglish} (₹${dish.priceRupees})",
                        isSelected = isSelected,
                        onClick = { onSelectDishA(dish.itemId) }
                    )
                }
            }
        }

        // ================================================================
        // PRICE & SPICE DIFFERENCE SUMMARY CARD
        // ================================================================
        val priceDiff = abs(dishA.priceRupees - dishB.priceRupees)
        val priceDiffPercentage = if (dishA.priceRupees > 0 && dishB.priceRupees > 0) {
            val maxPrice = maxOf(dishA.priceRupees, dishB.priceRupees)
            (priceDiff * 100) / maxPrice
        } else 0

        NeomorphicCard(
            modifier = Modifier.fillMaxWidth(),
            elevation = 4.dp
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Analytics,
                        contentDescription = null,
                        tint = NeoPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = "Comparison Summary",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = NeoTextPrimary
                    )
                }

                // TTS Speech for this comparison
                NeomorphicIconButton(
                    icon = Icons.AutoMirrored.Filled.VolumeUp,
                    onClick = {
                        val speech = buildString {
                            append("Comparing ${dishA.nameEnglish} and ${dishB.nameEnglish}. ")
                            if (dishA.priceRupees < dishB.priceRupees) {
                                append("${dishA.nameEnglish} is cheaper by $priceDiff rupees. ")
                            } else if (dishA.priceRupees > dishB.priceRupees) {
                                append("${dishB.nameEnglish} is cheaper by $priceDiff rupees. ")
                            } else {
                                append("Both dishes cost ${dishA.priceRupees} rupees. ")
                            }
                            if (dishA.spiceLevel != dishB.spiceLevel) {
                                val hotter = if (dishA.spiceLevel > dishB.spiceLevel) dishA.nameEnglish else dishB.nameEnglish
                                append("$hotter is spicier. ")
                            } else {
                                append("Both have equal spice level. ")
                            }
                        }
                        onSpeakNarration(speech)
                    },
                    contentDescription = "Read comparison summary aloud",
                    size = 36.dp
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Price Comparison Box
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .neomorphicSurface(
                        shape = RoundedCornerShape(10.dp),
                        surfaceColor = NeoSurfacePressed,
                        borderColor = NeoBorder,
                        elevation = 1.dp,
                        cornerRadius = 10.dp
                    )
                    .padding(10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.AttachMoney,
                    contentDescription = null,
                    tint = NeoPrimary,
                    modifier = Modifier.size(20.dp)
                )
                Column {
                    Text(
                        text = when {
                            dishA.priceRupees < dishB.priceRupees -> "${dishA.nameEnglish} is ₹$priceDiff ($priceDiffPercentage%) cheaper than ${dishB.nameEnglish}"
                            dishA.priceRupees > dishB.priceRupees -> "${dishB.nameEnglish} is ₹$priceDiff ($priceDiffPercentage%) cheaper than ${dishA.nameEnglish}"
                            else -> "Both dishes cost the exact same: ₹${dishA.priceRupees}"
                        },
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = NeoTextPrimary
                    )
                    Text(
                        text = "₹${dishA.priceRupees} vs ₹${dishB.priceRupees}",
                        fontSize = 12.sp,
                        color = NeoTextSecondary
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Spice Comparison Box
            val spiceDiff = abs(dishA.spiceLevel - dishB.spiceLevel)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .neomorphicSurface(
                        shape = RoundedCornerShape(10.dp),
                        surfaceColor = NeoSurfacePressed,
                        borderColor = NeoBorder,
                        elevation = 1.dp,
                        cornerRadius = 10.dp
                    )
                    .padding(10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.LocalFireDepartment,
                    contentDescription = null,
                    tint = SpiceRed,
                    modifier = Modifier.size(20.dp)
                )
                Column {
                    Text(
                        text = when {
                            dishA.spiceLevel > dishB.spiceLevel -> "${dishA.nameEnglish} is spicier (+${spiceDiff} flame${if (spiceDiff > 1) "s" else ""})"
                            dishA.spiceLevel < dishB.spiceLevel -> "${dishB.nameEnglish} is spicier (+${spiceDiff} flame${if (spiceDiff > 1) "s" else ""})"
                            else -> "Equal heat intensity: both are ${getSpiceName(dishA.spiceLevel)}"
                        },
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = NeoTextPrimary
                    )
                    Text(
                        text = "${getSpiceName(dishA.spiceLevel)} vs ${getSpiceName(dishB.spiceLevel)}",
                        fontSize = 12.sp,
                        color = NeoTextSecondary
                    )
                }
            }
        }

        // ================================================================
        // SIDE-BY-SIDE CARDS (2 COLUMNS)
        // ================================================================
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Column Dish A
            Box(modifier = Modifier.weight(1f)) {
                SideBySideDishColumn(
                    dish = dishA,
                    slotLabel = "Dish A",
                    slotColor = NeoPrimary,
                    selectedLanguage = selectedLanguage,
                    isRecentlyAdded = dishA.itemId in recentlyAddedIds,
                    onAddToCart = { onAddToCart(dishA) },
                    onSpeak = {
                        val speech = "${dishA.nameEnglish}. Price: ${NumberVerbalizer.verbalizePrice(dishA.priceRupees, selectedLanguage)}. Spice level: ${getSpiceName(dishA.spiceLevel)}. ${dishA.description ?: ""}"
                        onSpeakNarration(speech)
                    }
                )
            }

            // Column Dish B
            Box(modifier = Modifier.weight(1f)) {
                SideBySideDishColumn(
                    dish = dishB,
                    slotLabel = "Dish B",
                    slotColor = NeoTextSecondary,
                    selectedLanguage = selectedLanguage,
                    isRecentlyAdded = dishB.itemId in recentlyAddedIds,
                    onAddToCart = { onAddToCart(dishB) },
                    onSpeak = {
                        val speech = "${dishB.nameEnglish}. Price: ${NumberVerbalizer.verbalizePrice(dishB.priceRupees, selectedLanguage)}. Spice level: ${getSpiceName(dishB.spiceLevel)}. ${dishB.description ?: ""}"
                        onSpeakNarration(speech)
                    }
                )
            }
        }

        // ================================================================
        // DETAILED FEATURE MATRIX TABLE
        // ================================================================
        NeomorphicCard(
            modifier = Modifier.fillMaxWidth(),
            elevation = 3.dp
        ) {
            Text(
                text = "Detailed Comparison Matrix",
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = NeoTextPrimary
            )
            Spacer(modifier = Modifier.height(10.dp))

            MatrixRow(
                title = "Price in Words",
                valA = NumberVerbalizer.verbalizePrice(dishA.priceRupees, selectedLanguage),
                valB = NumberVerbalizer.verbalizePrice(dishB.priceRupees, selectedLanguage)
            )
            HorizontalDivider(color = NeoBorder, thickness = 0.8.dp)

            MatrixRow(
                title = "Spice Level",
                valA = "${getSpiceName(dishA.spiceLevel)} (${dishA.spiceLevel}/3)",
                valB = "${getSpiceName(dishB.spiceLevel)} (${dishB.spiceLevel}/3)"
            )
            HorizontalDivider(color = NeoBorder, thickness = 0.8.dp)

            MatrixRow(
                title = "Dietary Profile",
                valA = if (dishA.isVegetarian) "Vegetarian" else "Non-Vegetarian",
                valB = if (dishB.isVegetarian) "Vegetarian" else "Non-Vegetarian"
            )
            HorizontalDivider(color = NeoBorder, thickness = 0.8.dp)

            MatrixRow(
                title = "Jain Friendly",
                valA = if (dishA.isJain) "Yes (No root veg)" else "No",
                valB = if (dishB.isJain) "Yes (No root veg)" else "No"
            )
            HorizontalDivider(color = NeoBorder, thickness = 0.8.dp)

            MatrixRow(
                title = "Nut-Free Safe",
                valA = if (dishA.isNutFree) "Nut-Free" else "Contains Nuts",
                valB = if (dishB.isNutFree) "Nut-Free" else "Contains Nuts"
            )
            HorizontalDivider(color = NeoBorder, thickness = 0.8.dp)

            MatrixRow(
                title = "Chef's Special",
                valA = if (dishA.isChefSpecial) "Signature Dish" else "Standard",
                valB = if (dishB.isChefSpecial) "Signature Dish" else "Standard"
            )
        }
    }
}

/**
 * Individual column inside the 2-dish side-by-side mode.
 */
@Composable
private fun SideBySideDishColumn(
    dish: MenuItemEntity,
    slotLabel: String,
    slotColor: Color,
    selectedLanguage: String,
    isRecentlyAdded: Boolean,
    onAddToCart: () -> Unit,
    onSpeak: () -> Unit
) {
    NeomorphicCard(
        modifier = Modifier.fillMaxWidth(),
        elevation = 4.dp
    ) {
        // Slot label pill & audio button
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(slotColor.copy(alpha = 0.15f))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(
                    text = slotLabel,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = slotColor
                )
            }

            NeomorphicIconButton(
                icon = Icons.AutoMirrored.Filled.VolumeUp,
                onClick = onSpeak,
                contentDescription = "Read ${dish.nameEnglish} details",
                size = 32.dp
            )
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Titles
        Text(
            text = dish.nameEnglish,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            color = NeoTextPrimary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )

        val nativeName = when (selectedLanguage.lowercase()) {
            "hi" -> dish.nameHindi
            "te" -> dish.nameTelugu
            else -> dish.nameHindi ?: dish.nameTelugu
        }
        if (!nativeName.isNullOrBlank()) {
            Text(
                text = nativeName,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = NeoTextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Price
        Text(
            text = "₹${dish.priceRupees}",
            fontSize = 20.sp,
            fontWeight = FontWeight.ExtraBold,
            color = NeoPrimary
        )
        Text(
            text = NumberVerbalizer.verbalizePrice(dish.priceRupees, selectedLanguage),
            fontSize = 10.sp,
            color = NeoTextMuted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        Spacer(modifier = Modifier.height(10.dp))

        // Spice Gauge Meter
        SpiceMeter(spiceLevel = dish.spiceLevel, compact = true)

        Spacer(modifier = Modifier.height(8.dp))

        // Dietary Badges
        DietaryBadge(isVegetarian = dish.isVegetarian, isJain = dish.isJain)

        Spacer(modifier = Modifier.height(6.dp))

        // Nut Allergy Badge
        NutBadge(isNutFree = dish.isNutFree, compact = true)

        Spacer(modifier = Modifier.height(10.dp))

        // Description
        Text(
            text = dish.description ?: "Freshly prepared dish",
            fontSize = 11.sp,
            color = NeoTextSecondary,
            lineHeight = 15.sp,
            maxLines = 4,
            overflow = TextOverflow.Ellipsis
        )

        Spacer(modifier = Modifier.height(14.dp))

        // Direct Order / Add to Cart Button
        AnimatedAddToCartButton(
            isRecentlyAdded = isRecentlyAdded,
            onClick = onAddToCart,
            compact = true
        )
    }
}

/**
 * Comprehensive List View with dietary, spice, and sorting filters.
 */
@Composable
private fun ComprehensiveListViewContent(
    dishes: List<MenuItemEntity>,
    selectedLanguage: String,
    dietaryFilter: DietaryFilter,
    spiceFilter: SpiceFilter,
    priceSortOrder: PriceSortOrder,
    recentlyAddedIds: Set<String>,
    onDietaryFilterChanged: (DietaryFilter) -> Unit,
    onSpiceFilterChanged: (SpiceFilter) -> Unit,
    onPriceSortOrderChanged: (PriceSortOrder) -> Unit,
    onAddToCart: (MenuItemEntity) -> Unit,
    onSelectForComparison: (MenuItemEntity) -> Unit,
    onSpeakNarration: (String) -> Unit
) {
    // Filter & Sort calculation
    val filteredList = remember(dishes, dietaryFilter, spiceFilter, priceSortOrder) {
        dishes.filter { dish ->
            val matchesDiet = when (dietaryFilter) {
                DietaryFilter.ALL -> true
                DietaryFilter.VEG -> dish.isVegetarian
                DietaryFilter.NON_VEG -> !dish.isVegetarian
                DietaryFilter.JAIN -> dish.isJain
                DietaryFilter.NUT_FREE -> dish.isNutFree
            }
            val matchesSpice = when (spiceFilter) {
                SpiceFilter.ALL -> true
                SpiceFilter.MILD -> dish.spiceLevel in 0..1
                SpiceFilter.MEDIUM -> dish.spiceLevel == 2
                SpiceFilter.SPICY -> dish.spiceLevel >= 3
            }
            matchesDiet && matchesSpice
        }.let { list ->
            when (priceSortOrder) {
                PriceSortOrder.DEFAULT -> list
                PriceSortOrder.LOW_TO_HIGH -> list.sortedBy { it.priceRupees }
                PriceSortOrder.HIGH_TO_LOW -> list.sortedByDescending { it.priceRupees }
            }
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // ================================================================
        // FILTER CONTROLS SECTION
        // ================================================================
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                // Dietary Filter Row
                Text(
                    text = "Filter by Dietary Preference",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = NeoTextPrimary
                )
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(horizontal = 2.dp)
                ) {
                    item {
                        NeomorphicPill(
                            text = "All",
                            isSelected = dietaryFilter == DietaryFilter.ALL,
                            onClick = { onDietaryFilterChanged(DietaryFilter.ALL) }
                        )
                    }
                    item {
                        NeomorphicPill(
                            text = "Vegetarian",
                            icon = Icons.Default.Eco,
                            isSelected = dietaryFilter == DietaryFilter.VEG,
                            onClick = { onDietaryFilterChanged(DietaryFilter.VEG) }
                        )
                    }
                    item {
                        NeomorphicPill(
                            text = "Non-Veg",
                            icon = Icons.Default.Restaurant,
                            isSelected = dietaryFilter == DietaryFilter.NON_VEG,
                            onClick = { onDietaryFilterChanged(DietaryFilter.NON_VEG) }
                        )
                    }
                    item {
                        NeomorphicPill(
                            text = "Jain Suitable",
                            icon = Icons.Default.Shield,
                            isSelected = dietaryFilter == DietaryFilter.JAIN,
                            onClick = { onDietaryFilterChanged(DietaryFilter.JAIN) }
                        )
                    }
                    item {
                        NeomorphicPill(
                            text = "Nut-Free Safe",
                            icon = Icons.Default.CheckCircle,
                            isSelected = dietaryFilter == DietaryFilter.NUT_FREE,
                            onClick = { onDietaryFilterChanged(DietaryFilter.NUT_FREE) }
                        )
                    }
                }

                // Spice Filter Row
                Text(
                    text = "Filter by Spice Level",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = NeoTextPrimary
                )
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(horizontal = 2.dp)
                ) {
                    item {
                        NeomorphicPill(
                            text = "All Heat",
                            isSelected = spiceFilter == SpiceFilter.ALL,
                            onClick = { onSpiceFilterChanged(SpiceFilter.ALL) }
                        )
                    }
                    item {
                        NeomorphicPill(
                            text = "Mild (0-1)",
                            icon = Icons.Default.LocalFireDepartment,
                            isSelected = spiceFilter == SpiceFilter.MILD,
                            onClick = { onSpiceFilterChanged(SpiceFilter.MILD) }
                        )
                    }
                    item {
                        NeomorphicPill(
                            text = "Medium (2)",
                            icon = Icons.Default.LocalFireDepartment,
                            isSelected = spiceFilter == SpiceFilter.MEDIUM,
                            onClick = { onSpiceFilterChanged(SpiceFilter.MEDIUM) }
                        )
                    }
                    item {
                        NeomorphicPill(
                            text = "Spicy (3)",
                            icon = Icons.Default.LocalFireDepartment,
                            isSelected = spiceFilter == SpiceFilter.SPICY,
                            onClick = { onSpiceFilterChanged(SpiceFilter.SPICY) }
                        )
                    }
                }

                // Price Sorting Row
                Text(
                    text = "Sort by Price",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = NeoTextPrimary
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NeomorphicPill(
                        text = "Default",
                        isSelected = priceSortOrder == PriceSortOrder.DEFAULT,
                        onClick = { onPriceSortOrderChanged(PriceSortOrder.DEFAULT) }
                    )
                    NeomorphicPill(
                        text = "Low to High",
                        icon = Icons.Default.ArrowUpward,
                        isSelected = priceSortOrder == PriceSortOrder.LOW_TO_HIGH,
                        onClick = { onPriceSortOrderChanged(PriceSortOrder.LOW_TO_HIGH) }
                    )
                    NeomorphicPill(
                        text = "High to Low",
                        icon = Icons.Default.ArrowDownward,
                        isSelected = priceSortOrder == PriceSortOrder.HIGH_TO_LOW,
                        onClick = { onPriceSortOrderChanged(PriceSortOrder.HIGH_TO_LOW) }
                    )
                }

                // Results count
                Text(
                    text = "Showing ${filteredList.size} of ${dishes.size} dishes",
                    fontSize = 12.sp,
                    color = NeoTextSecondary
                )
            }
        }

        // ================================================================
        // DISH CARDS LIST
        // ================================================================
        if (filteredList.isEmpty()) {
            item {
                NeomorphicCard(
                    modifier = Modifier.fillMaxWidth(),
                    elevation = 2.dp
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.SearchOff,
                            contentDescription = null,
                            tint = NeoTextMuted,
                            modifier = Modifier.size(36.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "No dishes match current filters",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = NeoTextSecondary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Try adjusting spice or dietary filters above.",
                            fontSize = 12.sp,
                            color = NeoTextMuted
                        )
                    }
                }
            }
        } else {
            items(filteredList, key = { it.itemId }) { dish ->
                CatalogDishCard(
                    dish = dish,
                    selectedLanguage = selectedLanguage,
                    isRecentlyAdded = dish.itemId in recentlyAddedIds,
                    onAddToCart = { onAddToCart(dish) },
                    onCompare = { onSelectForComparison(dish) },
                    onSpeak = {
                        val speech = "${dish.nameEnglish}. Price: ${NumberVerbalizer.verbalizePrice(dish.priceRupees, selectedLanguage)}. Spice level: ${getSpiceName(dish.spiceLevel)}. ${if (dish.isVegetarian) "Vegetarian" else "Non-Vegetarian"}. ${dish.description ?: ""}"
                        onSpeakNarration(speech)
                    }
                )
            }
        }
    }
}

/**
 * Dish card in Comprehensive List view.
 */
@Composable
private fun CatalogDishCard(
    dish: MenuItemEntity,
    selectedLanguage: String,
    isRecentlyAdded: Boolean,
    onAddToCart: () -> Unit,
    onCompare: () -> Unit,
    onSpeak: () -> Unit
) {
    NeomorphicCard(
        modifier = Modifier.fillMaxWidth(),
        elevation = 4.dp
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            // Titles & Badges
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    DietaryBadge(isVegetarian = dish.isVegetarian, isJain = dish.isJain)
                    NutBadge(isNutFree = dish.isNutFree, compact = true)
                }

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = dish.nameEnglish,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = NeoTextPrimary
                )

                val nativeName = when (selectedLanguage.lowercase()) {
                    "hi" -> dish.nameHindi
                    "te" -> dish.nameTelugu
                    else -> dish.nameHindi ?: dish.nameTelugu
                }
                if (!nativeName.isNullOrBlank()) {
                    Text(
                        text = nativeName,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = NeoTextSecondary
                    )
                }
            }

            // Price column
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "₹${dish.priceRupees}",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = NeoPrimary
                )
                Text(
                    text = NumberVerbalizer.verbalizePrice(dish.priceRupees, selectedLanguage),
                    fontSize = 10.sp,
                    color = NeoTextMuted
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Spice Level Gauge
        SpiceMeter(spiceLevel = dish.spiceLevel, compact = false)

        Spacer(modifier = Modifier.height(8.dp))

        // Description
        Text(
            text = dish.description ?: "Freshly prepared chef specialty",
            fontSize = 12.sp,
            color = NeoTextSecondary,
            lineHeight = 16.sp
        )

        Spacer(modifier = Modifier.height(14.dp))

        // Action Buttons Row: Hear TTS, Compare, Add to Cart
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Hear Description Button
            Box(
                modifier = Modifier
                    .neomorphicSurface(
                        shape = RoundedCornerShape(12.dp),
                        surfaceColor = NeoSurface,
                        borderColor = NeoBorder,
                        elevation = 2.dp,
                        cornerRadius = 12.dp
                    )
                    .clickable { onSpeak() }
                    .padding(horizontal = 10.dp, vertical = 9.dp)
                    .semantics {
                        contentDescription = "Hear audio narration for ${dish.nameEnglish}"
                        role = Role.Button
                    }
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                        contentDescription = null,
                        tint = NeoPrimary,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "Hear",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = NeoPrimary
                    )
                }
            }

            // Compare Action Button
            Box(
                modifier = Modifier
                    .neomorphicSurface(
                        shape = RoundedCornerShape(12.dp),
                        surfaceColor = NeoSurface,
                        borderColor = NeoBorder,
                        elevation = 2.dp,
                        cornerRadius = 12.dp
                    )
                    .clickable { onCompare() }
                    .padding(horizontal = 10.dp, vertical = 9.dp)
                    .semantics {
                        contentDescription = "Select ${dish.nameEnglish} for 2-dish side-by-side comparison"
                        role = Role.Button
                    }
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.CompareArrows,
                        contentDescription = null,
                        tint = NeoTextPrimary,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "Compare",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = NeoTextPrimary
                    )
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            // Add to Cart Neomorphic Button
            AnimatedAddToCartButton(
                isRecentlyAdded = isRecentlyAdded,
                onClick = onAddToCart,
                compact = false
            )
        }
    }
}

/**
 * Animated Neomorphic "Add to Cart" button with instant checkmark state transition.
 */
@Composable
private fun AnimatedAddToCartButton(
    isRecentlyAdded: Boolean,
    onClick: () -> Unit,
    compact: Boolean
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.94f else 1.0f,
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
        label = "add_btn_scale"
    )

    val buttonColor = if (isRecentlyAdded) VegGreen else NeoPrimary
    val contentColor = NeoOnPrimary

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .scale(scale)
            .neomorphicSurface(
                shape = RoundedCornerShape(12.dp),
                surfaceColor = buttonColor,
                lightColor = Color(0x6693C5FD),
                darkColor = Color(0x661E3A8A),
                borderColor = buttonColor,
                elevation = 4.dp,
                isPressed = isPressed || isRecentlyAdded,
                cornerRadius = 12.dp
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .padding(
                horizontal = if (compact) 12.dp else 16.dp,
                vertical = if (compact) 9.dp else 10.dp
            )
            .semantics {
                contentDescription = if (isRecentlyAdded) "Added to Cart" else "Add to Cart"
                role = Role.Button
            }
    ) {
        AnimatedContent(
            targetState = isRecentlyAdded,
            transitionSpec = {
                fadeIn(animationSpec = tween(200)) togetherWith fadeOut(animationSpec = tween(200))
            },
            label = "cart_state"
        ) { added ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (added) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        tint = contentColor,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "Added!",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = contentColor
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.ShoppingCart,
                        contentDescription = null,
                        tint = contentColor,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = if (compact) "Order" else "Add to Cart",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = contentColor
                    )
                }
            }
        }
    }
}

/**
 * Spice Level Gauge with flame icons & verbal classification.
 */
@Composable
private fun SpiceMeter(
    spiceLevel: Int,
    compact: Boolean,
    modifier: Modifier = Modifier
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = modifier
    ) {
        // 3 Flame indicators
        for (i in 1..3) {
            val isFlameLit = i <= spiceLevel
            Icon(
                imageVector = Icons.Default.LocalFireDepartment,
                contentDescription = null,
                tint = if (isFlameLit) SpiceRed else NeoDarkShadow.copy(alpha = 0.5f),
                modifier = Modifier.size(if (compact) 14.dp else 16.dp)
            )
        }

        Spacer(modifier = Modifier.width(2.dp))

        Text(
            text = getSpiceName(spiceLevel),
            fontSize = if (compact) 11.sp else 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (spiceLevel >= 2) SpiceRed else NeoTextSecondary
        )
    }
}

/**
 * Nut-Free / Allergy safety badge.
 */
@Composable
private fun NutBadge(
    isNutFree: Boolean,
    compact: Boolean,
    modifier: Modifier = Modifier
) {
    if (isNutFree) {
        Box(
            modifier = modifier
                .background(VegGreenContainer, RoundedCornerShape(4.dp))
                .border(1.dp, VegGreen, RoundedCornerShape(4.dp))
                .padding(horizontal = if (compact) 5.dp else 6.dp, vertical = 2.dp)
                .semantics {
                    contentDescription = "Nut-Free dish, safe for nut allergies"
                }
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Shield,
                    contentDescription = null,
                    tint = VegGreenOnContainer,
                    modifier = Modifier.size(11.dp)
                )
                Text(
                    text = "Nut-Free",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = VegGreenOnContainer
                )
            }
        }
    } else {
        Box(
            modifier = modifier
                .background(JainYellowContainer, RoundedCornerShape(4.dp))
                .border(1.dp, JainYellow, RoundedCornerShape(4.dp))
                .padding(horizontal = if (compact) 5.dp else 6.dp, vertical = 2.dp)
                .semantics {
                    contentDescription = "Contains nuts, cashew or almond allergy warning"
                }
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    tint = JainYellowOnContainer,
                    modifier = Modifier.size(11.dp)
                )
                Text(
                    text = "Contains Nuts",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = JainYellowOnContainer
                )
            }
        }
    }
}

/**
 * Detailed Feature Matrix Table Row.
 */
@Composable
private fun MatrixRow(
    title: String,
    valA: String,
    valB: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = NeoTextSecondary,
            modifier = Modifier.weight(1.1f)
        )
        Text(
            text = valA,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = NeoTextPrimary,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = valB,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = NeoTextPrimary,
            modifier = Modifier.weight(1f)
        )
    }
}

/**
 * Interactive Dialog to pick a dish for slot A or B.
 */
@Composable
private fun DishSelectionDialog(
    title: String,
    dishes: List<MenuItemEntity>,
    selectedDishId: String,
    selectedLanguage: String,
    onDismiss: () -> Unit,
    onSelectDish: (MenuItemEntity) -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .neomorphicSurface(
                    shape = RoundedCornerShape(20.dp),
                    surfaceColor = NeoSurface,
                    borderColor = NeoBorder,
                    elevation = 8.dp,
                    cornerRadius = 20.dp
                )
                .padding(18.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = title,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = NeoTextPrimary
                )
                NeomorphicIconButton(
                    icon = Icons.Default.Close,
                    onClick = onDismiss,
                    contentDescription = "Close dialog",
                    size = 36.dp
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 380.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(dishes, key = { it.itemId }) { dish ->
                    val isSelected = dish.itemId == selectedDishId
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .neomorphicSurface(
                                shape = RoundedCornerShape(12.dp),
                                surfaceColor = if (isSelected) NeoPrimaryContainer else NeoSurface,
                                borderColor = if (isSelected) NeoPrimary else NeoBorder,
                                elevation = 2.dp,
                                isPressed = isSelected,
                                cornerRadius = 12.dp
                            )
                            .clickable { onSelectDish(dish) }
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = dish.nameEnglish,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = NeoTextPrimary
                            )
                            val native = when (selectedLanguage.lowercase()) {
                                "hi" -> dish.nameHindi
                                "te" -> dish.nameTelugu
                                else -> dish.nameHindi ?: dish.nameTelugu
                            }
                            if (!native.isNullOrBlank()) {
                                Text(
                                    text = native,
                                    fontSize = 11.sp,
                                    color = NeoTextSecondary
                                )
                            }
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.padding(top = 2.dp)
                            ) {
                                DietaryBadge(isVegetarian = dish.isVegetarian, isJain = dish.isJain)
                                Text(
                                    text = getSpiceName(dish.spiceLevel),
                                    fontSize = 10.sp,
                                    color = SpiceRed,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }

                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = "₹${dish.priceRupees}",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = NeoPrimary
                            )
                            if (isSelected) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    tint = NeoPrimary,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Spice Level verbal description helper.
 */
private fun getSpiceName(level: Int): String {
    return when (level) {
        0 -> "Mild (0)"
        1 -> "Mild (1)"
        2 -> "Medium (2)"
        3 -> "Spicy (3)"
        else -> "Spicy ($level)"
    }
}
