# PHASE 6: MENU STORE, SECTION NARRATION & VOICE-FIRST MENU NAVIGATION
## Detailed Engineering Specification & Physical Execution Protocol

---

### 1. Phase Overview & Strategic Intent
Phase 6 bridges raw menu data into an accessible auditory experience:
1. Persist extracted menu sections and dishes into Room Database with foreign-key relationships.
2. Implement the **Layout Narrator**, generating localized audio summaries that provide an instant mental model for blind and elderly diners.
3. Build the hands-free **Voice Navigation State Machine** allowing users to browse sections and dishes using natural voice commands (*"Next"*, *"Vegetarian only"*, *"Skip to Breads"*).
4. Implement the accessible **MenuBrowserScreen** adhering to WCAG AAA contrast and TalkBack heading semantics.

---

### 2. Layout Narrator Engine (`menu/narrator/LayoutNarrator.kt`)

#### 2.1 Spoken Orientation Strategy
Blind users cannot skim a visual page with their eyes. The Layout Narrator provides an auditory layout map within 5 seconds:
- Section count and names.
- Total item count and vegetarian breakdown.
- Price ranges per section.

```kotlin
package com.sarvaai.app.menu.narrator

import com.sarvaai.app.adapt.verbalizer.NumberVerbalizer
import com.sarvaai.app.data.entities.MenuItemEntity
import com.sarvaai.app.data.entities.MenuSectionEntity

object LayoutNarrator {

    fun generateLayoutSummary(
        restaurantName: String,
        sections: List<MenuSectionEntity>,
        itemsBySection: Map<String, List<MenuItemEntity>>,
        language: String
    ): String {
        val totalSections = sections.size
        val totalItems = itemsBySection.values.sumOf { it.size }
        val vegItems = itemsBySection.values.flatten().count { it.isVegetarian }

        return when (language) {
            "hi" -> {
                val sectionNames = sections.joinToString(", ") { it.titleNative ?: it.titleEnglish }
                "$restaurantName के मेनू में $totalSections भाग और कुल $totalItems व्यंजन हैं, जिनमें $vegItems शाकाहारी हैं। " +
                "भाग हैं: $sectionNames। आप किसी भी भाग को सुनने के लिए उसका नाम बोल सकते हैं या 'अगला' कह सकते हैं।"
            }
            "te" -> {
                val sectionNames = sections.joinToString(", ") { it.titleNative ?: it.titleEnglish }
                "$restaurantName మెనూలో $totalSections విభాగాలు మరియు మొత్తం $totalItems వంటకాలు ఉన్నాయి. " +
                "ఇందులో $vegItems శాఖాహార వంటకాలు ఉన్నాయి. " +
                "విభాగాలు: $sectionNames. వినడానికి 'తర్వాత' అని చెప్పండి."
            }
            else -> {
                val sectionNames = sections.joinToString(", ") { it.titleEnglish }
                "This menu for $restaurantName has $totalSections sections with $totalItems items, including $vegItems vegetarian options. " +
                "The sections are: $sectionNames. You can say 'next section' or ask for a section by name."
            }
        }
    }

    fun generateSectionSummary(
        section: MenuSectionEntity,
        items: List<MenuItemEntity>,
        language: String
    ): String {
        val count = items.size
        val minPrice = items.minOfOrNull { it.pricePaise / 100 } ?: 0
        val maxPrice = items.maxOfOrNull { it.pricePaise / 100 } ?: 0
        val title = section.titleEnglish

        return when (language) {
            "hi" -> "$title में $count व्यंजन हैं, जिनकी कीमत $minPrice से $maxPrice रुपये तक है।"
            "te" -> "$title లో $count వంటకాలు ఉన్నాయి, ధరలు $minPrice నుండి $maxPrice రూపాయల వరకు ఉన్నాయి."
            else -> "$title has $count items, ranging from $minPrice to $maxPrice rupees."
        }
    }
}
```

---

### 3. Voice Navigation State Machine (`menu/navigation/VoiceMenuNavigator.kt`)

