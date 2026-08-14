package com.zickat.shopifymcpserver.pages.domain

import arrow.core.Either
import arrow.core.raise.either
import com.zickat.shopifymcpserver.pages.domain.repositories.PageDeleteOutcome
import com.zickat.shopifymcpserver.pages.domain.repositories.PageRepository
import com.zickat.shopifymcpserver.shared_kernel.UseCaseError

class DeletePageUseCase(
    private val pageRepository: PageRepository,
) {

    fun execute(storeId: String, pageId: String): Either<UseCaseError, DeletePageResult> = either {
        val before = pageRepository.get(storeId, pageId).bind() ?: return@either DeletePageResult.notFound(pageId)

        when (val outcome = pageRepository.delete(storeId, pageId).bind()) {
            is PageDeleteOutcome.Deleted -> DeletePageResult.deleted(before.title, before.handle)
            is PageDeleteOutcome.Failed -> DeletePageResult.failed(pageId, outcome.detail)
        }
    }
}
