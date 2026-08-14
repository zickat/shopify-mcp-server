package com.zickat.shopifymcpserver.products.exposed_interface

import arrow.core.Either
import com.zickat.shopifymcpserver.products.exposed_interface.model.GetEnrichedContentResult
import com.zickat.shopifymcpserver.products.exposed_interface.model.GetRawContentResult
import com.zickat.shopifymcpserver.products.exposed_interface.model.ListOrphanProductsResult
import com.zickat.shopifymcpserver.products.exposed_interface.model.ListToReviewResult
import com.zickat.shopifymcpserver.products.exposed_interface.model.MarkBlockedResult
import com.zickat.shopifymcpserver.products.exposed_interface.model.ProductStatusFilter
import com.zickat.shopifymcpserver.products.exposed_interface.model.PublishResourceResult
import com.zickat.shopifymcpserver.products.exposed_interface.model.SearchProductsResult
import com.zickat.shopifymcpserver.products.exposed_interface.model.ToReviewResourceType
import com.zickat.shopifymcpserver.products.exposed_interface.model.UnpublishResourceResult
import com.zickat.shopifymcpserver.shared_kernel.UseCaseError
import org.springframework.modulith.NamedInterface

@NamedInterface("exposed_interface")
interface ProductsExposedService {
    fun searchProducts(storeId: String, query: String?, statusFilter: ProductStatusFilter): Either<UseCaseError, SearchProductsResult>
    fun getRawContent(storeId: String, productId: String): Either<UseCaseError, GetRawContentResult>
    fun getEnrichedContent(storeId: String, productId: String): Either<UseCaseError, GetEnrichedContentResult>
    fun listToReview(storeId: String, resourceType: ToReviewResourceType): Either<UseCaseError, ListToReviewResult>
    fun listOrphanProducts(storeId: String): Either<UseCaseError, ListOrphanProductsResult>
    fun markBlocked(storeId: String, resourceId: String): Either<UseCaseError, MarkBlockedResult>
    fun publishResource(storeId: String, resourceId: String): Either<UseCaseError, PublishResourceResult>
    fun unpublishResource(storeId: String, resourceId: String): Either<UseCaseError, UnpublishResourceResult>
}
