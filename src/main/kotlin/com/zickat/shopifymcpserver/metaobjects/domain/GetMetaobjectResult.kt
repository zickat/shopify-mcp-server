package com.zickat.shopifymcpserver.metaobjects.domain

import com.zickat.shopifymcpserver.metaobjects.domain.models.MetaobjectFieldValue
import com.zickat.shopifymcpserver.metaobjects.domain.models.MetaobjectReferenceStatus

enum class GetMetaobjectOutcome { FOUND, NOT_FOUND }

data class GetMetaobjectResult(
    val outcome: GetMetaobjectOutcome,
    val metaobjectId: String,
    val type: String? = null,
    val fields: List<MetaobjectFieldValue>? = null,
    val referenceStatus: MetaobjectReferenceStatus? = null,
) {
    companion object {
        fun found(metaobjectId: String, type: String, fields: List<MetaobjectFieldValue>, referenceStatus: MetaobjectReferenceStatus?) =
            GetMetaobjectResult(GetMetaobjectOutcome.FOUND, metaobjectId, type, fields, referenceStatus)
        fun notFound(metaobjectId: String) = GetMetaobjectResult(GetMetaobjectOutcome.NOT_FOUND, metaobjectId)
    }
}
