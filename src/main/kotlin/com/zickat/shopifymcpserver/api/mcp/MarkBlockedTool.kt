package com.zickat.shopifymcpserver.api.mcp

import com.zickat.shopifymcpserver.api.exposed_interface.RoutedToolPipeline
import com.zickat.shopifymcpserver.products.exposed_interface.ProductsExposedService
import com.zickat.shopifymcpserver.shared_kernel.HasToolUseCase
import com.zickat.shopifymcpserver.shared_kernel.ToolUseCase
import com.zickat.shopifymcpserver.shared_kernel.UseCaseKind
import com.zickat.shopifymcpserver.shared_kernel.isGidOfType
import com.zickat.shopifymcpserver.shopify.exposed_interface.ShopifyResourceTypes
import com.zickat.shopifymcpserver.tenancy.exposed_interface.AccessExposedService
import io.modelcontextprotocol.server.McpSyncServerExchange
import io.modelcontextprotocol.spec.McpSchema.CallToolResult
import org.springframework.ai.mcp.annotation.McpTool
import org.springframework.ai.mcp.annotation.McpToolParam
import org.springframework.stereotype.Service

@Service
class MarkBlockedTool(
    private val pipeline: RoutedToolPipeline,
    private val accessExposedService: AccessExposedService,
    private val productsExposedService: ProductsExposedService,
) : HasToolUseCase {

    private object MarkBlockedToolUseCase : ToolUseCase {
        override val kind = UseCaseKind.MUTATION
    }

    override val toolUseCase: ToolUseCase = MarkBlockedToolUseCase

    @McpTool(
        name = "mark_blocked",
        description = "Marks a product, collection or guide (article) as blocked (internal pipeline " +
            "metafield custom.content_status = \"blocked\") — excludes it from the catalog's editorial " +
            "review workflow without touching its Shopify publication status or visibility. " +
            "resource_id must be the gid of the type named by resource_type.",
    )
    fun markBlocked(
        @McpToolParam(description = "Resource type: \"product\", \"collection\" or \"article\" (guide).", required = true)
        resource_type: String,
        @McpToolParam(description = "gid://shopify/{Product|Collection|Article}/... — consistent with resource_type.", required = true)
        resource_id: String,
        @McpToolParam(description = "REQUIRED — reason for blocking, echoed back in the response for the caller to keep.", required = true)
        reason: String,
        exchange: McpSyncServerExchange,
    ): CallToolResult {
        val toolInput = mapOf("resource_type" to resource_type, "resource_id" to resource_id, "reason" to reason)

        return pipeline.runForActiveStore(
            "mark_blocked",
            MarkBlockedToolUseCase,
            exchange.sessionId(),
            toolInput,
        ) { tenant, user ->
            val slug = accessExposedService.slugFor(user.identityId, tenant.storeId)
            val expectedGidType = if (resource_type in ALLOWED_RESOURCE_TYPES) ShopifyResourceTypes.gidTypeFor(resource_type) else null
            when {
                expectedGidType == null -> McpToolResults.invalidMarkBlockedResourceType(slug, resource_type)
                !resource_id.isGidOfType(expectedGidType) ->
                    McpToolResults.invalidGidType(slug, "resource_id", resource_id, expectedGidType)
                else -> productsExposedService.markBlocked(tenant.storeId, resource_id).fold(
                    { error -> McpToolResults.errorResult(slug, error) },
                    { result -> McpToolResults.markBlockedResult(slug, resource_type, resource_id, reason, result) },
                )
            }
        }
    }

    companion object {
        private val ALLOWED_RESOURCE_TYPES = setOf("product", "collection", "article")
    }
}
