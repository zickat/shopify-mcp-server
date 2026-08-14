package com.zickat.shopifymcpserver.metaobjects.exposed_interface.model

enum class DeleteMetaobjectOutcome { DELETED, REFUSED, NOT_FOUND, FAILED }

data class DeleteMetaobjectResult(
    val outcome: DeleteMetaobjectOutcome,
    val text: String? = null,
    val metaobjectId: String? = null,
) {
    companion object {
        fun deleted(text: String) = DeleteMetaobjectResult(DeleteMetaobjectOutcome.DELETED, text = text)
        fun refused(text: String) = DeleteMetaobjectResult(DeleteMetaobjectOutcome.REFUSED, text = text)
        fun notFound(metaobjectId: String) = DeleteMetaobjectResult(DeleteMetaobjectOutcome.NOT_FOUND, metaobjectId = metaobjectId)
        fun failed(metaobjectId: String, detail: String) =
            DeleteMetaobjectResult(DeleteMetaobjectOutcome.FAILED, metaobjectId = metaobjectId, text = detail)
    }
}
