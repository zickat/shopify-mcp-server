package com.zickat.shopifymcpserver.products.domain.repositories

import arrow.core.Either
import com.zickat.shopifymcpserver.products.domain.models.OrphanScan
import com.zickat.shopifymcpserver.products.domain.models.ProductEnrichedFetch
import com.zickat.shopifymcpserver.products.domain.models.ProductRawSnapshot
import com.zickat.shopifymcpserver.products.domain.models.ProductScan
import com.zickat.shopifymcpserver.products.domain.models.ToReviewEntry
import com.zickat.shopifymcpserver.products.domain.models.ToReviewResourceType
import com.zickat.shopifymcpserver.shared_kernel.UseCaseError

sealed interface ProductMetafieldWriteOutcome {
    data object Marked : ProductMetafieldWriteOutcome
    data class Failed(val detail: String) : ProductMetafieldWriteOutcome
}

sealed interface ProductPublishOutcome {
    data class Published(val title: String, val wasNeverPublished: Boolean) : ProductPublishOutcome
    data object NotFound : ProductPublishOutcome
    data class Failed(val detail: String) : ProductPublishOutcome
}

sealed interface ProductUnpublishOutcome {
    data class Unpublished(val title: String, val countBefore: Int) : ProductUnpublishOutcome
    data class Noop(val title: String) : ProductUnpublishOutcome
    data object NotFound : ProductUnpublishOutcome
    data class Failed(val detail: String) : ProductUnpublishOutcome
}

interface ProductRepository {
    fun search(storeId: String, query: String?): Either<UseCaseError, ProductScan>
    fun rawContent(storeId: String, productId: String): Either<UseCaseError, ProductRawSnapshot?>
    fun enrichedContent(storeId: String, productId: String): Either<UseCaseError, ProductEnrichedFetch?>
    fun listToReview(storeId: String, resourceType: ToReviewResourceType): Either<UseCaseError, List<ToReviewEntry>>
    fun listOrphans(storeId: String): Either<UseCaseError, OrphanScan>
    fun markBlocked(storeId: String, resourceId: String): Either<UseCaseError, ProductMetafieldWriteOutcome>
    fun publish(storeId: String, resourceId: String): Either<UseCaseError, ProductPublishOutcome>
    fun unpublish(storeId: String, resourceId: String): Either<UseCaseError, ProductUnpublishOutcome>
}
