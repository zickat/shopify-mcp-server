package com.zickat.shopifymcpserver.menus.api.mcp

import com.zickat.shopifymcpserver.tool_dispatch.exposed_interface.RoutedToolPipeline
import com.zickat.shopifymcpserver.menus.domain.ListMenusUseCase
import com.zickat.shopifymcpserver.shared_kernel.HasToolUseCase
import com.zickat.shopifymcpserver.shared_kernel.ToolUseCase
import com.zickat.shopifymcpserver.shared_kernel.UseCaseKind
import com.zickat.shopifymcpserver.tenancy.exposed_interface.AccessExposedService
import com.zickat.shopifymcpserver.tenancy.exposed_interface.slugFor
import io.modelcontextprotocol.server.McpSyncServerExchange
import io.modelcontextprotocol.spec.McpSchema.CallToolResult
import org.springframework.ai.mcp.annotation.McpTool
import org.springframework.ai.mcp.annotation.McpToolParam
import org.springframework.stereotype.Service

@Service
class ListMenusTool(
    private val pipeline: RoutedToolPipeline,
    private val accessExposedService: AccessExposedService,
    private val listMenusUseCase: ListMenusUseCase,
) : HasToolUseCase {

    private object ListMenusToolUseCase : ToolUseCase {
        override val kind = UseCaseKind.READ
    }

    override val toolUseCase: ToolUseCase = ListMenusToolUseCase

    @McpTool(
        name = "list_menus",
        description = "Lists Shopify navigation menus (header main-menu, footer, others) with, for " +
            "each one, its FULL ITEM TREE — sub-menus included, up to 4 levels. Each line carries a " +
            "hierarchical numbering (1., 1.1., 1.1.1. — the LAST segment is the value to pass as " +
            "position to add_menu_item, the prefix names the parent), the title, the target (type + " +
            "resourceId, or URL), and the suffix \"— item_id: <gid>\", stable at any depth: the anchor " +
            "to reuse in add/remove/reorder/update_menu_item. A menu's header carries \", par défaut\" " +
            "when Shopify protects it (main-menu and footer): it is neither deletable nor renamable by " +
            "handle. Three caps, all SIGNALLED and never silent: depth (display, default 3) emits " +
            "\"+N sous-item(s) masqué(s)\" ; 200 lines per menu emits \"N item(s) non affiché(s)\" ; " +
            "menu pagination is signalled when truncated. A menu with items beyond 4 levels (N5+) " +
            "carries a warning: it is not editable by any of these tools (rewriting it would drop " +
            "those items) — this is the only place that cause is visible. Read-only, never isError. " +
            "No query: every menu. With query: Shopify Admin search syntax — title:*word* and " +
            "full-text search work ; the handle: filter is NOT supported (returns every menu). Each " +
            "menu's handle is in the result regardless.",
    )
    fun listMenus(
        @McpToolParam(
            description = "OPTIONAL — Shopify Admin search on menus (e.g. \"title:*footer*\"). " +
                "Omitted/blank = every menu. Note: \"handle:...\" is NOT a supported filter (returns everything).",
            required = false,
        )
        query: String?,
        @McpToolParam(
            description = "OPTIONAL — display depth of the tree, 1 to 4 (default 3). Purely cosmetic: " +
                "the read always goes 4 levels deep, this parameter only saves displayed context. What " +
                "is hidden is ALWAYS counted and announced, never silently truncated.",
            required = false,
        )
        depth: Int?,
        exchange: McpSyncServerExchange,
    ): CallToolResult {
        val toolInput = buildMap {
            query?.let { put("query", it) }
            depth?.let { put("depth", it.toString()) }
        }

        return pipeline.runForActiveStore(
            "list_menus",
            ListMenusToolUseCase,
            exchange.sessionId(),
            toolInput,
        ) { tenant, user ->
            val slug = accessExposedService.slugFor(user.identityId, tenant.storeId)
            listMenusUseCase.execute(tenant.storeId, query, depth).fold(
                { error -> MenuToolResults.errorResult(slug, error) },
                { result -> MenuToolResults.listMenusResult(slug, result) },
            )
        }
    }
}
