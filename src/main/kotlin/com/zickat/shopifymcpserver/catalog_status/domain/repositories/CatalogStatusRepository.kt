package com.zickat.shopifymcpserver.catalog_status.domain.repositories

import arrow.core.Either
import com.zickat.shopifymcpserver.catalog_status.domain.models.CatalogStatusListing
import com.zickat.shopifymcpserver.catalog_status.domain.models.SearchResourceType
import com.zickat.shopifymcpserver.shared_kernel.UseCaseError

interface CatalogStatusRepository {
    fun search(storeId: String, resourceType: SearchResourceType, query: String?): Either<UseCaseError, CatalogStatusListing>
}
