package com.zickat.shopifymcpserver.pages.domain

import arrow.core.Either
import arrow.core.raise.either
import com.zickat.shopifymcpserver.pages.domain.repositories.PageRepository
import com.zickat.shopifymcpserver.shared_kernel.UseCaseError

class GetPageMetafieldsUseCase(
    private val pageRepository: PageRepository,
) {

    fun execute(storeId: String, pageId: String, keys: List<String>?): Either<UseCaseError, GetPageMetafieldsResult> = either {
        val snapshot = pageRepository.metafields(storeId, pageId).bind()
        when {
            snapshot == null -> GetPageMetafieldsResult.notFound(pageId)
            else -> GetPageMetafieldsResult.found(snapshot.title, snapshot.metafields, keys, snapshot.truncated)
        }
    }
}
