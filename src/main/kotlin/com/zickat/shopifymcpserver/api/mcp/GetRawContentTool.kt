package com.zickat.shopifymcpserver.api.mcp

import com.zickat.shopifymcpserver.products.exposed_interface.ProductsExposedService
import com.zickat.shopifymcpserver.shared_kernel.HasToolUseCase
import com.zickat.shopifymcpserver.shared_kernel.ToolUseCase
import com.zickat.shopifymcpserver.shared_kernel.UseCaseKind
import com.zickat.shopifymcpserver.shared_kernel.isGidOfType
import com.zickat.shopifymcpserver.tenancy.exposed_interface.AccessExposedService
import io.modelcontextprotocol.server.McpSyncServerExchange
import io.modelcontextprotocol.spec.McpSchema.CallToolResult
import org.springframework.ai.mcp.annotation.McpTool
import org.springframework.ai.mcp.annotation.McpToolParam
import org.springframework.stereotype.Service

@Service
class GetRawContentTool(
    private val pipeline: RoutedToolPipeline,
    private val accessExposedService: AccessExposedService,
    private val productsExposedService: ProductsExposedService,
) : HasToolUseCase {

    private object GetRawContentToolUseCase : ToolUseCase {
        override val kind = UseCaseKind.READ
    }

    override val toolUseCase: ToolUseCase = GetRawContentToolUseCase

    @McpTool(
        name = "get_raw_content",
        description = "Returns the existing raw material of a product (title, source description, price, " +
            "images, variant options, variants) — read this before drafting enriched content via " +
            "enrich_product. Read-only, never modifies anything.",
    )
    fun getRawContent(
        @McpToolParam(description = "gid://shopify/Product/...", required = true)
        product_id: String,
        exchange: McpSyncServerExchange,
    ): CallToolResult {
        val toolInput = mapOf("product_id" to product_id)

        return pipeline.runForActiveStore(
            "get_raw_content",
            GetRawContentToolUseCase,
            exchange.sessionId(),
            toolInput,
        ) { tenant, user ->
            val slug = accessExposedService.slugFor(user.identityId, tenant.storeId)
            if (!product_id.isGidOfType("Product")) {
                McpToolResults.invalidGidType(slug, "product_id", product_id, "Product")
            } else {
                productsExposedService.getRawContent(tenant.storeId, product_id).fold(
                    { error -> McpToolResults.errorResult(slug, error) },
                    { result -> McpToolResults.getRawContentResult(slug, result) },
                )
            }
        }
    }
}
