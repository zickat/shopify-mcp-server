package com.zickat.shopifymcpserver.metaobjects.domain

import arrow.core.Either
import arrow.core.raise.either
import com.zickat.shopifymcpserver.metaobjects.domain.models.MetaobjectFieldValue
import com.zickat.shopifymcpserver.metaobjects.domain.repositories.MetaobjectWriteOutcome
import com.zickat.shopifymcpserver.metaobjects.domain.repositories.MetaobjectsRepository
import com.zickat.shopifymcpserver.shared_kernel.UseCaseError

class UpdateMetaobjectUseCase(
    private val metaobjectsRepository: MetaobjectsRepository,
) {

    fun execute(storeId: String, metaobjectId: String, fields: List<MetaobjectFieldInput>): Either<UseCaseError, UpdateMetaobjectResult> = either {
        val before = metaobjectsRepository.getBeforeUpdate(storeId, metaobjectId).bind()
        when {
            before == null -> UpdateMetaobjectResult.notFound(metaobjectId)
            else -> when (val outcome = metaobjectsRepository.update(storeId, metaobjectId, before.type, fields).bind()) {
                is MetaobjectWriteOutcome.Success ->
                    UpdateMetaobjectResult.updated(metaobjectId, before.type, fields.map { MetaobjectFieldValue(it.key, it.value) })
                is MetaobjectWriteOutcome.Failed ->
                    UpdateMetaobjectResult.failed(metaobjectId, outcome.detail)
            }
        }
    }
}
