package com.zickat.shopifymcpserver.menus.api.mcp

import com.zickat.shopifymcpserver.tool_dispatch.exposed_interface.RoutedToolPipeline
import com.zickat.shopifymcpserver.menus.domain.UpdateMenuUseCase
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
class UpdateMenuTool(
    private val pipeline: RoutedToolPipeline,
    private val accessExposedService: AccessExposedService,
    private val updateMenuUseCase: UpdateMenuUseCase,
) : HasToolUseCase {

    private object UpdateMenuToolKind : ToolUseCase {
        override val kind = UseCaseKind.MUTATION
    }

    override val toolUseCase: ToolUseCase = UpdateMenuToolKind

    @McpTool(
        name = "update_menu",
        description = "Updates the menu's own TITLE and/or HANDLE — not its items. The title is " +
            "cosmetic and needs no confirmation. THE HANDLE IS FAR MORE DANGEROUS, and that is why the " +
            "guardrails exist: a theme references its menus BY HANDLE (section.settings.menu stores a " +
            "handle, not an id) — changing it makes the corresponding navigation area disappear WITH " +
            "NO error, NO log, without Shopify signalling anything. Hence: (1) on a default menu " +
            "(\", par défaut\" mention in list_menus — main-menu, footer), the handle change is " +
            "REFUSED and this refusal is NEVER overridable, Shopify itself forbidding it ; the title " +
            "stays editable there ; (2) on any other menu, the handle change requires " +
            "confirm_handle_change: true. This confirmation is a DELIBERATE SLOWDOWN, NOT A " +
            "GUARANTEE: the connector cannot verify whether a handle is referenced by the theme " +
            "without stepping outside this repository. The report always recalls the old handle, " +
            "which makes rolling back trivial. Under the hood, menuUpdate requires the items on every " +
            "call: the operation is a read-modify-write cycle with an identity transformation, " +
            "protected by the same guardrails as the other mutations (no item can be lost along the " +
            "way). isError with no write at all if: menu not found, neither title nor handle supplied, " +
            "handle without confirmation, handle on a default menu, or Shopify userErrors (handle " +
            "already taken).",
    )
    fun updateMenu(
        @McpToolParam(description = "gid://shopify/Menu/...", required = true)
        menu_id: String,
        @McpToolParam(
            description = "OPTIONAL — new displayed menu title. Cosmetic, no confirmation required, " +
                "editable even on a default menu.",
            required = false,
        )
        title: String?,
        @McpToolParam(
            description = "OPTIONAL — new handle. BREAKS every theme reference to this menu (the " +
                "navigation area silently disappears). Requires confirm_handle_change: true, and is " +
                "always refused on a default menu. Shopify normalizes the value (lowercase, dashes) and " +
                "refuses collisions.",
            required = false,
        )
        handle: String?,
        @McpToolParam(
            description = "true to allow the handle change — never overrides the refusal on a default " +
                "menu. Do not confuse with confirm_non_empty_deletion (delete_menu): a distinct risk " +
                "axis (breaking a theme reference, not destroying content). No effect if handle is not " +
                "supplied. Defaults to false.",
            required = false,
        )
        confirm_handle_change: Boolean?,
        exchange: McpSyncServerExchange,
    ): CallToolResult {
        val confirmHandleChange = confirm_handle_change ?: false
        val toolInput = buildMap {
            put("menu_id", menu_id)
            title?.let { put("title", it) }
            handle?.let { put("handle", it) }
            put("confirm_handle_change", confirmHandleChange.toString())
        }

        return pipeline.runForActiveStore(
            "update_menu",
            UpdateMenuToolKind,
            exchange.sessionId(),
            toolInput,
        ) { tenant, user ->
            val slug = accessExposedService.slugFor(user.identityId, tenant.storeId)
            if (!menu_id.isGidOfType(MENU_GID_TYPE)) {
                MenuToolResults.invalidGidType(slug, "menu_id", menu_id, MENU_GID_TYPE)
            } else {
                updateMenuUseCase.execute(tenant.storeId, menu_id, title, handle, confirmHandleChange).fold(
                    { error -> MenuToolResults.errorResult(slug, error) },
                    { result -> MenuToolResults.updateMenuResult(slug, result) },
                )
            }
        }
    }

    companion object {
        private const val MENU_GID_TYPE = "Menu"
    }
}
