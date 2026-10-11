package com.sarvaai.app.ui.screens.browser

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sarvaai.app.adapt.verbalizer.NumberVerbalizer
import com.sarvaai.app.audio.earcons.EarconEngine
import com.sarvaai.app.audio.earcons.EarconType
import com.sarvaai.app.audio.haptics.HapticFeedbackEngine
import com.sarvaai.app.audio.haptics.HapticPattern
import com.sarvaai.app.data.entities.MenuItemEntity
import com.sarvaai.app.data.entities.MenuSectionEntity
import com.sarvaai.app.menu.narrator.LayoutNarrator
import com.sarvaai.app.menu.navigation.NavigationAction
import com.sarvaai.app.menu.navigation.VoiceMenuNavigator
import com.sarvaai.app.ui.components.AccessibleButton
import com.sarvaai.app.ui.theme.*

@Composable
fun MenuBrowserScreen(
    restaurantName: String,
    sections: List<MenuSectionEntity>,
    itemsBySection: Map<String, List<MenuItemEntity>>,
    selectedLanguage: String,
    hapticEngine: HapticFeedbackEngine,
    earconEngine: EarconEngine,
    isAudioPlaying: Boolean = false,
    onSpeakNarration: (String) -> Unit,
    onStopNarration: () -> Unit = {},
    onAddItemToCart: (MenuItemEntity) -> Unit,
    onOpenCart: () -> Unit = {},
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedSectionIndex by remember { mutableStateOf(0) }
    var filterVegOnly by remember { mutableStateOf(false) }
    var filterJainOnly by remember { mutableStateOf(false) }

    // Safe hardware/gesture back handling
    BackHandler {
        onStopNarration()
        onClose()
    }

    DisposableEffect(Unit) {
        onDispose {
            onStopNarration()
        }
    }

    val navigator = remember(sections, itemsBySection) {
        VoiceMenuNavigator(sections, itemsBySection)
    }

    val activeItem by navigator.activeItem.collectAsState()

    val currentSection = sections.getOrNull(selectedSectionIndex)
    val rawSectionItems = currentSection?.let { itemsBySection[it.sectionId] } ?: emptyList()

    val filteredItems = remember(rawSectionItems, filterVegOnly, filterJainOnly) {
        rawSectionItems.filter { item ->
            if (filterJainOnly && !item.isJain) false
            else if (filterVegOnly && !item.isVegetarian) false
            else true
        }
    }

    val totalItemsCount = remember(itemsBySection) {
        itemsBySection.values.sumOf { it.size }
    }

    Surface(
        modifier = modifier.fillMaxSize(),
        color = NeoBackground
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
                .systemBarsPadding()
        ) {
            // Header Bar
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(42.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(NeoPrimaryContainer)
                            .border(1.dp, NeoBorder, RoundedCornerShape(10.dp))
                    ) {
                        Icon(
                            imageVector = Icons.Default.RestaurantMenu,
                            contentDescription = null,
                            tint = NeoPrimary,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = restaurantName,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = NeoTextPrimary,
                            modifier = Modifier.semantics { heading() }
                        )
                        Text(
                            text = "Navigable Menu (${sections.size} sections, $totalItemsCount items)",
                            fontSize = 12.sp,
                            color = NeoTextSecondary
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (isAudioPlaying) {
                        IconButton(
                            onClick = onStopNarration,
                            modifier = Modifier
                                .padding(end = 6.dp)
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(NonVegRedContainer)
                                .border(1.dp, NonVegRed, CircleShape)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Stop,
                                contentDescription = "Stop speech narration",
                                tint = NonVegRed,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    IconButton(
                        onClick = {
                            hapticEngine.trigger(HapticPattern.CLICK)
                            onStopNarration()
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
                            contentDescription = "Close Menu Browser",
                            tint = NeoTextPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            // Overview Narration & Cart Buttons (Neomorphic White)
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                AccessibleButton(
                    text = "Hear Menu Layout",
                    onClick = {
                        hapticEngine.trigger(HapticPattern.CONFIRMATION)
                        earconEngine.play(EarconType.ORDER_CONFIRMED)
                        val overview = LayoutNarrator.generateLayoutSummary(restaurantName, sections, itemsBySection, selectedLanguage)
                        onSpeakNarration(overview)
                    },
                    modifier = Modifier.weight(1f),
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

                AccessibleButton(
                    text = "Voice Cart",
                    onClick = {
                        hapticEngine.trigger(HapticPattern.CLICK)
                        onStopNarration()
                        onOpenCart()
                    },
                    modifier = Modifier.weight(1f),
                    isPrimary = false,
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.ShoppingCart,
                            contentDescription = null,
                            tint = NeoTextPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Section Tabs (Neomorphic Pills)
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                itemsIndexed(sections) { index, section ->
                    val isSelected = index == selectedSectionIndex
                    val sectionTitle = section.titleNative ?: section.titleEnglish
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .height(42.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (isSelected) NeoPrimary else NeoSurface)
                            .border(
                                1.5.dp,
                                if (isSelected) NeoPrimary else NeoBorder,
                                RoundedCornerShape(10.dp)
                            )
                            .clickable {
                                hapticEngine.trigger(HapticPattern.CLICK)
                                selectedSectionIndex = index
                                val action = navigator.selectSection(index)
                                if (action is NavigationAction.ReadSection) {
                                    val summary = LayoutNarrator.generateSectionSummary(action.section, action.items, selectedLanguage)
                                    onSpeakNarration(summary)
                                }
                            }
                            .padding(horizontal = 16.dp)
                            .semantics {
                                contentDescription = "$sectionTitle section tab"
                            }
                    ) {
                        Text(
                            text = sectionTitle,
                            fontSize = 14.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            color = if (isSelected) NeoOnPrimary else NeoTextPrimary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Dietary Filters (Veg / Jain - Material Icons, Zero Emojis)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = filterVegOnly,
                    onClick = {
                        filterVegOnly = !filterVegOnly
                        hapticEngine.trigger(HapticPattern.CLICK)
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Eco,
                            contentDescription = null,
                            tint = if (filterVegOnly) VegGreen else NeoTextSecondary,
                            modifier = Modifier.size(16.dp)
                        )
                    },
                    label = {
                        Text(
                            text = "Pure Veg Only",
                            fontWeight = if (filterVegOnly) FontWeight.Bold else FontWeight.Normal
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        containerColor = NeoSurface,
                        labelColor = NeoTextPrimary,
                        selectedContainerColor = VegGreenContainer,
                        selectedLabelColor = VegGreenOnContainer
                    ),
                    border = FilterChipDefaults.filterChipBorder(
                        enabled = true,
                        selected = filterVegOnly,
                        borderColor = NeoBorder,
                        selectedBorderColor = VegGreen
                    )
                )

                FilterChip(
                    selected = filterJainOnly,
                    onClick = {
                        filterJainOnly = !filterJainOnly
                        hapticEngine.trigger(HapticPattern.CLICK)
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Shield,
                            contentDescription = null,
                            tint = if (filterJainOnly) JainYellow else NeoTextSecondary,
                            modifier = Modifier.size(16.dp)
                        )
                    },
                    label = {
                        Text(
                            text = "Jain Friendly",
                            fontWeight = if (filterJainOnly) FontWeight.Bold else FontWeight.Normal
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        containerColor = NeoSurface,
                        labelColor = NeoTextPrimary,
                        selectedContainerColor = JainYellowContainer,
                        selectedLabelColor = JainYellowOnContainer
                    ),
                    border = FilterChipDefaults.filterChipBorder(
                        enabled = true,
                        selected = filterJainOnly,
                        borderColor = NeoBorder,
                        selectedBorderColor = JainYellow
                    )
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Dish Items List (Neomorphic Item Cards)
            if (filteredItems.isEmpty()) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.SearchOff,
                            contentDescription = null,
                            tint = NeoTextMuted,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "No dishes found in this category",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = NeoTextSecondary
                        )
                    }
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    items(filteredItems, key = { it.itemId }) { item ->
                        val isHighlighted = activeItem?.itemId == item.itemId
                        val priceWords = NumberVerbalizer.verbalizePrice(item.priceRupees, selectedLanguage)

                        Card(
                            colors = CardDefaults.cardColors(containerColor = NeoSurface),
                            shape = RoundedCornerShape(16.dp),
                            border = BorderStroke(
                                width = if (isHighlighted) 2.dp else 1.5.dp,
                                color = if (isHighlighted) NeoPrimary else NeoBorder
                            ),
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .semantics(mergeDescendants = true) {
                                    contentDescription = "${item.nameEnglish}, ${if (item.isVegetarian) "Vegetarian" else "Non-vegetarian"}, ${item.priceRupees} rupees. Double tap to hear description or add to cart."
                                }
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = item.nameEnglish,
                                            fontSize = 17.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = NeoTextPrimary
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = "₹${item.priceRupees} • $priceWords",
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = NeoPrimary
                                        )
                                    }

                                    // Dietary Badge Icon
                                    Box(
                                        contentAlignment = Alignment.Center,
                                        modifier = Modifier
                                            .size(24.dp)
                                            .border(
                                                1.5.dp,
                                                if (item.isVegetarian) VegGreen else NonVegRed,
                                                RoundedCornerShape(4.dp)
                                            )
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(10.dp)
                                                .clip(CircleShape)
                                                .background(if (item.isVegetarian) VegGreen else NonVegRed)
                                        )
                                    }
                                }

                                item.description?.let { desc ->
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = desc,
                                        fontSize = 13.sp,
                                        color = NeoTextSecondary,
                                        lineHeight = 18.sp
                                    )
                                }

                                Spacer(modifier = Modifier.height(12.dp))

                                // Action Row (Read aloud & Add to cart with Neomorphic buttons)
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Box(
                                        contentAlignment = Alignment.Center,
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(42.dp)
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(NeoSurfacePressed)
                                            .border(1.dp, NeoBorder, RoundedCornerShape(10.dp))
                                            .clickable {
                                                hapticEngine.trigger(HapticPattern.CLICK)
                                                navigator.selectItem(item)
                                                val itemNarration = LayoutNarrator.generateItemSpokenDescription(item, selectedLanguage)
                                                onSpeakNarration(itemNarration)
                                            }
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                                                contentDescription = null,
                                                tint = NeoTextPrimary,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = "Read Aloud",
                                                fontSize = 13.sp,
                                                color = NeoTextPrimary,
                                                fontWeight = FontWeight.Medium
                                            )
                                        }
                                    }

                                    Box(
                                        contentAlignment = Alignment.Center,
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(42.dp)
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(NeoPrimary)
                                            .border(1.dp, NeoPrimaryContainer, RoundedCornerShape(10.dp))
                                            .clickable {
                                                hapticEngine.trigger(HapticPattern.CONFIRMATION)
                                                earconEngine.play(EarconType.ORDER_CONFIRMED)
                                                onAddItemToCart(item)
                                                val confirmText = when (selectedLanguage) {
                                                    "te" -> "${item.nameEnglish} ఆర్డర్‌లో చేర్చబడింది."
                                                    "hi" -> "${item.nameEnglish} ऑर्डर में जोड़ दिया गया है।"
                                                    else -> "Added ${item.nameEnglish} to your order."
                                                }
                                                onSpeakNarration(confirmText)
                                            }
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                imageVector = Icons.Default.Add,
                                                contentDescription = null,
                                                tint = NeoOnPrimary,
                                                modifier = Modifier.size(18.dp)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = "Add to Order",
                                                fontSize = 13.sp,
                                                color = NeoOnPrimary,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Bottom Bar with View Cart Quick Action
            Card(
                colors = CardDefaults.cardColors(containerColor = NeoSurface),
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.5.dp, NeoBorder),
                elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        hapticEngine.trigger(HapticPattern.CLICK)
                        onStopNarration()
                        onOpenCart()
                    }
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(NeoPrimaryContainer)
                        ) {
                            Icon(
                                imageVector = Icons.Default.ShoppingCart,
                                contentDescription = null,
                                tint = NeoPrimary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Ready to Order?",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = NeoTextPrimary
                            )
                            Text(
                                text = "Tap to review items in voice cart",
                                fontSize = 12.sp,
                                color = NeoTextSecondary
                            )
                        }
                    }

                    Icon(
                        imageVector = Icons.Default.ChevronRight,
                        contentDescription = "Open Cart",
                        tint = NeoPrimary,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}
