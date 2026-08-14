package com.zickat.shopifymcpserver.products.domain

import arrow.core.Either
import com.zickat.shopifymcpserver.products.domain.repositories.ProductRepository
import com.zickat.shopifymcpserver.products.domain.repositories.ProductUnpublishOutcome
import com.zickat.shopifymcpserver.shared_kernel.UseCaseError

class UnpublishResourceUseCase(
    private val productRepository: ProductRepository,
) {

    fun execute(storeId: String, resourceId: String): Either<UseCaseError, UnpublishResourceResult> =
        productRepository.unpublish(storeId, resourceId).map { outcome ->
            when (outcome) {
                is ProductUnpublishOutcome.Unpublished -> UnpublishResourceResult.unpublished(outcome.title, outcome.countBefore)
                is ProductUnpublishOutcome.Noop -> UnpublishResourceResult.noop(outcome.title)
                ProductUnpublishOutcome.NotFound -> UnpublishResourceResult.notFound(resourceId)
                is ProductUnpublishOutcome.Failed -> UnpublishResourceResult.failed(outcome.detail)
            }
        }
}
