package com.zickat.shopifymcpserver.products.api.mcp

import com.zickat.shopifymcpserver.tool_dispatch.exposed_interface.RoutedToolPipeline
import com.zickat.shopifymcpserver.products.domain.UnpublishResourceUseCase
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
class UnpublishResourceTool(
    private val pipeline: RoutedToolPipeline,
    private val accessExposedService: AccessExposedService,
    private val unpublishResourceUseCase: UnpublishResourceUseCase,
) : HasToolUseCase {

    private object UnpublishResourceToolUseCase : ToolUseCase {
        override val kind = UseCaseKind.MUTATION
    }

    override val toolUseCase: ToolUseCase = UnpublishResourceToolUseCase

    @McpTool(
        name = "unpublish_resource",
        description = "Removes a product from the Online Store sales channel. Does not change the " +
            "product's native status (stays ACTIVE) — only its storefront visibility. No-op if the " +
            "product is already on no channel. resource_id must be a Product gid.",
    )
    fun unpublishResource(
        @McpToolParam(description = "Resource type — only \"product\" is supported.", required = true)
        resource_type: String,
        @McpToolParam(description = "gid://shopify/Product/...", required = true)
        resource_id: String,
        exchange: McpSyncServerExchange,
    ): CallToolResult {
        val toolInput = mapOf("resource_type" to resource_type, "resource_id" to resource_id)

        return pipeline.runForActiveStore(
            "unpublish_resource",
            UnpublishResourceToolUseCase,
            exchange.sessionId(),
            toolInput,
        ) { tenant, user ->
            val slug = accessExposedService.slugFor(user.identityId, tenant.storeId)
            when {
                resource_type != "product" -> ProductsToolResults.invalidPublishResourceType(slug, resource_type)
                !resource_id.isGidOfType("Product") -> ProductsToolResults.invalidGidType(slug, "resource_id", resource_id, "Product")
                else -> unpublishResourceUseCase.execute(tenant.storeId, resource_id).fold(
                    { error -> ProductsToolResults.errorResult(slug, error) },
                    { result -> ProductsToolResults.unpublishResourceResult(slug, result) },
                )
            }
        }
    }
}
