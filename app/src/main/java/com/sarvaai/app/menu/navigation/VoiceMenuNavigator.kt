package com.sarvaai.app.menu.navigation

import com.sarvaai.app.data.entities.MenuItemEntity
import com.sarvaai.app.data.entities.MenuSectionEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

sealed class NavigationAction {
    data class ReadSection(val section: MenuSectionEntity, val items: List<MenuItemEntity>) : NavigationAction()
    data class ReadItem(val item: MenuItemEntity) : NavigationAction()
    data class FilterApplied(val filter: String, val matchingCount: Int) : NavigationAction()
    data class AddCurrentToCart(val item: MenuItemEntity) : NavigationAction()
    object RepeatCurrent : NavigationAction()
    object BoundaryReached : NavigationAction()
    object EndOfMenu : NavigationAction()
    object Unhandled : NavigationAction()
}

class VoiceMenuNavigator(
    private val sections: List<MenuSectionEntity>,
    private val itemsBySection: Map<String, List<MenuItemEntity>>
) {
    private var currentSectionIndex = 0
    private var currentItemIndex = -1 // -1 means section intro is active
    private var filterVegetarianOnly = false

    private val _activeItem = MutableStateFlow<MenuItemEntity?>(null)
    val activeItem: StateFlow<MenuItemEntity?> = _activeItem

    private val _activeSection = MutableStateFlow<MenuSectionEntity?>(sections.firstOrNull())
    val activeSection: StateFlow<MenuSectionEntity?> = _activeSection

    fun getCurrentSection(): MenuSectionEntity? = sections.getOrNull(currentSectionIndex)

    fun getCurrentItem(): MenuItemEntity? {
        val currentItems = getActiveSectionItems()
        return if (currentItemIndex in currentItems.indices) currentItems[currentItemIndex] else null
    }

    fun handleVoiceCommand(command: String): NavigationAction {
        val lower = command.lowercase().trim()

        return when {
            // Add to Cart
            lower.contains("order") || lower.contains("add") || lower.contains("take this") ||
                    lower.contains("కావాలి") || lower.contains("ఆర్డర్") || lower.contains("చేర్చండి") ||
                    lower.contains("चाहिए") || lower.contains("जोड़ो") || lower.contains("ऑर्डर") -> {
                val item = getCurrentItem()
                if (item != null) {
                    NavigationAction.AddCurrentToCart(item)
                } else {
                    NavigationAction.Unhandled
                }
            }

            // Next Section
            lower.contains("next section") || lower.contains("skip section") ||
                    lower.contains("తర్వాత విభాగం") || lower.contains("अगला भाग") -> {
                nextSection()
            }

            // Next Item / Forward
            lower.contains("next") || lower.contains("forward") || lower.contains("more") ||
                    lower.contains("తర్వాత") || lower.contains("తరువాత") || lower.contains("ఇంకోటి") ||
                    lower.contains("अगला") || lower.contains("आगे") || lower.contains("दूसरा") -> {
                nextItem()
            }

            // Previous Item / Back
            lower.contains("previous") || lower.contains("back") ||
                    lower.contains("వెనుక") || lower.contains("మునుపటి") ||
                    lower.contains("पिछला") || lower.contains("पीछे") -> {
                previousItem()
            }

            // Vegetarian Filter
            lower.contains("vegetarian") || lower.contains("veg only") ||
                    lower.contains("శాకాహారం") || lower.contains("శాఖాహారం") ||
                    lower.contains("शाकाहारी") -> {
                filterVegetarianOnly = true
                currentItemIndex = 0
                val filtered = getActiveSectionItems()
                _activeItem.value = filtered.firstOrNull()
                NavigationAction.FilterApplied("VEGETARIAN", filtered.size)
            }

            // Repeat Current
            lower.contains("repeat") || lower.contains("again") ||
                    lower.contains("మళ్ళీ") || lower.contains("ఇంకోసారి") ||
                    lower.contains("फिर से") || lower.contains("दोबारा") -> {
                NavigationAction.RepeatCurrent
            }

            else -> NavigationAction.Unhandled
        }
    }

    fun selectSection(sectionIndex: Int): NavigationAction {
        if (sectionIndex in sections.indices) {
            currentSectionIndex = sectionIndex
            currentItemIndex = -1
            val sec = sections[currentSectionIndex]
            _activeSection.value = sec
            _activeItem.value = null
            return NavigationAction.ReadSection(sec, getActiveSectionItems())
        }
        return NavigationAction.BoundaryReached
    }

    fun selectItem(item: MenuItemEntity): NavigationAction {
        val currentItems = getActiveSectionItems()
        val index = currentItems.indexOfFirst { it.itemId == item.itemId }
        if (index != -1) {
            currentItemIndex = index
            _activeItem.value = item
            return NavigationAction.ReadItem(item)
        }
        return NavigationAction.Unhandled
    }

    private fun nextItem(): NavigationAction {
        val currentItems = getActiveSectionItems()
        if (currentItemIndex + 1 < currentItems.size) {
            currentItemIndex++
            val item = currentItems[currentItemIndex]
            _activeItem.value = item
            return NavigationAction.ReadItem(item)
        } else {
            return nextSection()
        }
    }

    private fun previousItem(): NavigationAction {
        val currentItems = getActiveSectionItems()
        if (currentItemIndex > 0) {
            currentItemIndex--
            val item = currentItems[currentItemIndex]
            _activeItem.value = item
            return NavigationAction.ReadItem(item)
        }
        return NavigationAction.BoundaryReached
    }

    private fun nextSection(): NavigationAction {
        if (currentSectionIndex + 1 < sections.size) {
            currentSectionIndex++
            currentItemIndex = -1
            val sec = sections[currentSectionIndex]
            _activeSection.value = sec
            _activeItem.value = null
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
