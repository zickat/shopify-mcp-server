package com.zickat.shopifymcpserver.metaobjects.domain.models

data class MetaobjectReferencer(
    val referencerType: String,
    val referencerTitle: String,
    val referencerId: String?,
    val namespace: String,
    val key: String,
)

sealed interface MetaobjectReferenceStatus {
    data object Orphan : MetaobjectReferenceStatus
    data class Referenced(val references: List<MetaobjectReferencer>, val truncated: Boolean) : MetaobjectReferenceStatus
    data object Uncertain : MetaobjectReferenceStatus
}
