package com.zickat.shopifymcpserver.pages.exposed_interface.model

enum class DeletePageOutcome { DELETED, NOT_FOUND, FAILED }

data class DeletePageResult(
    val outcome: DeletePageOutcome,
    val text: String? = null,
    val pageId: String? = null,
    val failureDetail: String? = null,
) {
    companion object {
        fun deleted(text: String) = DeletePageResult(DeletePageOutcome.DELETED, text = text)
        fun notFound(pageId: String) = DeletePageResult(DeletePageOutcome.NOT_FOUND, pageId = pageId)
        fun failed(pageId: String, detail: String) = DeletePageResult(DeletePageOutcome.FAILED, pageId = pageId, failureDetail = detail)
    }
}
