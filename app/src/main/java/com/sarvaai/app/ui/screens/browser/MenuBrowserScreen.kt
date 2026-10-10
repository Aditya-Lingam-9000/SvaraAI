package com.sarvaai.app.ui.screens.browser

import androidx.compose.animation.AnimatedVisibility
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
import com.sarvaai.app.ui.components.AccessibleCard
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

    Surface(
        modifier = modifier.fillMaxSize(),
        color = CharcoalBackground
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
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = restaurantName,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = SaffronPrimary,
                        modifier = Modifier.semantics { heading() }
                    )
                    Text(
                        text = "Voice-Navigable Menu (${sections.size} sections)",
                        fontSize = 13.sp,
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
                        Icon(
                            imageVector = Icons.Default.Stop,
                            contentDescription = "Stop speech narration",
                            tint = NonVegRed
                        )
                    }
                }

                IconButton(
                    onClick = {
                        hapticEngine.trigger(HapticPattern.CLICK)
                        onStopNarration()
                        onClose()
                    }
                ) {
                    Icon(imageVector = Icons.Default.Close, contentDescription = "Close Menu Browser", tint = PureWhite)
                }
            }

            // Overview Narration & Cart Buttons
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                AccessibleButton(
                    text = "🎧 Hear Layout",
                    onClick = {
                        hapticEngine.trigger(HapticPattern.CONFIRMATION)
                        earconEngine.play(EarconType.ORDER_CONFIRMED)
                        val overview = LayoutNarrator.generateLayoutSummary(restaurantName, sections, itemsBySection, selectedLanguage)
                        onSpeakNarration(overview)
                    },
                    modifier = Modifier.weight(1f),
                    isPrimary = true
                )

                AccessibleButton(
                    text = "🛒 Voice Cart (P7)",
                    onClick = {
                        hapticEngine.trigger(HapticPattern.CLICK)
                        onStopNarration()
                        onOpenCart()
                    },
                    modifier = Modifier.weight(1f),
                    isPrimary = false
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Section Tabs
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
                            .height(44.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (isSelected) SaffronPrimary else CharcoalSurfaceVariant)
                            .border(1.5.dp, if (isSelected) SaffronPrimary else Color(0xFF444444), RoundedCornerShape(10.dp))
                            .clickable {
                                hapticEngine.trigger(HapticPattern.CLICK)
                                selectedSectionIndex = index
                                val action = navigator.selectSection(index)
                                if (action is NavigationAction.ReadSection) {
                                    val summary = LayoutNarrator.generateSectionSummary(action.section, action.items, selectedLanguage)
                                    onSpeakNarration(summary)
                                }
                            }
                            .padding(horizontal = 14.dp)
                            .semantics {
                                contentDescription = "$sectionTitle section tab"
                            }
                    ) {
                        Text(
                            text = sectionTitle,
                            fontSize = 14.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            color = if (isSelected) SaffronOnPrimary else PureWhite
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Dietary Filters (Veg / Jain)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = filterVegOnly,
                    onClick = {
                        filterVegOnly = !filterVegOnly
                        hapticEngine.trigger(HapticPattern.CLICK)
                    },
                    label = { Text("🟢 Pure Veg Only") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = VegGreen.copy(alpha = 0.3f),
                        selectedLabelColor = VegGreen
                    )
                )

                FilterChip(
                    selected = filterJainOnly,
                    onClick = {
                        filterJainOnly = !filterJainOnly
                        hapticEngine.trigger(HapticPattern.CLICK)
                    },
                    label = { Text("🟨 Jain Friendly") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = HighContrastYellow.copy(alpha = 0.3f),
                        selectedLabelColor = HighContrastYellow
                    )
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Dish Items List
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
                        colors = CardDefaults.cardColors(containerColor = if (isHighlighted) CharcoalSurfaceVariant else CharcoalSurface),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(
                                width = if (isHighlighted) 2.dp else 1.dp,
                                color = if (isHighlighted) SaffronPrimary else Color(0xFF333333),
                                shape = RoundedCornerShape(12.dp)
                            )
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
                                        color = PureWhite
                                    )
                                    Text(
                                        text = "₹${item.priceRupees} • $priceWords",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = SaffronPrimary
                                    )
                                }

                                // Dietary Icon
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier
                                        .size(24.dp)
                                        .border(1.5.dp, if (item.isVegetarian) VegGreen else NonVegRed, RoundedCornerShape(4.dp))
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
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = desc,
                                    fontSize = 12.sp,
                                    color = SubtitleText,
                                    lineHeight = 16.sp
                                )
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            // Action Row (Read aloud & Add to cart)
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(40.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(CharcoalSurfaceVariant)
                                        .border(1.dp, Color(0xFF555555), RoundedCornerShape(8.dp))
                                        .clickable {
                                            hapticEngine.trigger(HapticPattern.CLICK)
                                            navigator.selectItem(item)
                                            val itemNarration = LayoutNarrator.generateItemSpokenDescription(item, selectedLanguage)
                                            onSpeakNarration(itemNarration)
                                        }
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(imageVector = Icons.AutoMirrored.Filled.VolumeUp, contentDescription = null, tint = PureWhite, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(text = "Read Aloud", fontSize = 12.sp, color = PureWhite, fontWeight = FontWeight.Medium)
                                    }
                                }

                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(40.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(SaffronPrimary)
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
                                        Icon(imageVector = Icons.Default.Add, contentDescription = null, tint = SaffronOnPrimary, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(text = "Add to Order", fontSize = 12.sp, color = SaffronOnPrimary, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}
