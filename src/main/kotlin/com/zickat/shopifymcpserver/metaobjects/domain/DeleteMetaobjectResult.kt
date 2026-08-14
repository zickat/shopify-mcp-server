package com.zickat.shopifymcpserver.metaobjects.domain

import com.zickat.shopifymcpserver.metaobjects.domain.models.MetaobjectFieldValue
import com.zickat.shopifymcpserver.metaobjects.domain.models.MetaobjectReferenceStatus

enum class DeleteMetaobjectOutcome { DELETED, REFUSED, NOT_FOUND, FAILED }

data class DeleteMetaobjectResult(
    val outcome: DeleteMetaobjectOutcome,
    val metaobjectId: String,
    val type: String? = null,
    val fields: List<MetaobjectFieldValue>? = null,
    val referenceStatus: MetaobjectReferenceStatus? = null,
    val failureDetail: String? = null,
) {
    companion object {
        fun deleted(metaobjectId: String, type: String, fields: List<MetaobjectFieldValue>, referenceStatus: MetaobjectReferenceStatus?) =
            DeleteMetaobjectResult(DeleteMetaobjectOutcome.DELETED, metaobjectId, type, fields, referenceStatus)
        fun refused(metaobjectId: String, type: String, referenceStatus: MetaobjectReferenceStatus?) =
            DeleteMetaobjectResult(DeleteMetaobjectOutcome.REFUSED, metaobjectId, type, referenceStatus = referenceStatus)
        fun notFound(metaobjectId: String) = DeleteMetaobjectResult(DeleteMetaobjectOutcome.NOT_FOUND, metaobjectId)
        fun failed(metaobjectId: String, detail: String) = DeleteMetaobjectResult(DeleteMetaobjectOutcome.FAILED, metaobjectId, failureDetail = detail)
    }
}
