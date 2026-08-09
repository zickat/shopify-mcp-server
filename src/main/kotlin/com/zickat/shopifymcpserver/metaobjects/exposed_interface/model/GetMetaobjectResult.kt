package com.zickat.shopifymcpserver.metaobjects.exposed_interface.model

enum class GetMetaobjectOutcome { FOUND, NOT_FOUND }

data class GetMetaobjectResult(
    val outcome: GetMetaobjectOutcome,
    val text: String? = null,
    val metaobjectId: String? = null,
) {
    companion object {
        fun found(text: String) = GetMetaobjectResult(GetMetaobjectOutcome.FOUND, text = text)
        fun notFound(metaobjectId: String) = GetMetaobjectResult(GetMetaobjectOutcome.NOT_FOUND, metaobjectId = metaobjectId)
    }
}
