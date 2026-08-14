package com.zickat.shopifymcpserver.pages.domain

enum class DeletePageOutcome { DELETED, NOT_FOUND, FAILED }

data class DeletePageResult(
    val outcome: DeletePageOutcome,
    val title: String? = null,
    val handle: String? = null,
    val pageId: String? = null,
    val failureDetail: String? = null,
) {
    companion object {
        fun deleted(title: String, handle: String) = DeletePageResult(DeletePageOutcome.DELETED, title = title, handle = handle)
        fun notFound(pageId: String) = DeletePageResult(DeletePageOutcome.NOT_FOUND, pageId = pageId)
        fun failed(pageId: String, detail: String) = DeletePageResult(DeletePageOutcome.FAILED, pageId = pageId, failureDetail = detail)
    }
}
