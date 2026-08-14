package com.zickat.shopifymcpserver.pages.domain

enum class UpdatePageMetafieldsOutcome { UPDATED, NOT_FOUND, FAILED }

data class UpdatePageMetafieldsResult(
    val outcome: UpdatePageMetafieldsOutcome,
    val title: String? = null,
    val metafields: List<PageMetafieldInput> = emptyList(),
    val pageId: String? = null,
    val failureDetail: String? = null,
) {
    companion object {
        fun updated(title: String, metafields: List<PageMetafieldInput>) =
            UpdatePageMetafieldsResult(UpdatePageMetafieldsOutcome.UPDATED, title = title, metafields = metafields)
        fun notFound(pageId: String) = UpdatePageMetafieldsResult(UpdatePageMetafieldsOutcome.NOT_FOUND, pageId = pageId)
        fun failed(pageId: String, detail: String) =
            UpdatePageMetafieldsResult(UpdatePageMetafieldsOutcome.FAILED, pageId = pageId, failureDetail = detail)
    }
}
