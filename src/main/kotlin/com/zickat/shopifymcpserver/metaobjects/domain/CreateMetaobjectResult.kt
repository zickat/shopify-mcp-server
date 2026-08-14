package com.zickat.shopifymcpserver.metaobjects.domain

import com.zickat.shopifymcpserver.metaobjects.domain.models.MetaobjectFieldValue

enum class CreateMetaobjectOutcome { CREATED, FAILED }

data class CreateMetaobjectResult(
    val outcome: CreateMetaobjectOutcome,
    val type: String,
    val metaobjectId: String? = null,
    val fields: List<MetaobjectFieldValue>? = null,
    val failureDetail: String? = null,
) {
    companion object {
        fun created(type: String, metaobjectId: String, fields: List<MetaobjectFieldValue>) =
            CreateMetaobjectResult(CreateMetaobjectOutcome.CREATED, type, metaobjectId, fields)
        fun failed(type: String, detail: String) = CreateMetaobjectResult(CreateMetaobjectOutcome.FAILED, type, failureDetail = detail)
    }
}
