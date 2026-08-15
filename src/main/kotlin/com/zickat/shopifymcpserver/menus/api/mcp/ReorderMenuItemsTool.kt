package com.zickat.shopifymcpserver.menus.api.mcp

import com.zickat.shopifymcpserver.tool_dispatch.exposed_interface.RoutedToolPipeline
import com.zickat.shopifymcpserver.menus.domain.ReorderMenuItemsUseCase
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
class ReorderMenuItemsTool(
    private val pipeline: RoutedToolPipeline,
    private val accessExposedService: AccessExposedService,
    private val reorderMenuItemsUseCase: ReorderMenuItemsUseCase,
) : HasToolUseCase {

    private object ReorderMenuItemsToolKind : ToolUseCase {
        override val kind = UseCaseKind.MUTATION
    }

    override val toolUseCase: ToolUseCase = ReorderMenuItemsToolKind

    @McpTool(
        name = "reorder_menu_items",
        description = "Reorders ONE SIBLING GROUP of a menu according to ordered_item_ids. The targeted " +
            "group is the children of parent_item_id ; parent_item_id OMITTED, it is the menu's top-" +
            "level items. ordered_item_ids must be an EXACT PERMUTATION of that group's item_id (same " +
            "cardinality, same elements, no duplicate, no unknown) — items from other sibling groups " +
            "do not belong here. Otherwise isError with NO modification at all (never a partial " +
            "state). Each permuted item carries its sub-menu as-is ; other branches are untouched. Use " +
            "list_menus to retrieve the targeted group's item_id (their numbering shares the same " +
            "prefix).",
    )
    fun reorderMenuItems(
        @McpToolParam(description = "gid://shopify/Menu/...", required = true)
        menu_id: String,
        @McpToolParam(
            description = "OPTIONAL — gid://shopify/MenuItem/... whose CHILDREN are being reordered. " +
                "OMITTED = reorders the menu's top-level items.",
            required = false,
        )
        parent_item_id: String?,
        @McpToolParam(
            description = "ORDERED list of item_id of the group DESIGNATED by parent_item_id (or the " +
                "top level if omitted), in the target order. Must reference exactly that group's item " +
                "set (permutation).",
            required = true,
        )
        ordered_item_ids: List<String>,
        exchange: McpSyncServerExchange,
    ): CallToolResult {
        val toolInput = buildMap {
            put("menu_id", menu_id)
            parent_item_id?.let { put("parent_item_id", it) }
            put("ordered_item_ids", ordered_item_ids.joinToString(","))
        }

        return pipeline.runForActiveStore(
            "reorder_menu_items",
            ReorderMenuItemsToolKind,
            exchange.sessionId(),
            toolInput,
        ) { tenant, user ->
            val slug = accessExposedService.slugFor(user.identityId, tenant.storeId)
            val invalidOrderedId = ordered_item_ids.firstOrNull { !it.isGidOfType(MENU_ITEM_GID_TYPE) }
            when {
                !menu_id.isGidOfType(MENU_GID_TYPE) -> MenuToolResults.invalidGidType(slug, "menu_id", menu_id, MENU_GID_TYPE)
                parent_item_id != null && !parent_item_id.isGidOfType(MENU_ITEM_GID_TYPE) ->
                    MenuToolResults.invalidGidType(slug, "parent_item_id", parent_item_id, MENU_ITEM_GID_TYPE)
                invalidOrderedId != null -> MenuToolResults.invalidGidType(slug, "ordered_item_ids", invalidOrderedId, MENU_ITEM_GID_TYPE)
                else -> reorderMenuItemsUseCase.execute(tenant.storeId, menu_id, parent_item_id, ordered_item_ids).fold(
                    { error -> MenuToolResults.errorResult(slug, error) },
                    { result -> MenuToolResults.reorderMenuItemsResult(slug, result) },
                )
            }
        }
    }

    companion object {
        private const val MENU_GID_TYPE = "Menu"
        private const val MENU_ITEM_GID_TYPE = "MenuItem"
    }
}
