package com.zickat.shopifymcpserver.metaobjects.exposed_interface.model

enum class UpdateMetaobjectOutcome { UPDATED, NOT_FOUND, FAILED }

data class UpdateMetaobjectResult(
    val outcome: UpdateMetaobjectOutcome,
    val text: String? = null,
    val metaobjectId: String? = null,
    val failureDetail: String? = null,
) {
    companion object {
        fun updated(text: String) = UpdateMetaobjectResult(UpdateMetaobjectOutcome.UPDATED, text = text)
        fun notFound(metaobjectId: String) = UpdateMetaobjectResult(UpdateMetaobjectOutcome.NOT_FOUND, metaobjectId = metaobjectId)
        fun failed(metaobjectId: String, detail: String) =
            UpdateMetaobjectResult(UpdateMetaobjectOutcome.FAILED, metaobjectId = metaobjectId, failureDetail = detail)
    }
}
