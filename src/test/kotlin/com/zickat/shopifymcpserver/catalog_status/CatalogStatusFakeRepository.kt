package com.zickat.shopifymcpserver.catalog_status

import arrow.core.Either
import com.zickat.shopifymcpserver.catalog_status.domain.models.CatalogStatusListing
import com.zickat.shopifymcpserver.catalog_status.domain.models.SearchResourceType
import com.zickat.shopifymcpserver.catalog_status.domain.repositories.CatalogStatusRepository
import com.zickat.shopifymcpserver.shared_kernel.UseCaseError

class CatalogStatusFakeRepository : CatalogStatusRepository {

    data class SearchCall(val storeId: String, val resourceType: SearchResourceType, val query: String?)

    var searchResponse: Either<UseCaseError, CatalogStatusListing>? = null

    val searchCalls = mutableListOf<SearchCall>()

    override fun search(storeId: String, resourceType: SearchResourceType, query: String?): Either<UseCaseError, CatalogStatusListing> {
        searchCalls += SearchCall(storeId, resourceType, query)
        return requireNotNull(searchResponse) { "searchResponse must be set before calling search()" }
    }
}
