package com.zickat.shopifymcpserver.api.mcp

import com.zickat.shopifymcpserver.products.exposed_interface.ProductsExposedService
import com.zickat.shopifymcpserver.products.exposed_interface.model.ProductStatusFilter
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
class SearchProductsTool(
    private val pipeline: RoutedToolPipeline,
    private val accessExposedService: AccessExposedService,
    private val productsExposedService: ProductsExposedService,
) : HasToolUseCase {

    private object SearchProductsToolUseCase : ToolUseCase {
        override val kind = UseCaseKind.READ
    }

    override val toolUseCase: ToolUseCase = SearchProductsToolUseCase

    @McpTool(
        name = "search_products",
        description = "Searches products of the catalog by Shopify query (e.g. \"product_type:sacoches\", " +
            "\"created_at:>=2026-06-01\") and/or by pipeline processing status. Entry point to identify a " +
            "batch of products to process — never returns content, only enough to identify the products " +
            "(get_raw_content reads the detail). Pipeline status is always recomputed from a direct read " +
            "of each product's metafield, never trusted from the Shopify search index (which lags after a " +
            "write) — only the free-text query itself goes through the index.",
    )
    fun searchProducts(
        @McpToolParam(
            description = "OPTIONAL Shopify Admin search query on products (e.g. product_type:..., tag:..., " +
                "title:*word*, created_at:>=YYYY-MM-DD). Leave empty to filter by status only.",
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
            query?.let { put("query", it) }
            status_filter?.let { put("status_filter", it) }
        }

        return pipeline.runForActiveStore(
            "search_products",
            SearchProductsToolUseCase,
            exchange.sessionId(),
            toolInput,
        ) { tenant, user ->
            val slug = accessExposedService.slugFor(user.identityId, tenant.storeId)
            productsExposedService.searchProducts(tenant.storeId, query, parseStatusFilter(status_filter)).fold(
                { error -> McpToolResults.errorResult(slug, error) },
                { result -> McpToolResults.searchProductsResult(slug, result) },
            )
        }
    }

    private fun parseStatusFilter(value: String?): ProductStatusFilter = when (value) {
        "to_review" -> ProductStatusFilter.TO_REVIEW
        "blocked" -> ProductStatusFilter.BLOCKED
        "all" -> ProductStatusFilter.ALL
        else -> ProductStatusFilter.UNTREATED
    }
}
