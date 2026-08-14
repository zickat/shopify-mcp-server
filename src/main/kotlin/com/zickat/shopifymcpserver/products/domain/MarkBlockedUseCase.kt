package com.zickat.shopifymcpserver.products.domain

import arrow.core.Either
import com.zickat.shopifymcpserver.products.domain.repositories.ProductMetafieldWriteOutcome
import com.zickat.shopifymcpserver.products.domain.repositories.ProductRepository
import com.zickat.shopifymcpserver.shared_kernel.UseCaseError

class MarkBlockedUseCase(
    private val productRepository: ProductRepository,
) {

    fun execute(storeId: String, resourceId: String): Either<UseCaseError, MarkBlockedResult> =
        productRepository.markBlocked(storeId, resourceId).map { outcome ->
            when (outcome) {
                ProductMetafieldWriteOutcome.Marked -> MarkBlockedResult.Marked
                is ProductMetafieldWriteOutcome.Failed -> MarkBlockedResult.failed(outcome.detail)
            }
        }
}
