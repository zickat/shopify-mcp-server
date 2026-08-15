package com.zickat.shopifymcpserver.menus.api.mcp

import com.zickat.shopifymcpserver.tool_dispatch.exposed_interface.RoutedToolPipeline
import com.zickat.shopifymcpserver.menus.domain.UpdateMenuItemUseCase
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
class UpdateMenuItemTool(
    private val pipeline: RoutedToolPipeline,
    private val accessExposedService: AccessExposedService,
    private val updateMenuItemUseCase: UpdateMenuItemUseCase,
) : HasToolUseCase {

    private object UpdateMenuItemToolKind : ToolUseCase {
        override val kind = UseCaseKind.MUTATION
    }

    override val toolUseCase: ToolUseCase = UpdateMenuItemToolKind

    @McpTool(
        name = "update_menu_item",
        description = "Updates a menu item IN PLACE, at any depth: its item_id and its sub-menu are " +
            "PRESERVED. That is its whole point — renaming an item via remove + add would destroy its " +
            "children, exactly what remove_menu_item refuses to do. ONLY THE SUPPLIED FIELDS CHANGE. " +
            "title alone: the title changes, the target AND type stay untouched, not re-derived (7 of " +
            "the 13 link types are not derivable from a gid — FRONTPAGE, COLLECTIONS, CATALOG, " +
            "SEARCH, SHOP_POLICY, HTTP without a url, CUSTOMER_ACCOUNT_PAGE ; re-deriving the type " +
            "while renaming \"Home\" would break it). resource_id or url: the target is replaced and " +
            "the type re-derived from the gid. The two target parameters are mutually exclusive ; url " +
            "is XSS-validated. Does NOT move anything — neither position (see reorder_menu_items) nor " +
            "parent (no tool does). isError with no write at all if: menu or item not found, none of " +
            "the three fields supplied (a no-op call), resource_id and url supplied together, invalid " +
            "url, unrecognized gid, or Shopify userErrors.",
    )
    fun updateMenuItem(
        @McpToolParam(description = "gid://shopify/Menu/...", required = true)
        menu_id: String,
        @McpToolParam(
            description = "gid://shopify/MenuItem/... to update, AT ANY DEPTH in the menu (anchor " +
                "supplied by list_menus).",
            required = true,
        )
        item_id: String,
        @McpToolParam(
            description = "OPTIONAL — new displayed label. Omitted: the current title is kept. Supplied " +
                "ALONE, the item's target and type are untouched.",
            required = false,
        )
        title: String?,
        @McpToolParam(
            description = "OPTIONAL — new target: a Collection/Product/Page/Article/Blog/Metaobject " +
                "gid. The link type is re-derived from it. Exclusive with url. Omitted (like url): " +
                "target and type unchanged.",
            required = false,
        )
        resource_id: String?,
        @McpToolParam(
            description = "OPTIONAL — new absolute external target (http(s)), validated. The type " +
                "becomes HTTP. Exclusive with resource_id.",
            required = false,
        )
        url: String?,
        exchange: McpSyncServerExchange,
    ): CallToolResult {
        val toolInput = buildMap {
            put("menu_id", menu_id)
            put("item_id", item_id)
            title?.let { put("title", it) }
            resource_id?.let { put("resource_id", it) }
            url?.let { put("url", it) }
        }

        return pipeline.runForActiveStore(
            "update_menu_item",
            UpdateMenuItemToolKind,
            exchange.sessionId(),
            toolInput,
        ) { tenant, user ->
            val slug = accessExposedService.slugFor(user.identityId, tenant.storeId)
            when {
                !menu_id.isGidOfType(MENU_GID_TYPE) -> MenuToolResults.invalidGidType(slug, "menu_id", menu_id, MENU_GID_TYPE)
                !item_id.isGidOfType(MENU_ITEM_GID_TYPE) -> MenuToolResults.invalidGidType(slug, "item_id", item_id, MENU_ITEM_GID_TYPE)
                else -> updateMenuItemUseCase.execute(tenant.storeId, menu_id, item_id, title, resource_id, url).fold(
                    { error -> MenuToolResults.errorResult(slug, error) },
                    { result -> MenuToolResults.updateMenuItemResult(slug, result) },
                )
            }
        }
    }

    companion object {
        private const val MENU_GID_TYPE = "Menu"
        private const val MENU_ITEM_GID_TYPE = "MenuItem"
    }
}
