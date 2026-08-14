package com.zickat.shopifymcpserver.pages.exposed_interface.model

enum class TogglePagePublishOutcome { TOGGLED, NO_OP, NOT_FOUND, FAILED }

data class TogglePagePublishResult(
    val outcome: TogglePagePublishOutcome,
    val text: String? = null,
    val pageId: String? = null,
    val failureDetail: String? = null,
) {
    companion object {
        fun toggled(text: String) = TogglePagePublishResult(TogglePagePublishOutcome.TOGGLED, text = text)
        fun noOp(text: String) = TogglePagePublishResult(TogglePagePublishOutcome.NO_OP, text = text)
        fun notFound(pageId: String) = TogglePagePublishResult(TogglePagePublishOutcome.NOT_FOUND, pageId = pageId)
        fun failed(pageId: String, detail: String) = TogglePagePublishResult(TogglePagePublishOutcome.FAILED, pageId = pageId, failureDetail = detail)
    }
}
