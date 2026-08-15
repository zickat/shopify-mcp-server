package com.zickat.shopifymcpserver.menus.domain

enum class AddMenuItemOutcome { ADDED, INVALID_ITEM, FAILED }

data class AddMenuItemResult(
    val outcome: AddMenuItemOutcome,
    val title: String,
    val menuTitle: String? = null,
    val menuHandle: String? = null,
    val parentItemId: String? = null,
    val position: Int? = null,
    val target: String? = null,
    val warnings: List<String> = emptyList(),
    val failureDetail: String? = null,
) {
    companion object {
        fun added(
            title: String,
            menuTitle: String,
            menuHandle: String,
            parentItemId: String?,
            position: Int,
            target: String,
            warnings: List<String>,
        ) = AddMenuItemResult(AddMenuItemOutcome.ADDED, title, menuTitle, menuHandle, parentItemId, position, target, warnings)

        fun invalidItem(title: String, detail: String) = AddMenuItemResult(AddMenuItemOutcome.INVALID_ITEM, title, failureDetail = detail)

        fun failed(title: String, detail: String) = AddMenuItemResult(AddMenuItemOutcome.FAILED, title, failureDetail = detail)
    }
}
