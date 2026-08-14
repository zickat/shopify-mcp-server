package com.zickat.shopifymcpserver.catalog_status.spi.shopify

import arrow.core.Either
import arrow.core.raise.either
import com.zickat.shopifymcpserver.catalog_status.domain.models.CatalogStatusListing
import com.zickat.shopifymcpserver.catalog_status.domain.models.CatalogStatusResourceNode
import com.zickat.shopifymcpserver.catalog_status.domain.models.SearchResourceType
import com.zickat.shopifymcpserver.catalog_status.domain.repositories.CatalogStatusRepository
import com.zickat.shopifymcpserver.shared_kernel.MAX_SEARCH_PAGES
import com.zickat.shopifymcpserver.shared_kernel.TechnicalError
import com.zickat.shopifymcpserver.shared_kernel.UseCaseError
import com.zickat.shopifymcpserver.shopify.exposed_interface.ShopifyAdminGateway
import org.springframework.stereotype.Repository

@Repository
class CatalogStatusShopifyRepository(
    private val shopifyAdminGateway: ShopifyAdminGateway,
) : CatalogStatusRepository {

    override fun search(storeId: String, resourceType: SearchResourceType, query: String?): Either<UseCaseError, CatalogStatusListing> = either {
        val resources = mutableListOf<CatalogStatusResourceNode>()
        var cursor: String? = null
        var page = 0
        var truncated = false

        do {
            val variables = CatalogStatusGraphQL.searchVariables(query, cursor)
            val response = shopifyAdminGateway.executeGraphQL(storeId, CatalogStatusGraphQL.searchQueryFor(resourceType), variables).bind()
            val connection = CatalogStatusGraphQL.resourcesConnection(resourceType, response)
                ?: raise(TechnicalError("shopify.graphql.response.malformed"))

            resources += CatalogStatusGraphQL.resourceNodes(connection)

            val hasNextPage = CatalogStatusGraphQL.hasNextPage(connection)
            cursor = if (hasNextPage) CatalogStatusGraphQL.endCursor(connection) else null
            page += 1
            if (cursor != null && page >= MAX_SEARCH_PAGES) {
                truncated = true
                cursor = null
            }
        } while (cursor != null)

        CatalogStatusListing(resources, truncated)
    }
}
