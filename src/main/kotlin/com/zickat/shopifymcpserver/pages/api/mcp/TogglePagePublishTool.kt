package com.zickat.shopifymcpserver.pages.api.mcp

import com.zickat.shopifymcpserver.api.exposed_interface.RoutedToolPipeline
import com.zickat.shopifymcpserver.pages.domain.TogglePagePublishUseCase
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

/**
 * `publish_page`/`unpublish_page` — both registered here, on the same shared
 * [TogglePagePublishUseCase], mirroring the loop `pages.ts` uses to register both tool variants
 * from one implementation rather than two duplicated files (D25/D27 applied identically to both).
 */
@Service
class TogglePagePublishTool(
    private val pipeline: RoutedToolPipeline,
    private val accessExposedService: AccessExposedService,
    private val togglePagePublishUseCase: TogglePagePublishUseCase,
) : HasToolUseCase {

    private object TogglePagePublishToolKind : ToolUseCase {
        override val kind = UseCaseKind.MUTATION
    }

    override val toolUseCase: ToolUseCase = TogglePagePublishToolKind

    @McpTool(
        name = "publish_page",
        description = "Publishes a draft Page (flips isPublished: true via pageUpdate). No-op " +
            "documented if the Page is already published (no mutation emitted, explicit message). " +
            "Never touches title/body/handle nor a custom.* metafield — only the visibility status " +
            "changes.",
    )
    fun publishPage(
        @McpToolParam(description = "gid://shopify/Page/...", required = true)
        page_id: String,
        exchange: McpSyncServerExchange,
    ): CallToolResult = toggle("publish_page", page_id, target = true, exchange)

    @McpTool(
        name = "unpublish_page",
        description = "Removes a Page from the storefront without deleting it (flips isPublished: " +
            "false via pageUpdate). No-op documented if the Page is already unpublished. Never " +
            "touches title/body/handle nor a custom.* metafield — only the visibility status changes. " +
            "To permanently delete a Page, use delete_page.",
    )
    fun unpublishPage(
        @McpToolParam(description = "gid://shopify/Page/...", required = true)
        page_id: String,
        exchange: McpSyncServerExchange,
    ): CallToolResult = toggle("unpublish_page", page_id, target = false, exchange)

    private fun toggle(toolName: String, pageId: String, target: Boolean, exchange: McpSyncServerExchange): CallToolResult {
        val toolInput = mapOf("page_id" to pageId)

        return pipeline.runForActiveStore(
            toolName,
            TogglePagePublishToolKind,
            exchange.sessionId(),
            toolInput,
        ) { tenant, user ->
            val slug = accessExposedService.slugFor(user.identityId, tenant.storeId)
            if (!pageId.isGidOfType(PAGE_GID_TYPE)) {
                PageToolResults.invalidGidType(slug, "page_id", pageId, PAGE_GID_TYPE)
            } else {
                togglePagePublishUseCase.execute(tenant.storeId, pageId, target).fold(
                    { error -> PageToolResults.errorResult(slug, error) },
                    { result -> PageToolResults.togglePagePublishResult(slug, result) },
                )
            }
        }
    }

    companion object {
        private const val PAGE_GID_TYPE = "Page"
    }
}
