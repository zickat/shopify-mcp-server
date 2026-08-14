package com.zickat.shopifymcpserver.pages.exposed_interface.model

enum class UpdatePageOutcome { UPDATED, NO_OP, NOT_FOUND, FAILED }

data class UpdatePageResult(
    val outcome: UpdatePageOutcome,
    val text: String? = null,
    val pageId: String? = null,
    val failureDetail: String? = null,
) {
    companion object {
        fun updated(text: String) = UpdatePageResult(UpdatePageOutcome.UPDATED, text = text)
        val NoOp = UpdatePageResult(UpdatePageOutcome.NO_OP, text = "Aucun champ fourni — aucune modification demandée.")
        fun notFound(pageId: String) = UpdatePageResult(UpdatePageOutcome.NOT_FOUND, pageId = pageId)
        fun failed(pageId: String, detail: String) = UpdatePageResult(UpdatePageOutcome.FAILED, pageId = pageId, failureDetail = detail)
    }
}
