package com.zickat.shopifymcpserver.metaobjects.domain

import com.zickat.shopifymcpserver.metaobjects.domain.models.MetaobjectFieldValue
import com.zickat.shopifymcpserver.metaobjects.domain.models.MetaobjectReferenceStatus
import com.zickat.shopifymcpserver.metaobjects.domain.repositories.MetaobjectDefinitionSummary

data class MetaobjectInstanceWithReferences(
    val id: String,
    val fields: List<MetaobjectFieldValue>,
    val referenceStatus: MetaobjectReferenceStatus?,
)

sealed interface ListMetaobjectsResult {
    data class Definitions(val definitions: List<MetaobjectDefinitionSummary>, val truncated: Boolean) : ListMetaobjectsResult
    data class Instances(val type: String, val instances: List<MetaobjectInstanceWithReferences>, val truncated: Boolean) : ListMetaobjectsResult
}
