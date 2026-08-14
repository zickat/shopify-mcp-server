package com.zickat.shopifymcpserver.pages.domain

import arrow.core.Either
import com.zickat.shopifymcpserver.pages.domain.repositories.PageRepository
import com.zickat.shopifymcpserver.shared_kernel.UseCaseError

class ListPagesUseCase(
    private val pageRepository: PageRepository,
) {

    fun execute(storeId: String, query: String?): Either<UseCaseError, ListPagesResult> {
        val searchQuery = query?.trim()?.takeIf { it.isNotEmpty() }
        return pageRepository.list(storeId, searchQuery).map { listing ->
            ListPagesResult(listing.pages, listing.truncated, searchQuery != null)
        }
    }
}
