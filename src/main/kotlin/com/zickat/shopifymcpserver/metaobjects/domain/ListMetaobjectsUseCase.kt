package com.zickat.shopifymcpserver.metaobjects.domain

import arrow.core.Either
import arrow.core.raise.either
import com.zickat.shopifymcpserver.metaobjects.domain.repositories.MetaobjectsRepository
import com.zickat.shopifymcpserver.shared_kernel.UseCaseError

class ListMetaobjectsUseCase(
    private val metaobjectsRepository: MetaobjectsRepository,
) {

    fun execute(storeId: String, type: String?): Either<UseCaseError, ListMetaobjectsResult> =
        if (type == null) executeDefinitions(storeId) else executeInstances(storeId, type)

    private fun executeDefinitions(storeId: String): Either<UseCaseError, ListMetaobjectsResult> = either {
        val listing = metaobjectsRepository.listDefinitions(storeId).bind()
        ListMetaobjectsResult.Definitions(listing.definitions, listing.truncated)
    }

    private fun executeInstances(storeId: String, type: String): Either<UseCaseError, ListMetaobjectsResult> = either {
        val listing = metaobjectsRepository.listInstances(storeId, type).bind()
        val instances = listing.instances.map { instance ->
            val referenceStatus = metaobjectsRepository.referenceStatus(storeId, instance.id).bind()
            MetaobjectInstanceWithReferences(instance.id, instance.fields, referenceStatus)
        }
        ListMetaobjectsResult.Instances(type, instances, listing.truncated)
    }
}
