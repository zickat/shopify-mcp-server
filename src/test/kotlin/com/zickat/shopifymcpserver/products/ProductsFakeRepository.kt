package com.zickat.shopifymcpserver.products

import arrow.core.Either
import com.zickat.shopifymcpserver.products.domain.models.OrphanScan
import com.zickat.shopifymcpserver.products.domain.models.ProductEnrichedFetch
import com.zickat.shopifymcpserver.products.domain.models.ProductRawSnapshot
import com.zickat.shopifymcpserver.products.domain.models.ProductScan
import com.zickat.shopifymcpserver.products.domain.models.ToReviewEntry
import com.zickat.shopifymcpserver.products.domain.models.ToReviewResourceType
import com.zickat.shopifymcpserver.products.domain.repositories.ProductMetafieldWriteOutcome
import com.zickat.shopifymcpserver.products.domain.repositories.ProductPublishOutcome
import com.zickat.shopifymcpserver.products.domain.repositories.ProductRepository
import com.zickat.shopifymcpserver.products.domain.repositories.ProductUnpublishOutcome
import com.zickat.shopifymcpserver.shared_kernel.UseCaseError

class ProductsFakeRepository : ProductRepository {

    data class SearchCall(val storeId: String, val query: String?)
    data class ListToReviewCall(val storeId: String, val resourceType: ToReviewResourceType)
    data class ResourceCall(val storeId: String, val resourceId: String)

    var searchResponse: Either<UseCaseError, ProductScan>? = null
    var rawContentResponse: Either<UseCaseError, ProductRawSnapshot?>? = null
    var enrichedContentResponse: Either<UseCaseError, ProductEnrichedFetch?>? = null
    var listToReviewResponse: Either<UseCaseError, List<ToReviewEntry>>? = null
    var listOrphansResponse: Either<UseCaseError, OrphanScan>? = null
    var markBlockedResponse: Either<UseCaseError, ProductMetafieldWriteOutcome>? = null
    var publishResponse: Either<UseCaseError, ProductPublishOutcome>? = null
    var unpublishResponse: Either<UseCaseError, ProductUnpublishOutcome>? = null

    val searchCalls = mutableListOf<SearchCall>()
    val rawContentCalls = mutableListOf<ResourceCall>()
    val enrichedContentCalls = mutableListOf<ResourceCall>()
    val listToReviewCalls = mutableListOf<ListToReviewCall>()
    val listOrphansCalls = mutableListOf<String>()
    val markBlockedCalls = mutableListOf<ResourceCall>()
    val publishCalls = mutableListOf<ResourceCall>()
    val unpublishCalls = mutableListOf<ResourceCall>()

    override fun search(storeId: String, query: String?): Either<UseCaseError, ProductScan> {
        searchCalls += SearchCall(storeId, query)
        return requireNotNull(searchResponse) { "searchResponse must be set before calling search()" }
    }

    override fun rawContent(storeId: String, productId: String): Either<UseCaseError, ProductRawSnapshot?> {
        rawContentCalls += ResourceCall(storeId, productId)
        return requireNotNull(rawContentResponse) { "rawContentResponse must be set before calling rawContent()" }
    }

    override fun enrichedContent(storeId: String, productId: String): Either<UseCaseError, ProductEnrichedFetch?> {
        enrichedContentCalls += ResourceCall(storeId, productId)
        return requireNotNull(enrichedContentResponse) { "enrichedContentResponse must be set before calling enrichedContent()" }
    }

    override fun listToReview(storeId: String, resourceType: ToReviewResourceType): Either<UseCaseError, List<ToReviewEntry>> {
        listToReviewCalls += ListToReviewCall(storeId, resourceType)
        return requireNotNull(listToReviewResponse) { "listToReviewResponse must be set before calling listToReview()" }
    }

    override fun listOrphans(storeId: String): Either<UseCaseError, OrphanScan> {
        listOrphansCalls += storeId
        return requireNotNull(listOrphansResponse) { "listOrphansResponse must be set before calling listOrphans()" }
    }

    override fun markBlocked(storeId: String, resourceId: String): Either<UseCaseError, ProductMetafieldWriteOutcome> {
        markBlockedCalls += ResourceCall(storeId, resourceId)
        return requireNotNull(markBlockedResponse) { "markBlockedResponse must be set before calling markBlocked()" }
    }

    override fun publish(storeId: String, resourceId: String): Either<UseCaseError, ProductPublishOutcome> {
        publishCalls += ResourceCall(storeId, resourceId)
        return requireNotNull(publishResponse) { "publishResponse must be set before calling publish()" }
    }

    override fun unpublish(storeId: String, resourceId: String): Either<UseCaseError, ProductUnpublishOutcome> {
        unpublishCalls += ResourceCall(storeId, resourceId)
        return requireNotNull(unpublishResponse) { "unpublishResponse must be set before calling unpublish()" }
    }
}
