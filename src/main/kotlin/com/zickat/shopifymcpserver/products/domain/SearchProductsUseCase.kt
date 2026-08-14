package com.zickat.shopifymcpserver.products.domain

import arrow.core.Either
import com.zickat.shopifymcpserver.products.domain.models.ProductFact
import com.zickat.shopifymcpserver.products.domain.models.ProductStatusFilter
import com.zickat.shopifymcpserver.products.domain.repositories.ProductRepository
import com.zickat.shopifymcpserver.shared_kernel.UseCaseError

class SearchProductsUseCase(
    private val productRepository: ProductRepository,
) {

    fun execute(storeId: String, query: String?, statusFilter: ProductStatusFilter): Either<UseCaseError, SearchProductsResult> =
        productRepository.search(storeId, query).map { scan ->
            SearchProductsResult(
                entries = scan.entries.filter { it.matches(statusFilter) }.map { it.toListingEntry() },
                truncated = scan.truncated,
            )
        }

    private fun ProductFact.matches(statusFilter: ProductStatusFilter): Boolean = when (statusFilter) {
        ProductStatusFilter.UNTREATED -> contentStatusValue == null && !hasSummaryPoints
        ProductStatusFilter.TO_REVIEW -> contentStatusValue == "to_review"
        ProductStatusFilter.BLOCKED -> contentStatusValue == "blocked"
        ProductStatusFilter.ALL -> true
    }
}
