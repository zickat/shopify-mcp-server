package com.zickat.shopifymcpserver.pages.domain

import arrow.core.Either
import arrow.core.raise.either
import com.zickat.shopifymcpserver.pages.domain.repositories.PageMetafieldsWriteOutcome
import com.zickat.shopifymcpserver.pages.domain.repositories.PageRepository
import com.zickat.shopifymcpserver.shared_kernel.UseCaseError

class UpdatePageMetafieldsUseCase(
    private val pageRepository: PageRepository,
) {

    fun execute(storeId: String, pageId: String, metafields: List<PageMetafieldInput>): Either<UseCaseError, UpdatePageMetafieldsResult> = either {
        val snapshot = pageRepository.metafields(storeId, pageId).bind()
        when {
            snapshot == null -> UpdatePageMetafieldsResult.notFound(pageId)
            else -> when (val outcome = pageRepository.setMetafields(storeId, pageId, metafields).bind()) {
                is PageMetafieldsWriteOutcome.Updated -> UpdatePageMetafieldsResult.updated(snapshot.title, metafields)
                is PageMetafieldsWriteOutcome.Failed -> UpdatePageMetafieldsResult.failed(pageId, outcome.detail)
            }
        }
    }
}
