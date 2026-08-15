package com.zickat.shopifymcpserver.menus.domain

enum class RemoveMenuItemOutcome { REMOVED, FAILED }

data class RemoveMenuItemResult(
    val outcome: RemoveMenuItemOutcome,
    val itemId: String,
    val menuTitle: String? = null,
    val menuHandle: String? = null,
    val removedIds: List<String> = emptyList(),
    val warnings: List<String> = emptyList(),
    val failureDetail: String? = null,
) {
    companion object {
        fun removed(itemId: String, menuTitle: String, menuHandle: String, removedIds: List<String>, warnings: List<String>) =
            RemoveMenuItemResult(RemoveMenuItemOutcome.REMOVED, itemId, menuTitle, menuHandle, removedIds, warnings)

        fun failed(itemId: String, detail: String) = RemoveMenuItemResult(RemoveMenuItemOutcome.FAILED, itemId, failureDetail = detail)
    }
}
