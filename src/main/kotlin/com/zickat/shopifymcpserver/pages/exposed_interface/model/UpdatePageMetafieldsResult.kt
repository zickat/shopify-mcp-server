package com.zickat.shopifymcpserver.pages.exposed_interface.model

enum class UpdatePageMetafieldsOutcome { UPDATED, NOT_FOUND, FAILED }

data class UpdatePageMetafieldsResult(
    val outcome: UpdatePageMetafieldsOutcome,
    val text: String? = null,
    val pageId: String? = null,
    val failureDetail: String? = null,
) {
    companion object {
        fun updated(text: String) = UpdatePageMetafieldsResult(UpdatePageMetafieldsOutcome.UPDATED, text = text)
        fun notFound(pageId: String) = UpdatePageMetafieldsResult(UpdatePageMetafieldsOutcome.NOT_FOUND, pageId = pageId)
        fun failed(pageId: String, detail: String) =
            UpdatePageMetafieldsResult(UpdatePageMetafieldsOutcome.FAILED, pageId = pageId, failureDetail = detail)
    }
}
