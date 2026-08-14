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
class PublishResourceTool(
    private val pipeline: RoutedToolPipeline,
    private val accessExposedService: AccessExposedService,
    private val productsExposedService: ProductsExposedService,
) : HasToolUseCase {

    private object PublishResourceToolUseCase : ToolUseCase {
        override val kind = UseCaseKind.MUTATION
    }

    override val toolUseCase: ToolUseCase = PublishResourceToolUseCase

    @McpTool(
        name = "publish_resource",
        description = "Publishes a product: sets its status to ACTIVE, removes the internal pipeline " +
            "metafield custom.content_status, and — if it was on no sales channel yet — publishes it " +
            "to the Online Store channel. If already published on a channel, that step is skipped. " +
            "resource_id must be a Product gid.",
    )
    fun publishResource(
        @McpToolParam(description = "Resource type — only \"product\" is supported.", required = true)
        resource_type: String,
        @McpToolParam(description = "gid://shopify/Product/...", required = true)
        resource_id: String,
        exchange: McpSyncServerExchange,
    ): CallToolResult {
        val toolInput = mapOf("resource_type" to resource_type, "resource_id" to resource_id)

        return pipeline.runForActiveStore(
            "publish_resource",
            PublishResourceToolUseCase,
            exchange.sessionId(),
            toolInput,
        ) { tenant, user ->
            val slug = accessExposedService.slugFor(user.identityId, tenant.storeId)
            when {
                resource_type != "product" -> McpToolResults.invalidPublishResourceType(slug, resource_type)
                !resource_id.isGidOfType("Product") -> McpToolResults.invalidGidType(slug, "resource_id", resource_id, "Product")
                else -> productsExposedService.publishResource(tenant.storeId, resource_id).fold(
                    { error -> McpToolResults.errorResult(slug, error) },
                    { result -> McpToolResults.publishResourceResult(slug, result) },
                )
            }
        }
    }
}
