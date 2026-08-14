package com.zickat.shopifymcpserver.pages.domain

import arrow.core.Either
import arrow.core.raise.either
import com.zickat.shopifymcpserver.pages.domain.repositories.PagePatch
import com.zickat.shopifymcpserver.pages.domain.repositories.PageRepository
import com.zickat.shopifymcpserver.pages.domain.repositories.PageWriteOutcome
import com.zickat.shopifymcpserver.shared_kernel.UseCaseError

class UpdatePageUseCase(
    private val pageRepository: PageRepository,
) {

    fun execute(storeId: String, pageId: String, title: String?, body: String?, handle: String?): Either<UseCaseError, UpdatePageResult> = either {
        if (title == null && body == null && handle == null) {
            return@either UpdatePageResult.NoOp
        }

        pageRepository.get(storeId, pageId).bind() ?: return@either UpdatePageResult.notFound(pageId)

        val changedFields = listOfNotNull(
            "title".takeIf { title != null },
            "body".takeIf { body != null },
            "handle".takeIf { handle != null },
        )
        val outcome = pageRepository.update(storeId, pageId, PagePatch(title, body, handle)).bind()

        when (outcome) {
            is PageWriteOutcome.Success -> UpdatePageResult.updated(
                title = outcome.page.title,
                pageId = outcome.page.id,
                changedFields = changedFields,
                effectiveHandle = if (handle != null) outcome.page.handle else null,
            )
            is PageWriteOutcome.Failed -> UpdatePageResult.failed(pageId, outcome.detail)
        }
    }
}
