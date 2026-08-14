package com.zickat.shopifymcpserver.api.mcp

import com.zickat.shopifymcpserver.api.exposed_interface.RoutedToolPipeline
import com.zickat.shopifymcpserver.products.exposed_interface.ProductsExposedService
import com.zickat.shopifymcpserver.products.exposed_interface.model.ToReviewResourceType
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
class ListToReviewTool(
    private val pipeline: RoutedToolPipeline,
    private val accessExposedService: AccessExposedService,
    private val productsExposedService: ProductsExposedService,
) : HasToolUseCase {

    private object ListToReviewToolUseCase : ToolUseCase {
        override val kind = UseCaseKind.READ
    }

    override val toolUseCase: ToolUseCase = ListToReviewToolUseCase

    @McpTool(
        name = "list_to_review",
        description = "Lists resources (product|collection|article) with content_status: to_review.",
    )
    fun listToReview(
        @McpToolParam(description = "Resource type: \"product\", \"collection\" or \"article\".", required = true)
        resource_type: String,
        exchange: McpSyncServerExchange,
    ): CallToolResult {
        val toolInput = mapOf("resource_type" to resource_type)

        return pipeline.runForActiveStore(
            "list_to_review",
            ListToReviewToolUseCase,
            exchange.sessionId(),
            toolInput,
        ) { tenant, user ->
            val slug = accessExposedService.slugFor(user.identityId, tenant.storeId)
            when (val resourceType = parseResourceType(resource_type)) {
                null -> McpToolResults.invalidToReviewResourceType(slug, resource_type)
                else -> productsExposedService.listToReview(tenant.storeId, resourceType).fold(
                    { error -> McpToolResults.errorResult(slug, error) },
                    { result -> McpToolResults.listToReviewResult(slug, result) },
                )
            }
        }
    }

    private fun parseResourceType(value: String): ToReviewResourceType? = when (value) {
        "product" -> ToReviewResourceType.PRODUCT
        "collection" -> ToReviewResourceType.COLLECTION
        "article" -> ToReviewResourceType.ARTICLE
        else -> null
    }
}
