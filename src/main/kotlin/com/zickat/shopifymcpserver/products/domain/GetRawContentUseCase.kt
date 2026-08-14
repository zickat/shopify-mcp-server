package com.zickat.shopifymcpserver.products.domain

import arrow.core.Either
import com.zickat.shopifymcpserver.products.domain.repositories.ProductRepository
import com.zickat.shopifymcpserver.shared_kernel.UseCaseError

class GetRawContentUseCase(
    private val productRepository: ProductRepository,
) {

    fun execute(storeId: String, productId: String): Either<UseCaseError, GetRawContentResult> =
        productRepository.rawContent(storeId, productId).map { snapshot ->
            snapshot?.let { GetRawContentResult.found(it) } ?: GetRawContentResult.notFound(productId)
        }
}
