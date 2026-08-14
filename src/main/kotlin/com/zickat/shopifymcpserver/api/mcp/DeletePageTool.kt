package com.zickat.shopifymcpserver.api.mcp

import com.zickat.shopifymcpserver.pages.exposed_interface.PagesExposedService
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
class DeletePageTool(
    private val pipeline: RoutedToolPipeline,
    private val accessExposedService: AccessExposedService,
    private val pagesExposedService: PagesExposedService,
) : HasToolUseCase {

    private object DeletePageToolUseCase : ToolUseCase {
        override val kind = UseCaseKind.MUTATION
    }

    override val toolUseCase: ToolUseCase = DeletePageToolUseCase

    @McpTool(
        name = "delete_page",
        description = "PERMANENTLY deletes a Page (irreversible action — no trash, no restoration " +
            "client-side). Rereads the Page's existence BEFORE deletion (isError: true if not found, " +
            "never a silent deletion) and returns its title/handle for traceability. Reserve for Pages " +
            "that are genuinely obsolete; for a simple temporary storefront removal, use " +
            "unpublish_page (reversible).",
    )
    fun deletePage(
        @McpToolParam(description = "gid://shopify/Page/...", required = true)
        page_id: String,
        exchange: McpSyncServerExchange,
    ): CallToolResult {
        val toolInput = mapOf("page_id" to page_id)

        return pipeline.runForActiveStore(
            "delete_page",
            DeletePageToolUseCase,
            exchange.sessionId(),
            toolInput,
        ) { tenant, user ->
            val slug = accessExposedService.slugFor(user.identityId, tenant.storeId)
            if (!page_id.isGidOfType(PAGE_GID_TYPE)) {
                McpToolResults.invalidGidType(slug, "page_id", page_id, PAGE_GID_TYPE)
            } else {
                pagesExposedService.deletePage(tenant.storeId, page_id).fold(
                    { error -> McpToolResults.errorResult(slug, error) },
                    { result -> McpToolResults.deletePageResult(slug, result) },
                )
            }
        }
    }

    companion object {
        private const val PAGE_GID_TYPE = "Page"
    }
}
