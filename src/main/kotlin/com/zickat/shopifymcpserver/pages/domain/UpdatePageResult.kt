package com.zickat.shopifymcpserver.pages.domain

enum class UpdatePageOutcome { UPDATED, NO_OP, NOT_FOUND, FAILED }

data class UpdatePageResult(
    val outcome: UpdatePageOutcome,
    val title: String? = null,
    val pageId: String? = null,
    val changedFields: List<String> = emptyList(),
    val effectiveHandle: String? = null,
    val failureDetail: String? = null,
) {
    companion object {
        fun updated(title: String, pageId: String, changedFields: List<String>, effectiveHandle: String?) =
            UpdatePageResult(UpdatePageOutcome.UPDATED, title = title, pageId = pageId, changedFields = changedFields, effectiveHandle = effectiveHandle)
        val NoOp = UpdatePageResult(UpdatePageOutcome.NO_OP)
        fun notFound(pageId: String) = UpdatePageResult(UpdatePageOutcome.NOT_FOUND, pageId = pageId)
        fun failed(pageId: String, detail: String) = UpdatePageResult(UpdatePageOutcome.FAILED, pageId = pageId, failureDetail = detail)
    }
}
