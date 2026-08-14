package com.zickat.shopifymcpserver.pages.domain

enum class TogglePagePublishOutcome { TOGGLED, NO_OP, NOT_FOUND, FAILED }

data class TogglePagePublishResult(
    val outcome: TogglePagePublishOutcome,
    val title: String? = null,
    val targetPublished: Boolean? = null,
    val isPublished: Boolean? = null,
    val pageId: String? = null,
    val failureDetail: String? = null,
) {
    companion object {
        fun toggled(title: String, targetPublished: Boolean, isPublished: Boolean) =
            TogglePagePublishResult(TogglePagePublishOutcome.TOGGLED, title = title, targetPublished = targetPublished, isPublished = isPublished)
        fun noOp(title: String, targetPublished: Boolean, isPublished: Boolean) =
            TogglePagePublishResult(TogglePagePublishOutcome.NO_OP, title = title, targetPublished = targetPublished, isPublished = isPublished)
        fun notFound(pageId: String) = TogglePagePublishResult(TogglePagePublishOutcome.NOT_FOUND, pageId = pageId)
        fun failed(pageId: String, detail: String) = TogglePagePublishResult(TogglePagePublishOutcome.FAILED, pageId = pageId, failureDetail = detail)
    }
}
