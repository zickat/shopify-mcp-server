package com.zickat.shopifymcpserver.menus.domain

enum class ReorderMenuItemsOutcome { REORDERED, FAILED }

data class ReorderMenuItemsResult(
    val outcome: ReorderMenuItemsOutcome,
    val menuTitle: String? = null,
    val menuHandle: String? = null,
    val scopeLabel: String? = null,
    val orderedItemIds: List<String> = emptyList(),
    val warnings: List<String> = emptyList(),
    val failureDetail: String? = null,
) {
    companion object {
        fun reordered(menuTitle: String, menuHandle: String, scopeLabel: String, orderedItemIds: List<String>, warnings: List<String>) =
            ReorderMenuItemsResult(ReorderMenuItemsOutcome.REORDERED, menuTitle, menuHandle, scopeLabel, orderedItemIds, warnings)

        fun failed(detail: String) = ReorderMenuItemsResult(ReorderMenuItemsOutcome.FAILED, failureDetail = detail)
    }
}
