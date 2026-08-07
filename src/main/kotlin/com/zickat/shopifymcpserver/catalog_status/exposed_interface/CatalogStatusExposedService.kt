package com.zickat.shopifymcpserver.catalog_status.exposed_interface

import arrow.core.Either
import com.zickat.shopifymcpserver.catalog_status.exposed_interface.model.SearchResourceType
import com.zickat.shopifymcpserver.catalog_status.exposed_interface.model.SearchResourcesResult
import com.zickat.shopifymcpserver.catalog_status.exposed_interface.model.SearchStatusFilter
import com.zickat.shopifymcpserver.shared_kernel.UseCaseError
import org.springframework.modulith.NamedInterface

@NamedInterface("exposed_interface")
interface CatalogStatusExposedService {
    fun searchResources(
        storeId: String,
        resourceType: SearchResourceType,
        query: String?,
        statusFilter: SearchStatusFilter,
    ): Either<UseCaseError, SearchResourcesResult>
}
