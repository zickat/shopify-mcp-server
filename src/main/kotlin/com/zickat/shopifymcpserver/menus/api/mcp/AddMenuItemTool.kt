package com.zickat.shopifymcpserver.menus.api.mcp

import com.zickat.shopifymcpserver.tool_dispatch.exposed_interface.RoutedToolPipeline
import com.zickat.shopifymcpserver.menus.domain.AddMenuItemUseCase
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
class AddMenuItemTool(
    private val pipeline: RoutedToolPipeline,
    private val accessExposedService: AccessExposedService,
    private val addMenuItemUseCase: AddMenuItemUseCase,
) : HasToolUseCase {

    private object AddMenuItemToolKind : ToolUseCase {
        override val kind = UseCaseKind.MUTATION
    }

    override val toolUseCase: ToolUseCase = AddMenuItemToolKind

    @McpTool(
        name = "add_menu_item",
        description = "Adds an item to an existing menu, AT ANY LEVEL: parent_item_id designates the " +
            "parent under which to insert (anchor supplied by list_menus) ; OMITTED, the item is added " +
            "at the menu's top level. position is 1-based WITHIN THE DESIGNATED SIBLING GROUP (the " +
            "children of parent_item_id, or the top level if omitted) ; omitted or beyond = appended at " +
            "the end. Supply EXACTLY one of resource_id (a Collection/Product/Page/Article/Blog/" +
            "Metaobject gid — the link type is derived from the gid) or url (an absolute http(s) " +
            "external link, XSS-validated). DEPTH CAP: the insertion is REFUSED if the designated " +
            "parent is already at level 3 — Shopify navigation only renders 3, and creating a 4th " +
            "level would freeze the menu (none of these mutations could reach it anymore). The menu's " +
            "other items — and their sub-menus — are preserved as-is (read-modify-write: menuUpdate " +
            "rewrites the whole tree, this tool faithfully re-emits the existing one). isError with no " +
            "write at all if: menu not found, parent_item_id not found, parent already at level 3, " +
            "neither/both of resource_id/url, invalid url, unrecognized resource gid, or Shopify " +
            "userErrors.",
    )
    fun addMenuItem(
        @McpToolParam(description = "gid://shopify/Menu/...", required = true)
        menu_id: String,
        @McpToolParam(description = "Displayed link label", required = true)
        title: String,
        @McpToolParam(
            description = "Target resource gid (Collection/Product/Page/Article/Blog/Metaobject). " +
                "Exclusive with url — supply exactly one of the two. The link type is derived from it.",
            required = false,
        )
        resource_id: String?,
        @McpToolParam(
            description = "Absolute external URL (http(s)) for an HTTP link. Exclusive with " +
                "resource_id. Validated (http/https schemes only).",
            required = false,
        )
        url: String?,
        @McpToolParam(
            description = "OPTIONAL — gid://shopify/MenuItem/... of the parent under which to insert " +
                "(anchor supplied by list_menus). OMITTED = insertion at the menu's top level. Refused " +
                "if the parent is already at level 3.",
            required = false,
        )
        parent_item_id: String?,
        @McpToolParam(
            description = "1-based position WITHIN THE DESIGNATED SIBLING GROUP (the children of " +
                "parent_item_id, or the top level if parent_item_id is omitted). Omitted/beyond = " +
                "appended at the end.",
            required = false,
        )
        position: Int?,
        exchange: McpSyncServerExchange,
    ): CallToolResult {
        val toolInput = buildMap {
            put("menu_id", menu_id)
            put("title", title)
            resource_id?.let { put("resource_id", it) }
            url?.let { put("url", it) }
            parent_item_id?.let { put("parent_item_id", it) }
            position?.let { put("position", it.toString()) }
        }

        return pipeline.runForActiveStore(
            "add_menu_item",
            AddMenuItemToolKind,
            exchange.sessionId(),
            toolInput,
        ) { tenant, user ->
            val slug = accessExposedService.slugFor(user.identityId, tenant.storeId)
            when {
                !menu_id.isGidOfType(MENU_GID_TYPE) -> MenuToolResults.invalidGidType(slug, "menu_id", menu_id, MENU_GID_TYPE)
                parent_item_id != null && !parent_item_id.isGidOfType(MENU_ITEM_GID_TYPE) ->
                    MenuToolResults.invalidGidType(slug, "parent_item_id", parent_item_id, MENU_ITEM_GID_TYPE)
                else -> addMenuItemUseCase.execute(tenant.storeId, menu_id, title, resource_id, url, parent_item_id, position).fold(
                    { error -> MenuToolResults.errorResult(slug, error) },
                    { result -> MenuToolResults.addMenuItemResult(slug, result) },
                )
            }
        }
    }

    companion object {
        private const val MENU_GID_TYPE = "Menu"
        private const val MENU_ITEM_GID_TYPE = "MenuItem"
    }
}
