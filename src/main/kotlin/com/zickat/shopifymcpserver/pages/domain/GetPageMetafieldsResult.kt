package com.zickat.shopifymcpserver.pages.domain

enum class GetPageMetafieldsOutcome { FOUND, NOT_FOUND }

data class GetPageMetafieldsResult(
    val outcome: GetPageMetafieldsOutcome,
    val pageId: String? = null,
    val title: String? = null,
    val metafields: List<PageMetafield> = emptyList(),
    val requestedKeys: List<String>? = null,
    val truncated: Boolean = false,
) {
    companion object {
        fun found(title: String, metafields: List<PageMetafield>, requestedKeys: List<String>?, truncated: Boolean) =
            GetPageMetafieldsResult(GetPageMetafieldsOutcome.FOUND, title = title, metafields = metafields, requestedKeys = requestedKeys, truncated = truncated)
        fun notFound(pageId: String) = GetPageMetafieldsResult(GetPageMetafieldsOutcome.NOT_FOUND, pageId = pageId)
    }
}
