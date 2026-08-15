package com.zickat.shopifymcpserver.menus.domain

enum class UpdateMenuItemOutcome { UPDATED, NO_FIELD_PROVIDED, AMBIGUOUS_TARGET, INVALID_TARGET, FAILED }

data class UpdateMenuItemResult(
    val outcome: UpdateMenuItemOutcome,
    val itemId: String,
    val menuTitle: String? = null,
    val menuHandle: String? = null,
    val titleChange: Pair<String, String>? = null,
    val targetChange: Pair<String, String>? = null,
    val childCount: Int? = null,
    val warnings: List<String> = emptyList(),
    val failureDetail: String? = null,
) {
    companion object {
        fun updated(
            itemId: String,
            menuTitle: String,
            menuHandle: String,
            titleChange: Pair<String, String>?,
            targetChange: Pair<String, String>?,
            childCount: Int,
            warnings: List<String>,
        ) = UpdateMenuItemResult(UpdateMenuItemOutcome.UPDATED, itemId, menuTitle, menuHandle, titleChange, targetChange, childCount, warnings)

        fun noFieldProvided(itemId: String) = UpdateMenuItemResult(UpdateMenuItemOutcome.NO_FIELD_PROVIDED, itemId)

        fun ambiguousTarget(itemId: String) = UpdateMenuItemResult(UpdateMenuItemOutcome.AMBIGUOUS_TARGET, itemId)

        fun invalidTarget(itemId: String, detail: String) = UpdateMenuItemResult(UpdateMenuItemOutcome.INVALID_TARGET, itemId, failureDetail = detail)

        fun failed(itemId: String, detail: String) = UpdateMenuItemResult(UpdateMenuItemOutcome.FAILED, itemId, failureDetail = detail)
    }
}
