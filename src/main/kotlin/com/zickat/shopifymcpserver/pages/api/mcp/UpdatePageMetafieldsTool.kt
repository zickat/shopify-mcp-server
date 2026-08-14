package com.zickat.shopifymcpserver.pages.api.mcp

import com.zickat.shopifymcpserver.tool_dispatch.exposed_interface.RoutedToolPipeline
import com.zickat.shopifymcpserver.pages.domain.PageMetafieldInput
import com.zickat.shopifymcpserver.pages.domain.UpdatePageMetafieldsUseCase
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
class UpdatePageMetafieldsTool(
    private val pipeline: RoutedToolPipeline,
    private val accessExposedService: AccessExposedService,
    private val updatePageMetafieldsUseCase: UpdatePageMetafieldsUseCase,
) : HasToolUseCase {

    private object UpdatePageMetafieldsToolKind : ToolUseCase {
        override val kind = UseCaseKind.MUTATION
    }

    override val toolUseCase: ToolUseCase = UpdatePageMetafieldsToolKind

    @McpTool(
        name = "update_page_metafields",
        description = "Writes one or more custom.* metafields on a Page. Rereads the Page's existence " +
            "before any write (isError: true if not found). No redundant type/value validation client-" +
            "side: Shopify is the sole source of truth, its userErrors are surfaced as-is. A " +
            "list.*_reference field (e.g. themes) is replaced WHOLESALE by the given value — native " +
            "Shopify API property, not a merge: read via get_page_metafields first to compose the full " +
            "list to write. Never touches a native Page field (title/body — out of scope).",
    )
    fun updatePageMetafields(
        @McpToolParam(description = "gid://shopify/Page/...", required = true)
        page_id: String,
        @McpToolParam(
            description = "custom.* metafields to write ({key, type, value}) — implicit custom " +
                "namespace. For a list.*_reference, value is the full replacement JSON array of gids.",
            required = true,
        )
        metafields: List<PageMetafieldInput>,
        exchange: McpSyncServerExchange,
    ): CallToolResult {
        val toolInput = mapOf(
            "page_id" to page_id,
            "metafields" to metafields.joinToString("|") { "${it.key}::${it.type}::${it.value}" },
        )

        return pipeline.runForActiveStore(
            "update_page_metafields",
            UpdatePageMetafieldsToolKind,
            exchange.sessionId(),
            toolInput,
        ) { tenant, user ->
            val slug = accessExposedService.slugFor(user.identityId, tenant.storeId)
            if (!page_id.isGidOfType(PAGE_GID_TYPE)) {
                PageToolResults.invalidGidType(slug, "page_id", page_id, PAGE_GID_TYPE)
            } else {
                updatePageMetafieldsUseCase.execute(tenant.storeId, page_id, metafields).fold(
                    { error -> PageToolResults.errorResult(slug, error) },
                    { result -> PageToolResults.updatePageMetafieldsResult(slug, result) },
                )
            }
        }
    }

    companion object {
        private const val PAGE_GID_TYPE = "Page"
    }
}
