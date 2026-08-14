package com.zickat.shopifymcpserver.pages.domain

import arrow.core.Either
import com.zickat.shopifymcpserver.pages.domain.repositories.PageRepository
import com.zickat.shopifymcpserver.pages.domain.repositories.PageWriteOutcome
import com.zickat.shopifymcpserver.shared_kernel.UseCaseError

class CreatePageUseCase(
    private val pageRepository: PageRepository,
) {

    fun execute(storeId: String, title: String, body: String, handle: String?, publish: Boolean?): Either<UseCaseError, CreatePageResult> =
        pageRepository.create(storeId, title, body, handle, publish).map { outcome ->
            when (outcome) {
                is PageWriteOutcome.Success -> CreatePageResult.created(outcome.page)
                is PageWriteOutcome.Failed -> CreatePageResult.failed(title, outcome.detail)
            }
        }
}
