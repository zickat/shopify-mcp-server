package com.zickat.shopifymcpserver.products.domain

import arrow.core.Either
import com.zickat.shopifymcpserver.products.domain.models.ToReviewResourceType
import com.zickat.shopifymcpserver.products.domain.repositories.ProductRepository
import com.zickat.shopifymcpserver.shared_kernel.UseCaseError

class ListToReviewUseCase(
    private val productRepository: ProductRepository,
) {

    fun execute(storeId: String, resourceType: ToReviewResourceType): Either<UseCaseError, ListToReviewResult> =
        productRepository.listToReview(storeId, resourceType).map { entries -> ListToReviewResult(resourceType, entries) }
}
