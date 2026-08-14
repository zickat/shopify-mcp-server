package com.zickat.shopifymcpserver.metaobjects

import arrow.core.Either
import com.zickat.shopifymcpserver.metaobjects.domain.MetaobjectFieldInput
import com.zickat.shopifymcpserver.metaobjects.domain.models.MetaobjectReferenceStatus
import com.zickat.shopifymcpserver.metaobjects.domain.repositories.MetaobjectDefinitionListing
import com.zickat.shopifymcpserver.metaobjects.domain.repositories.MetaobjectDeleteOutcome
import com.zickat.shopifymcpserver.metaobjects.domain.repositories.MetaobjectInstanceListing
import com.zickat.shopifymcpserver.metaobjects.domain.repositories.MetaobjectSnapshot
import com.zickat.shopifymcpserver.metaobjects.domain.repositories.MetaobjectWriteOutcome
import com.zickat.shopifymcpserver.metaobjects.domain.repositories.MetaobjectsRepository
import com.zickat.shopifymcpserver.shared_kernel.UseCaseError

class MetaobjectsFakeRepository : MetaobjectsRepository {

    data class ReferenceStatusCall(val storeId: String, val metaobjectId: String)
    data class GetCall(val storeId: String, val metaobjectId: String)
    data class CreateCall(val storeId: String, val type: String, val fields: List<MetaobjectFieldInput>)
    data class UpdateCall(val storeId: String, val metaobjectId: String, val type: String, val fields: List<MetaobjectFieldInput>)
    data class DeleteCall(val storeId: String, val metaobjectId: String)

    var listDefinitionsResponse: Either<UseCaseError, MetaobjectDefinitionListing>? = null
    var listInstancesResponse: Either<UseCaseError, MetaobjectInstanceListing>? = null
    var referenceStatusResponses: ArrayDeque<Either<UseCaseError, MetaobjectReferenceStatus?>> = ArrayDeque()
    var getResponse: Either<UseCaseError, MetaobjectSnapshot?>? = null
    var createResponse: Either<UseCaseError, MetaobjectWriteOutcome>? = null
    var getBeforeUpdateResponse: Either<UseCaseError, MetaobjectSnapshot?>? = null
    var updateResponse: Either<UseCaseError, MetaobjectWriteOutcome>? = null
    var getBeforeDeleteResponse: Either<UseCaseError, MetaobjectSnapshot?>? = null
    var deleteResponse: Either<UseCaseError, MetaobjectDeleteOutcome>? = null

    val referenceStatusCalls = mutableListOf<ReferenceStatusCall>()
    val getCalls = mutableListOf<GetCall>()
    val createCalls = mutableListOf<CreateCall>()
    val getBeforeUpdateCalls = mutableListOf<GetCall>()
    val updateCalls = mutableListOf<UpdateCall>()
    val getBeforeDeleteCalls = mutableListOf<GetCall>()
    val deleteCalls = mutableListOf<DeleteCall>()

    fun enqueueReferenceStatus(response: Either<UseCaseError, MetaobjectReferenceStatus?>) {
        referenceStatusResponses.addLast(response)
    }

    override fun listDefinitions(storeId: String): Either<UseCaseError, MetaobjectDefinitionListing> =
        requireNotNull(listDefinitionsResponse) { "listDefinitionsResponse must be set before calling listDefinitions()" }

    override fun listInstances(storeId: String, type: String): Either<UseCaseError, MetaobjectInstanceListing> =
        requireNotNull(listInstancesResponse) { "listInstancesResponse must be set before calling listInstances()" }

    override fun referenceStatus(storeId: String, metaobjectId: String): Either<UseCaseError, MetaobjectReferenceStatus?> {
        referenceStatusCalls += ReferenceStatusCall(storeId, metaobjectId)
        return referenceStatusResponses.removeFirstOrNull()
            ?: error("referenceStatus response must be enqueued before calling referenceStatus()")
    }

    override fun get(storeId: String, metaobjectId: String): Either<UseCaseError, MetaobjectSnapshot?> {
        getCalls += GetCall(storeId, metaobjectId)
        return requireNotNull(getResponse) { "getResponse must be set before calling get()" }
    }

    override fun create(storeId: String, type: String, fields: List<MetaobjectFieldInput>): Either<UseCaseError, MetaobjectWriteOutcome> {
        createCalls += CreateCall(storeId, type, fields)
        return requireNotNull(createResponse) { "createResponse must be set before calling create()" }
    }

    override fun getBeforeUpdate(storeId: String, metaobjectId: String): Either<UseCaseError, MetaobjectSnapshot?> {
        getBeforeUpdateCalls += GetCall(storeId, metaobjectId)
        return requireNotNull(getBeforeUpdateResponse) { "getBeforeUpdateResponse must be set before calling getBeforeUpdate()" }
    }

    override fun update(storeId: String, metaobjectId: String, type: String, fields: List<MetaobjectFieldInput>): Either<UseCaseError, MetaobjectWriteOutcome> {
        updateCalls += UpdateCall(storeId, metaobjectId, type, fields)
        return requireNotNull(updateResponse) { "updateResponse must be set before calling update()" }
    }

    override fun getBeforeDelete(storeId: String, metaobjectId: String): Either<UseCaseError, MetaobjectSnapshot?> {
        getBeforeDeleteCalls += GetCall(storeId, metaobjectId)
        return requireNotNull(getBeforeDeleteResponse) { "getBeforeDeleteResponse must be set before calling getBeforeDelete()" }
    }

    override fun delete(storeId: String, metaobjectId: String): Either<UseCaseError, MetaobjectDeleteOutcome> {
        deleteCalls += DeleteCall(storeId, metaobjectId)
        return requireNotNull(deleteResponse) { "deleteResponse must be set before calling delete()" }
    }
}
