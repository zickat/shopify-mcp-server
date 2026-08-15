package com.zickat.shopifymcpserver.menus.domain

enum class DeleteMenuOutcome { DELETED, NOT_FOUND, REFUSED, FAILED }

data class DeleteMenuResult(
    val outcome: DeleteMenuOutcome,
    val menuId: String? = null,
    val title: String? = null,
    val handle: String? = null,
    val itemCount: Int? = null,
    val snapshot: String? = null,
    val refusals: List<String> = emptyList(),
    val failureDetail: String? = null,
) {
    companion object {
        fun deleted(title: String, handle: String, itemCount: Int, snapshot: String) =
            DeleteMenuResult(DeleteMenuOutcome.DELETED, title = title, handle = handle, itemCount = itemCount, snapshot = snapshot)

        fun notFound(menuId: String) = DeleteMenuResult(DeleteMenuOutcome.NOT_FOUND, menuId = menuId)

        fun refused(title: String, handle: String, refusals: List<String>) =
            DeleteMenuResult(DeleteMenuOutcome.REFUSED, title = title, handle = handle, refusals = refusals)

        fun failed(title: String, detail: String) = DeleteMenuResult(DeleteMenuOutcome.FAILED, title = title, failureDetail = detail)
    }
}
