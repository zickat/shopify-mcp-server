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
class UpdatePageTool(
    private val pipeline: RoutedToolPipeline,
    private val accessExposedService: AccessExposedService,
    private val pagesExposedService: PagesExposedService,
) : HasToolUseCase {

    private object UpdatePageToolUseCase : ToolUseCase {
        override val kind = UseCaseKind.MUTATION
    }

    override val toolUseCase: ToolUseCase = UpdatePageToolUseCase

    @McpTool(
        name = "update_page",
        description = "Partially updates a Page's native content (title, HTML body, handle) — only " +
            "the supplied fields are changed, omitted fields stay unchanged. Rereads the Page's " +
            "existence BEFORE any write (isError: true if not found). The Page's gid stays STABLE " +
            "across the update (never delete+recreate). Never writes a custom.* metafield — strictly " +
            "disjoint from update_page_metafields (native content vs custom.*). No field supplied -> " +
            "no-op (reported). body is HTML stored as-is and rendered on the storefront (no client-" +
            "side sanitizer).",
    )
    fun updatePage(
        @McpToolParam(description = "gid://shopify/Page/...", required = true)
        page_id: String,
        @McpToolParam(description = "New title — omitted = unchanged", required = false)
        title: String?,
        @McpToolParam(description = "New HTML body (stored as-is, rendered on the storefront) — omitted = unchanged", required = false)
        body: String?,
        @McpToolParam(description = "New URL handle — omitted = unchanged", required = false)
        handle: String?,
        exchange: McpSyncServerExchange,
    ): CallToolResult {
        val toolInput = buildMap {
            put("page_id", page_id)
            title?.let { put("title", it) }
            body?.let { put("body", it) }
            handle?.let { put("handle", it) }
        }

        return pipeline.runForActiveStore(
            "update_page",
            UpdatePageToolUseCase,
            exchange.sessionId(),
            toolInput,
        ) { tenant, user ->
            val slug = accessExposedService.slugFor(user.identityId, tenant.storeId)
            if (!page_id.isGidOfType(PAGE_GID_TYPE)) {
                McpToolResults.invalidGidType(slug, "page_id", page_id, PAGE_GID_TYPE)
            } else {
                pagesExposedService.updatePage(tenant.storeId, page_id, title, body, handle).fold(
                    { error -> McpToolResults.errorResult(slug, error) },
                    { result -> McpToolResults.updatePageResult(slug, result) },
                )
            }
        }
    }

    companion object {
        private const val PAGE_GID_TYPE = "Page"
    }
}
