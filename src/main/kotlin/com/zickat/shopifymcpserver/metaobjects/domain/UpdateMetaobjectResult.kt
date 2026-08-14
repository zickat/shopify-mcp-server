package com.zickat.shopifymcpserver.metaobjects.domain

import com.zickat.shopifymcpserver.metaobjects.domain.models.MetaobjectFieldValue

enum class UpdateMetaobjectOutcome { UPDATED, NOT_FOUND, FAILED }

data class UpdateMetaobjectResult(
    val outcome: UpdateMetaobjectOutcome,
    val metaobjectId: String,
    val type: String? = null,
    val fields: List<MetaobjectFieldValue>? = null,
    val failureDetail: String? = null,
) {
    companion object {
        fun updated(metaobjectId: String, type: String, fields: List<MetaobjectFieldValue>) =
            UpdateMetaobjectResult(UpdateMetaobjectOutcome.UPDATED, metaobjectId, type, fields)
        fun notFound(metaobjectId: String) = UpdateMetaobjectResult(UpdateMetaobjectOutcome.NOT_FOUND, metaobjectId)
        fun failed(metaobjectId: String, detail: String) = UpdateMetaobjectResult(UpdateMetaobjectOutcome.FAILED, metaobjectId, failureDetail = detail)
    }
}
