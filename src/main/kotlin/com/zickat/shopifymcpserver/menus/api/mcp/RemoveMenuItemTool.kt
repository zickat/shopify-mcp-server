package com.zickat.shopifymcpserver.menus.api.mcp

import com.zickat.shopifymcpserver.tool_dispatch.exposed_interface.RoutedToolPipeline
import com.zickat.shopifymcpserver.menus.domain.RemoveMenuItemUseCase
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
class RemoveMenuItemTool(
    private val pipeline: RoutedToolPipeline,
    private val accessExposedService: AccessExposedService,
    private val removeMenuItemUseCase: RemoveMenuItemUseCase,
) : HasToolUseCase {

    private object RemoveMenuItemToolKind : ToolUseCase {
        override val kind = UseCaseKind.MUTATION
    }

    override val toolUseCase: ToolUseCase = RemoveMenuItemToolKind

    @McpTool(
        name = "remove_menu_item",
        description = "Removes the item designated by its item_id, searched AT ANY DEPTH in the menu " +
            "(anchor supplied by list_menus). DEFAULT REFUSAL, to know before calling: if the item " +
            "carries sub-items, the removal is REFUSED with no write at all and the message states " +
            "the count — removing a silo head would take all its children with it, and \"Link " +
            "removed\" would be the exact wording of a silent destruction. Retry with " +
            "with_children: true to remove the whole sub-tree: the report then ENUMERATES every " +
            "removed item_id, the only way to rebuild in case of mistake. The menu's other items — " +
            "and their sub-menus — are kept as-is and in the same order. isError with no write at all " +
            "if the menu is not found or item_id does not exist at any depth of this menu (\"item not " +
            "found\" — never a silent rewrite).",
    )
    fun removeMenuItem(
        @McpToolParam(description = "gid://shopify/Menu/...", required = true)
        menu_id: String,
        @McpToolParam(
            description = "gid://shopify/MenuItem/... to remove, AT ANY DEPTH in the menu (anchor " +
                "supplied by list_menus).",
            required = true,
        )
        item_id: String,
        @McpToolParam(
            description = "OPTIONAL, default false. false: the removal is REFUSED with no write at all " +
                "if the item carries sub-items (the message states the count). true: removes the whole " +
                "sub-tree, the report enumerates every removed item_id.",
            required = false,
        )
        with_children: Boolean?,
        exchange: McpSyncServerExchange,
    ): CallToolResult {
        val withChildren = with_children ?: false
        val toolInput = mapOf("menu_id" to menu_id, "item_id" to item_id, "with_children" to withChildren.toString())

        return pipeline.runForActiveStore(
            "remove_menu_item",
            RemoveMenuItemToolKind,
            exchange.sessionId(),
            toolInput,
        ) { tenant, user ->
            val slug = accessExposedService.slugFor(user.identityId, tenant.storeId)
            when {
                !menu_id.isGidOfType(MENU_GID_TYPE) -> MenuToolResults.invalidGidType(slug, "menu_id", menu_id, MENU_GID_TYPE)
                !item_id.isGidOfType(MENU_ITEM_GID_TYPE) -> MenuToolResults.invalidGidType(slug, "item_id", item_id, MENU_ITEM_GID_TYPE)
                else -> removeMenuItemUseCase.execute(tenant.storeId, menu_id, item_id, withChildren).fold(
                    { error -> MenuToolResults.errorResult(slug, error) },
                    { result -> MenuToolResults.removeMenuItemResult(slug, result) },
                )
            }
        }
    }

    companion object {
        private const val MENU_GID_TYPE = "Menu"
        private const val MENU_ITEM_GID_TYPE = "MenuItem"
    }
}
