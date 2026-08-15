package com.zickat.shopifymcpserver.menus.domain

enum class CreateMenuOutcome { CREATED, INVALID_ITEM, FAILED }

data class CreateMenuResult(
    val outcome: CreateMenuOutcome,
    val title: String,
    val handle: String? = null,
    val menuId: String? = null,
    val block: String? = null,
    val warnings: List<String> = emptyList(),
    val failureDetail: String? = null,
) {
    companion object {
        fun created(title: String, handle: String, menuId: String, block: String, warnings: List<String>) =
            CreateMenuResult(CreateMenuOutcome.CREATED, title, handle, menuId, block, warnings)

        fun invalidItem(title: String, detail: String) = CreateMenuResult(CreateMenuOutcome.INVALID_ITEM, title, failureDetail = detail)

        fun failed(title: String, detail: String) = CreateMenuResult(CreateMenuOutcome.FAILED, title, failureDetail = detail)
    }
}
