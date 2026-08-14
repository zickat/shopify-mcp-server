package com.zickat.shopifymcpserver.api.mcp

import com.zickat.shopifymcpserver.api.exposed_interface.RoutedToolPipeline
import com.zickat.shopifymcpserver.products.exposed_interface.ProductsExposedService
import com.zickat.shopifymcpserver.shared_kernel.HasToolUseCase
import com.zickat.shopifymcpserver.shared_kernel.ToolUseCase
import com.zickat.shopifymcpserver.shared_kernel.UseCaseKind
import com.zickat.shopifymcpserver.shared_kernel.isGidOfType
import com.zickat.shopifymcpserver.tenancy.exposed_interface.AccessExposedService
import com.zickat.shopifymcpserver.tenancy.exposed_interface.slugFor
import io.modelcontextprotocol.server.McpSyncServerExchange
import io.modelcontextprotocol.spec.McpSchema.CallToolResult
import org.springframework.ai.mcp.annotation.McpTool
import org.springframework.ai.mcp.annotation.McpToolParam
import org.springframework.stereotype.Service

@Service
class GetEnrichedContentTool(
    private val pipeline: RoutedToolPipeline,
    private val accessExposedService: AccessExposedService,
    private val productsExposedService: ProductsExposedService,
) : HasToolUseCase {

    private object GetEnrichedContentToolUseCase : ToolUseCase {
        override val kind = UseCaseKind.READ
    }

    override val toolUseCase: ToolUseCase = GetEnrichedContentToolUseCase

    @McpTool(
        name = "get_enriched_content",
        description = "Returns the state currently written by the pipeline for a product (title, native " +
            "descriptionHtml, productType, tags, summary_points, why_recommend, how_to_use, specs, faq, " +
            "complementary products, review status, original supplier title/description snapshot if " +
            "already captured, related guide(s) and their origin, \"ideal for\") — call this before " +
            "retouching a product that is already enriched or already ACTIVE, to judge consistency " +
            "between a new title and the custom.* content already written, and to retrieve the current " +
            "body_html without reconstructing it blind. Read-only, never modifies anything, distinct from " +
            "get_raw_content (raw supplier-sourced sheet).",
    )
    fun getEnrichedContent(
        @McpToolParam(description = "gid://shopify/Product/...", required = true)
        product_id: String,
        exchange: McpSyncServerExchange,
    ): CallToolResult {
        val toolInput = mapOf("product_id" to product_id)

        return pipeline.runForActiveStore(
            "get_enriched_content",
            GetEnrichedContentToolUseCase,
            exchange.sessionId(),
            toolInput,
        ) { tenant, user ->
            val slug = accessExposedService.slugFor(user.identityId, tenant.storeId)
            if (!product_id.isGidOfType("Product")) {
                McpToolResults.invalidGidType(slug, "product_id", product_id, "Product")
            } else {
                productsExposedService.getEnrichedContent(tenant.storeId, product_id).fold(
                    { error -> McpToolResults.errorResult(slug, error) },
                    { result -> McpToolResults.getEnrichedContentResult(slug, result) },
                )
            }
        }
    }
}
