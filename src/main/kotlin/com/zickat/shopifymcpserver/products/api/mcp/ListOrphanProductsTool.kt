package com.zickat.shopifymcpserver.products.api.mcp

import com.zickat.shopifymcpserver.tool_dispatch.exposed_interface.RoutedToolPipeline
import com.zickat.shopifymcpserver.products.domain.ListOrphanProductsUseCase
import com.zickat.shopifymcpserver.shared_kernel.HasToolUseCase
import com.zickat.shopifymcpserver.shared_kernel.ToolUseCase
import com.zickat.shopifymcpserver.shared_kernel.UseCaseKind
import com.zickat.shopifymcpserver.tenancy.exposed_interface.AccessExposedService
import com.zickat.shopifymcpserver.tenancy.exposed_interface.slugFor
import io.modelcontextprotocol.server.McpSyncServerExchange
import io.modelcontextprotocol.spec.McpSchema.CallToolResult
import org.springframework.ai.mcp.annotation.McpTool
import org.springframework.stereotype.Service

@Service
class ListOrphanProductsTool(
    private val pipeline: RoutedToolPipeline,
    private val accessExposedService: AccessExposedService,
    private val listOrphanProductsUseCase: ListOrphanProductsUseCase,
) : HasToolUseCase {

    private object ListOrphanProductsToolUseCase : ToolUseCase {
        override val kind = UseCaseKind.READ
    }

    override val toolUseCase: ToolUseCase = ListOrphanProductsToolUseCase

    @McpTool(
        name = "list_orphan_products",
        description = "Returns products (native status ACTIVE or DRAFT, ARCHIVED excluded by default) " +
            "that belong to NO collection — manual or legacy, no distinction. Useful to spot " +
            "categorization blind spots in the catalog. Explicitly signals any truncation of the scan " +
            "(collection side or product side) rather than implying an exhaustive list.",
    )
    fun listOrphanProducts(exchange: McpSyncServerExchange): CallToolResult {
        val toolInput = emptyMap<String, String>()

        return pipeline.runForActiveStore(
            "list_orphan_products",
            ListOrphanProductsToolUseCase,
            exchange.sessionId(),
            toolInput,
        ) { tenant, user ->
            val slug = accessExposedService.slugFor(user.identityId, tenant.storeId)
            listOrphanProductsUseCase.execute(tenant.storeId).fold(
                { error -> ProductsToolResults.errorResult(slug, error) },
                { result -> ProductsToolResults.listOrphanProductsResult(slug, result) },
            )
        }
    }
}
