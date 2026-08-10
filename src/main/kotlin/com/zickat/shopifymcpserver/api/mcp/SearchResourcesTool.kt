package com.zickat.shopifymcpserver.api.mcp

import com.zickat.shopifymcpserver.catalog_status.exposed_interface.CatalogStatusExposedService
import com.zickat.shopifymcpserver.catalog_status.exposed_interface.model.SearchResourceType
import com.zickat.shopifymcpserver.catalog_status.exposed_interface.model.SearchStatusFilter
import com.zickat.shopifymcpserver.shared_kernel.HasToolUseCase
import com.zickat.shopifymcpserver.shared_kernel.ToolUseCase
import com.zickat.shopifymcpserver.shared_kernel.UseCaseKind
import com.zickat.shopifymcpserver.tenancy.exposed_interface.AccessExposedService
import io.modelcontextprotocol.server.McpSyncServerExchange
import io.modelcontextprotocol.spec.McpSchema.CallToolResult
import org.springframework.ai.mcp.annotation.McpTool
import org.springframework.ai.mcp.annotation.McpToolParam
import org.springframework.stereotype.Service

@Service
class SearchResourcesTool(
    private val pipeline: RoutedToolPipeline,
    private val accessExposedService: AccessExposedService,
    private val catalogStatusExposedService: CatalogStatusExposedService,
) : HasToolUseCase {

    private object SearchResourcesToolUseCase : ToolUseCase {
        override val kind = UseCaseKind.READ
    }

    override val toolUseCase: ToolUseCase = SearchResourcesToolUseCase

    @McpTool(
        name = "search_resources",
        description = "Searches collections or articles (guides) of the catalog by Shopify query " +
            "and/or by pipeline processing status. Collection/article equivalent of search_products " +
            "(products stay covered by search_products, not by this tool). Entry point to identify a " +
            "batch of collections/guides to process — never returns content, only enough to identify " +
            "the resources.",
    )
    fun searchResources(
        @McpToolParam(description = "Resource type to list: \"collection\" or \"article\".", required = true)
        resource_type: String,
        @McpToolParam(
            description = "Optional Shopify search query (Admin syntax, e.g. title:*word*, handle:..., " +
                "created_at:>=YYYY-MM-DD). Leave empty to filter by status only.",
            required = false,
        )
        query: String?,
        @McpToolParam(
            description = "untreated (default) = never processed by the pipeline ; to_review ; blocked ; " +
                "all = every status.",
            required = false,
        )
        status_filter: String?,
        exchange: McpSyncServerExchange,
    ): CallToolResult {
        val toolInput = buildMap {
            put("resource_type", resource_type)
            query?.let { put("query", it) }
            status_filter?.let { put("status_filter", it) }
        }

        return pipeline.runForActiveStore(
            "search_resources",
            SearchResourcesToolUseCase,
            exchange.sessionId(),
            toolInput,
        ) { tenant, user ->
            val slug = accessExposedService.slugFor(user.identityId, tenant.storeId)
            when (val resourceType = parseResourceType(resource_type)) {
                null -> McpToolResults.invalidResourceType(slug, resource_type)
                else -> catalogStatusExposedService.searchResources(
                    tenant.storeId,
                    resourceType,
                    query,
                    parseStatusFilter(status_filter),
                ).fold(
                    { error -> McpToolResults.errorResult(slug, error) },
                    { result -> McpToolResults.searchResourcesResult(slug, result) },
                )
            }
        }
    }

    private fun parseResourceType(value: String): SearchResourceType? = when (value) {
        "collection" -> SearchResourceType.COLLECTION
        "article" -> SearchResourceType.ARTICLE
        else -> null
    }

    private fun parseStatusFilter(value: String?): SearchStatusFilter = when (value) {
        "to_review" -> SearchStatusFilter.TO_REVIEW
        "blocked" -> SearchStatusFilter.BLOCKED
        "all" -> SearchStatusFilter.ALL
        else -> SearchStatusFilter.UNTREATED
    }
}
