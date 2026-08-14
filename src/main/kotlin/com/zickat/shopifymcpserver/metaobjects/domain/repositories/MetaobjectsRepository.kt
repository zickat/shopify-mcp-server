package com.zickat.shopifymcpserver.metaobjects.domain.repositories

import arrow.core.Either
import com.zickat.shopifymcpserver.metaobjects.domain.MetaobjectFieldInput
import com.zickat.shopifymcpserver.metaobjects.domain.models.MetaobjectFieldValue
import com.zickat.shopifymcpserver.metaobjects.domain.models.MetaobjectReferenceStatus
import com.zickat.shopifymcpserver.shared_kernel.UseCaseError

data class MetaobjectDefinitionSummary(
    val type: String,
    val name: String,
    val instanceCount: Int,
)

data class MetaobjectDefinitionListing(
    val definitions: List<MetaobjectDefinitionSummary>,
    val truncated: Boolean,
)

data class MetaobjectInstance(
    val id: String,
    val fields: List<MetaobjectFieldValue>,
)

data class MetaobjectInstanceListing(
    val instances: List<MetaobjectInstance>,
    val truncated: Boolean,
)

data class MetaobjectSnapshot(
    val id: String,
    val type: String,
    val fields: List<MetaobjectFieldValue>,
)

sealed interface MetaobjectWriteOutcome {
    data class Success(val metaobjectId: String) : MetaobjectWriteOutcome
    data class Failed(val detail: String) : MetaobjectWriteOutcome
}

sealed interface MetaobjectDeleteOutcome {
    data object Deleted : MetaobjectDeleteOutcome
    data class Failed(val detail: String) : MetaobjectDeleteOutcome
}

interface MetaobjectsRepository {
    fun listDefinitions(storeId: String): Either<UseCaseError, MetaobjectDefinitionListing>
    fun listInstances(storeId: String, type: String): Either<UseCaseError, MetaobjectInstanceListing>
    fun referenceStatus(storeId: String, metaobjectId: String): Either<UseCaseError, MetaobjectReferenceStatus?>
    fun get(storeId: String, metaobjectId: String): Either<UseCaseError, MetaobjectSnapshot?>
    fun create(storeId: String, type: String, fields: List<MetaobjectFieldInput>): Either<UseCaseError, MetaobjectWriteOutcome>
    fun getBeforeUpdate(storeId: String, metaobjectId: String): Either<UseCaseError, MetaobjectSnapshot?>
    fun update(storeId: String, metaobjectId: String, type: String, fields: List<MetaobjectFieldInput>): Either<UseCaseError, MetaobjectWriteOutcome>
    fun getBeforeDelete(storeId: String, metaobjectId: String): Either<UseCaseError, MetaobjectSnapshot?>
    fun delete(storeId: String, metaobjectId: String): Either<UseCaseError, MetaobjectDeleteOutcome>
}
