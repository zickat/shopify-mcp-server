package com.zickat.shopifymcpserver.pages.domain

import arrow.core.Either
import arrow.core.raise.either
import com.zickat.shopifymcpserver.pages.domain.repositories.PageRepository
import com.zickat.shopifymcpserver.pages.domain.repositories.PageWriteOutcome
import com.zickat.shopifymcpserver.shared_kernel.UseCaseError

class TogglePagePublishUseCase(
    private val pageRepository: PageRepository,
) {

    fun execute(storeId: String, pageId: String, target: Boolean): Either<UseCaseError, TogglePagePublishResult> = either {
        val before = pageRepository.get(storeId, pageId).bind()
        when {
            before == null -> TogglePagePublishResult.notFound(pageId)
            before.isPublished == target -> TogglePagePublishResult.noOp(before.title, target, before.isPublished)
            else -> when (val outcome = pageRepository.setPublished(storeId, pageId, target).bind()) {
                is PageWriteOutcome.Success -> TogglePagePublishResult.toggled(outcome.page.title, target, outcome.page.isPublished)
                is PageWriteOutcome.Failed -> TogglePagePublishResult.failed(pageId, outcome.detail)
            }
        }
    }
}
