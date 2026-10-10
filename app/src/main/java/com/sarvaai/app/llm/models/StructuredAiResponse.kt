package com.sarvaai.app.llm.models

import kotlinx.serialization.Serializable

@Serializable
data class StructuredAiResponse(
    val intent: String, // "ORDER_ITEMS", "ASK_MENU", "DIETARY_QUERY", "CONFIRM_ORDER", "GENERAL_QUERY"
    val headline: String,
    val spoken: String,
    val caption: String,
    val bullets: List<String> = emptyList(),
    val table: StructuredTable? = null,
    val tableNarration: String? = null,
    val simpleLevel: Int = 1,
    val confidence: String = "high",
    val needsStaffConfirmation: Boolean = false,
    val actions: List<UiAction> = emptyList(),
    val followupPrompt: String? = null
)

@Serializable
data class StructuredTable(
    val columns: List<String>,
    val rows: List<List<String>>
)

@Serializable
data class UiAction(
    val id: String,
    val label: String,
    val actionType: String, // "ADD_TO_CART", "ASK_WAITER", "NAVIGATE_SECTION"
    val payload: String? = null
)
