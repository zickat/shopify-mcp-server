package com.zickat.shopifymcpserver.products.domain

import arrow.core.Either
import com.zickat.shopifymcpserver.products.exposed_interface.ProductsExposedService
import com.zickat.shopifymcpserver.products.exposed_interface.model.GetEnrichedContentResult
import com.zickat.shopifymcpserver.products.exposed_interface.model.GetRawContentResult
import com.zickat.shopifymcpserver.products.exposed_interface.model.ListOrphanProductsResult
import com.zickat.shopifymcpserver.products.exposed_interface.model.ListToReviewResult
import com.zickat.shopifymcpserver.products.exposed_interface.model.ProductStatusFilter
import com.zickat.shopifymcpserver.products.exposed_interface.model.SearchProductsResult
import com.zickat.shopifymcpserver.products.exposed_interface.model.ToReviewResourceType
import com.zickat.shopifymcpserver.shared_kernel.UseCaseError
import org.springframework.stereotype.Service

@Service
class ProductsExposedServiceImpl(
    private val searchProductsUseCase: SearchProductsUseCase,
    private val getRawContentUseCase: GetRawContentUseCase,
    private val getEnrichedContentUseCase: GetEnrichedContentUseCase,
    private val listToReviewUseCase: ListToReviewUseCase,
    private val listOrphanProductsUseCase: ListOrphanProductsUseCase,
) : ProductsExposedService {

    override fun searchProducts(storeId: String, query: String?, statusFilter: ProductStatusFilter): Either<UseCaseError, SearchProductsResult> =
        searchProductsUseCase.execute(storeId, query, statusFilter)

    override fun getRawContent(storeId: String, productId: String): Either<UseCaseError, GetRawContentResult> =
        getRawContentUseCase.execute(storeId, productId)

    override fun getEnrichedContent(storeId: String, productId: String): Either<UseCaseError, GetEnrichedContentResult> =
        getEnrichedContentUseCase.execute(storeId, productId)

    override fun listToReview(storeId: String, resourceType: ToReviewResourceType): Either<UseCaseError, ListToReviewResult> =
        listToReviewUseCase.execute(storeId, resourceType)

    override fun listOrphanProducts(storeId: String): Either<UseCaseError, ListOrphanProductsResult> =
        listOrphanProductsUseCase.execute(storeId)
}
