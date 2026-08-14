package com.zickat.shopifymcpserver.metaobjects.domain

import arrow.core.Either
import arrow.core.raise.either
import com.zickat.shopifymcpserver.metaobjects.domain.repositories.MetaobjectsRepository
import com.zickat.shopifymcpserver.shared_kernel.UseCaseError

class GetMetaobjectUseCase(
    private val metaobjectsRepository: MetaobjectsRepository,
) {

    fun execute(storeId: String, metaobjectId: String): Either<UseCaseError, GetMetaobjectResult> = either {
        val snapshot = metaobjectsRepository.get(storeId, metaobjectId).bind()
        if (snapshot == null) {
            GetMetaobjectResult.notFound(metaobjectId)
        } else {
            val referenceStatus = metaobjectsRepository.referenceStatus(storeId, snapshot.id).bind()
            GetMetaobjectResult.found(snapshot.id, snapshot.type, snapshot.fields, referenceStatus)
        }
    }
}
