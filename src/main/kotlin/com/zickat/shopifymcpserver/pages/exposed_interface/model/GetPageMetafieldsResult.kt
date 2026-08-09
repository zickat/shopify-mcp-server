package com.zickat.shopifymcpserver.pages.exposed_interface.model

enum class GetPageMetafieldsOutcome { FOUND, NOT_FOUND }

data class GetPageMetafieldsResult(
    val outcome: GetPageMetafieldsOutcome,
    val text: String? = null,
    val pageId: String? = null,
) {
    companion object {
        fun found(text: String) = GetPageMetafieldsResult(GetPageMetafieldsOutcome.FOUND, text = text)
        fun notFound(pageId: String) = GetPageMetafieldsResult(GetPageMetafieldsOutcome.NOT_FOUND, pageId = pageId)
    }
}
