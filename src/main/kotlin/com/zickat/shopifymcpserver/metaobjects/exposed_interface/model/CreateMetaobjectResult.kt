package com.zickat.shopifymcpserver.metaobjects.exposed_interface.model

enum class CreateMetaobjectOutcome { CREATED, FAILED }

data class CreateMetaobjectResult(
    val outcome: CreateMetaobjectOutcome,
    val text: String? = null,
    val type: String? = null,
    val failureDetail: String? = null,
) {
    companion object {
        fun created(text: String) = CreateMetaobjectResult(CreateMetaobjectOutcome.CREATED, text = text)
        fun failed(type: String, detail: String) = CreateMetaobjectResult(CreateMetaobjectOutcome.FAILED, type = type, failureDetail = detail)
    }
}
