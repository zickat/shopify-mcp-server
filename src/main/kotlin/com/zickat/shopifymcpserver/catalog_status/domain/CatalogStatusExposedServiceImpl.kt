package com.zickat.shopifymcpserver.catalog_status.domain

import arrow.core.Either
import com.zickat.shopifymcpserver.catalog_status.exposed_interface.CatalogStatusExposedService
import com.zickat.shopifymcpserver.catalog_status.exposed_interface.model.SearchResourceType
import com.zickat.shopifymcpserver.catalog_status.exposed_interface.model.SearchResourcesResult
import com.zickat.shopifymcpserver.catalog_status.exposed_interface.model.SearchStatusFilter
import com.zickat.shopifymcpserver.shared_kernel.UseCaseError
import org.springframework.stereotype.Service

@Service
class CatalogStatusExposedServiceImpl(
    private val searchResourcesUseCase: SearchResourcesUseCase,
) : CatalogStatusExposedService {

    override fun searchResources(
        storeId: String,
        resourceType: SearchResourceType,
        query: String?,
        statusFilter: SearchStatusFilter,
    ): Either<UseCaseError, SearchResourcesResult> =
        searchResourcesUseCase.execute(storeId, resourceType, query, statusFilter)
}
