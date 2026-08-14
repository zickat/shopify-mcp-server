package com.zickat.shopifymcpserver.metaobjects.domain

import arrow.core.Either
import arrow.core.raise.either
import com.zickat.shopifymcpserver.metaobjects.domain.models.isOrphan
import com.zickat.shopifymcpserver.metaobjects.domain.repositories.MetaobjectDeleteOutcome
import com.zickat.shopifymcpserver.metaobjects.domain.repositories.MetaobjectsRepository
import com.zickat.shopifymcpserver.shared_kernel.UseCaseError

class DeleteMetaobjectUseCase(
    private val metaobjectsRepository: MetaobjectsRepository,
) {

    fun execute(storeId: String, metaobjectId: String, confirmReferencedDeletion: Boolean): Either<UseCaseError, DeleteMetaobjectResult> = either {
        val before = metaobjectsRepository.getBeforeDelete(storeId, metaobjectId).bind()
        when {
            before == null -> DeleteMetaobjectResult.notFound(metaobjectId)
            else -> {
                val referenceStatus = metaobjectsRepository.referenceStatus(storeId, metaobjectId).bind()
                val orphan = referenceStatus?.let(::isOrphan) ?: false
                when {
                    !orphan && !confirmReferencedDeletion -> DeleteMetaobjectResult.refused(metaobjectId, before.type, referenceStatus)
                    else -> when (val outcome = metaobjectsRepository.delete(storeId, metaobjectId).bind()) {
                        MetaobjectDeleteOutcome.Deleted ->
                            DeleteMetaobjectResult.deleted(metaobjectId, before.type, before.fields, referenceStatus)
                        is MetaobjectDeleteOutcome.Failed ->
                            DeleteMetaobjectResult.failed(metaobjectId, outcome.detail)
                    }
                }
            }
        }
    }
}
