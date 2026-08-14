package com.zickat.shopifymcpserver.products.domain

import arrow.core.Either
import com.zickat.shopifymcpserver.products.domain.repositories.ProductRepository
import com.zickat.shopifymcpserver.shared_kernel.UseCaseError

class ListOrphanProductsUseCase(
    private val productRepository: ProductRepository,
) {

    fun execute(storeId: String): Either<UseCaseError, ListOrphanProductsResult> =
        productRepository.listOrphans(storeId).map { scan ->
            ListOrphanProductsResult(
                orphans = scan.orphans.map { it.toListingEntry() },
                collectionsTruncated = scan.collectionsTruncated,
                collectionsWithTruncatedProducts = scan.collectionsWithTruncatedProducts,
                productsTruncated = scan.productsTruncated,
            )
        }
}
