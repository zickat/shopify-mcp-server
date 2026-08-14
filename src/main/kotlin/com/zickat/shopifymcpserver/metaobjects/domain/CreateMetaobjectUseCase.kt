package com.zickat.shopifymcpserver.metaobjects.domain

import arrow.core.Either
import arrow.core.raise.either
import com.zickat.shopifymcpserver.metaobjects.domain.models.MetaobjectFieldValue
import com.zickat.shopifymcpserver.metaobjects.domain.repositories.MetaobjectWriteOutcome
import com.zickat.shopifymcpserver.metaobjects.domain.repositories.MetaobjectsRepository
import com.zickat.shopifymcpserver.shared_kernel.UseCaseError

class CreateMetaobjectUseCase(
    private val metaobjectsRepository: MetaobjectsRepository,
) {

    fun execute(storeId: String, type: String, fields: List<MetaobjectFieldInput>): Either<UseCaseError, CreateMetaobjectResult> = either {
        when (val outcome = metaobjectsRepository.create(storeId, type, fields).bind()) {
            is MetaobjectWriteOutcome.Success ->
                CreateMetaobjectResult.created(type, outcome.metaobjectId, fields.map { MetaobjectFieldValue(it.key, it.value) })
            is MetaobjectWriteOutcome.Failed ->
                CreateMetaobjectResult.failed(type, outcome.detail)
        }
    }
}