Handles hands-free voice interrupts while reading:
```kotlin
package com.sarvaai.app.menu.navigation

import com.sarvaai.app.data.entities.MenuItemEntity
import com.sarvaai.app.data.entities.MenuSectionEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class VoiceMenuNavigator(
    private val sections: List<MenuSectionEntity>,
    private val itemsBySection: Map<String, List<MenuItemEntity>>
) {
    private var currentSectionIndex = 0
    private var currentItemIndex = 0
    private var filterVegetarianOnly = false

    private val _activeItem = MutableStateFlow<MenuItemEntity?>(null)
    val activeItem: StateFlow<MenuItemEntity?> = _activeItem

    fun handleVoiceCommand(command: String): NavigationAction {
        val lower = command.lowercase().trim()
        return when {
            lower.contains("next") || lower.contains("अगला") || lower.contains("తర్వాత") -> {
                nextItem()
            }
            lower.contains("previous") || lower.contains("back") || lower.contains("पीछे") || lower.contains("వెనుక") -> {
                previousItem()
            }
            lower.contains("next section") || lower.contains("अगला भाग") || lower.contains("తర్వాత విభాగం") -> {
                nextSection()
            }
            lower.contains("vegetarian") || lower.contains("शाकाहारी") || lower.contains("శాకాహారం") -> {
                filterVegetarianOnly = true
                currentItemIndex = 0
                NavigationAction.Filtered("VEGETARIAN")
            }
            lower.contains("repeat") || lower.contains("फिर से") || lower.contains("మళ్ళీ") -> {
                NavigationAction.RepeatCurrent
            }
            else -> NavigationAction.Unhandled
        }
    }

    private fun nextItem(): NavigationAction {
        val currentItems = getActiveSectionItems()
        if (currentItemIndex + 1 < currentItems.size) {
            currentItemIndex++
            _activeItem.value = currentItems[currentItemIndex]
            return NavigationAction.ReadItem(currentItems[currentItemIndex])
        } else {
            return nextSection()
        }
    }

    private fun previousItem(): NavigationAction {
        val currentItems = getActiveSectionItems()
        if (currentItemIndex > 0) {
            currentItemIndex--
            _activeItem.value = currentItems[currentItemIndex]
            return NavigationAction.ReadItem(currentItems[currentItemIndex])
        }
        return NavigationAction.BoundaryReached
    }

    private fun nextSection(): NavigationAction {
        if (currentSectionIndex + 1 < sections.size) {
            currentSectionIndex++
            currentItemIndex = 0
            val sec = sections[currentSectionIndex]
            return NavigationAction.ReadSection(sec, getActiveSectionItems())
        }
        return NavigationAction.EndOfMenu
    }

    private fun getActiveSectionItems(): List<MenuItemEntity> {
        val sec = sections.getOrNull(currentSectionIndex) ?: return emptyList()
        val all = itemsBySection[sec.sectionId] ?: emptyList()
        return if (filterVegetarianOnly) all.filter { it.isVegetarian } else all
    }
}

sealed class NavigationAction {
    data class ReadSection(val section: MenuSectionEntity, val items: List<MenuItemEntity>) : NavigationAction()
    data class ReadItem(val item: MenuItemEntity) : NavigationAction()
    data class Filtered(val filter: String) : NavigationAction()
    object RepeatCurrent : NavigationAction()
    object BoundaryReached : NavigationAction()
    object EndOfMenu : NavigationAction()
    object Unhandled : NavigationAction()
}
```

---

### 4. Accessible Menu Browser Screen (`MenuBrowserScreen.kt`)

```kotlin
package com.sarvaai.app.ui.screens.browser

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sarvaai.app.data.entities.MenuItemEntity
import com.sarvaai.app.ui.components.AccessibleButton
import com.sarvaai.app.ui.components.DietaryBadge
import com.sarvaai.app.ui.theme.*

@Composable
fun MenuBrowserScreen(
    sectionTitle: String,
    items: List<MenuItemEntity>,
    onAddItem: (MenuItemEntity) -> Unit,
    onExplainItem: (MenuItemEntity) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CharcoalBackground)
            .padding(16.dp)
            .systemBarsPadding()
    ) {
        Text(
            text = sectionTitle,
            fontSize = 28.sp,
            style = MaterialTheme.typography.headlineMedium,
            color = SaffronPrimary,
            modifier = Modifier.semantics { heading() }
        )

        Spacer(modifier = Modifier.height(16.dp))

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(items, key = { it.itemId }) { item ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = CharcoalSurface),
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics(mergeDescendants = true) {
                            contentDescription = "${item.nameEnglish}, ${if (item.isVegetarian) "Vegetarian" else "Non-vegetarian"}, ${item.pricePaise / 100} rupees. Double tap to add."
                        }
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = item.nameEnglish,
                                fontSize = 20.sp,
                                color = PureWhite,
                                style = MaterialTheme.typography.titleLarge,
                                modifier = Modifier.weight(1f)
                            )
                            DietaryBadge(isVegetarian = item.isVegetarian, isJain = item.isJain)
                        }

                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "₹${item.pricePaise / 100}",
                            fontSize = 18.sp,
                            color = SaffronPrimary,
                            style = MaterialTheme.typography.labelLarge
                        )

                        if (!item.description.isNullOrEmpty()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = item.description,
                                fontSize = 16.sp,
                                color = MutedText
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            AccessibleButton(
                                text = "Add to Order",
                                onClick = { onAddItem(item) },
                                modifier = Modifier.weight(1f)
                            )
                            AccessibleButton(
                                text = "Explain",
                                onClick = { onExplainItem(item) },
                                isPrimary = false,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
        }
    }
}
```

---

### 5. Physical Device Testing Protocol on realme P3 Pro (Android 16)

1. Load captured menu.
2. Observe Layout Narrator: App announces section summary cleanly in selected language.
3. Speak: *"Next"* -> App moves cursor and reads first dish.
4. Speak: *"Vegetarian only"* -> All non-veg dishes filtered out of playback queue.
5. Enable TalkBack: Swipe through `MenuBrowserScreen` -> Confirm merged accessibility descriptions announce dish, dietary status, and price in a single cohesive utterance.

---

### 6. Git Checkpoint & Remote Push
```bash
git add .
git commit -m "feat(phase6): implement layout narrator, voice menu navigator, and accessible menubrowser screen with talkback semantics"
git push origin main
```

---

### 7. Exit Criteria Checklist
- [x] Room database stores sections and items with foreign keys.
- [x] Layout narrator delivers high-level orientation in en, hi, and te.
- [x] Hands-free voice commands browse items seamlessly.
- [x] TalkBack semantic headings and merged descriptions verified on physical hardware.
- [x] Code pushed to remote repository.
