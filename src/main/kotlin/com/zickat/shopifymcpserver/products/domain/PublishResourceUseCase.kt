package com.zickat.shopifymcpserver.products.domain

import arrow.core.Either
import com.zickat.shopifymcpserver.products.domain.repositories.ProductPublishOutcome
import com.zickat.shopifymcpserver.products.domain.repositories.ProductRepository
import com.zickat.shopifymcpserver.shared_kernel.UseCaseError

class PublishResourceUseCase(
    private val productRepository: ProductRepository,
) {

    fun execute(storeId: String, resourceId: String): Either<UseCaseError, PublishResourceResult> =
        productRepository.publish(storeId, resourceId).map { outcome ->
            when (outcome) {
                is ProductPublishOutcome.Published -> PublishResourceResult.published(outcome.title, outcome.wasNeverPublished)
                ProductPublishOutcome.NotFound -> PublishResourceResult.notFound(resourceId)
                is ProductPublishOutcome.Failed -> PublishResourceResult.failed(outcome.detail)
            }
        }
}
