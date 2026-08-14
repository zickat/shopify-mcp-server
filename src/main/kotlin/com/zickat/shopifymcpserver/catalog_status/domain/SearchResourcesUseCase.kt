package com.zickat.shopifymcpserver.catalog_status.domain

import arrow.core.Either
import com.zickat.shopifymcpserver.catalog_status.domain.models.CatalogStatusResourceNode
import com.zickat.shopifymcpserver.catalog_status.domain.models.ResourceSummary
import com.zickat.shopifymcpserver.catalog_status.domain.models.SearchResourceType
import com.zickat.shopifymcpserver.catalog_status.domain.models.SearchResourcesResult
import com.zickat.shopifymcpserver.catalog_status.domain.models.SearchStatusFilter
import com.zickat.shopifymcpserver.catalog_status.domain.repositories.CatalogStatusRepository
import com.zickat.shopifymcpserver.shared_kernel.UseCaseError
import com.zickat.shopifymcpserver.shopify.exposed_interface.RichText

class SearchResourcesUseCase(
    private val catalogStatusRepository: CatalogStatusRepository,
) {

    fun execute(
        storeId: String,
        resourceType: SearchResourceType,
        query: String?,
        statusFilter: SearchStatusFilter,
    ): Either<UseCaseError, SearchResourcesResult> {
        val searchQuery = query?.trim()?.takeIf { it.isNotEmpty() }
        return catalogStatusRepository.search(storeId, resourceType, searchQuery).map { listing ->
            SearchResourcesResult(
                resourceType = resourceType,
                resources = listing.resources.mapNotNull { node -> summaryOrNull(resourceType, node, statusFilter) },
                truncated = listing.truncated,
            )
        }
    }

    private fun summaryOrNull(
        resourceType: SearchResourceType,
        node: CatalogStatusResourceNode,
        statusFilter: SearchStatusFilter,
    ): ResourceSummary? {
        val hasSummary = node.summary.orEmpty().trim().isNotEmpty()
        val hasSecondarySignal = when (resourceType) {
            SearchResourceType.COLLECTION -> RichText.toPlainText(node.secondarySignal).trim().isNotEmpty()
            SearchResourceType.ARTICLE -> RichText.parseStringArray(node.secondarySignal).isNotEmpty()
        }
        val isEnriched = hasSummary || hasSecondarySignal

        val included = when (statusFilter) {
            SearchStatusFilter.UNTREATED -> node.contentStatus == null && !isEnriched
            SearchStatusFilter.TO_REVIEW -> node.contentStatus == "to_review"
            SearchStatusFilter.BLOCKED -> node.contentStatus == "blocked"
            SearchStatusFilter.ALL -> true
        }
        if (!included) return null

        val displayedStatus = node.contentStatus ?: if (isEnriched) "published" else "untreated"
        return ResourceSummary(node.id, node.title, node.handle, displayedStatus)
    }
}
