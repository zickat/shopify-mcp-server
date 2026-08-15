package com.zickat.shopifymcpserver.menus.api.mcp

import com.zickat.shopifymcpserver.tool_dispatch.exposed_interface.RoutedToolPipeline
import com.zickat.shopifymcpserver.menus.domain.DeleteMenuUseCase
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
class DeleteMenuTool(
    private val pipeline: RoutedToolPipeline,
    private val accessExposedService: AccessExposedService,
    private val deleteMenuUseCase: DeleteMenuUseCase,
) : HasToolUseCase {

    private object DeleteMenuToolKind : ToolUseCase {
        override val kind = UseCaseKind.MUTATION
    }

    override val toolUseCase: ToolUseCase = DeleteMenuToolKind

    @McpTool(
        name = "delete_menu",
        description = "PERMANENTLY deletes a menu and its ENTIRE item tree (menuDelete, irreversible on " +
            "Shopify). TWO cumulative and independent guardrails: (1) a DEFAULT menu (\", par défaut\" " +
            "mention in list_menus — main-menu and footer) is refused in ALL cases, this refusal is " +
            "NEVER overridable, Shopify forbidding it itself ; (2) a menu carrying at least one item is " +
            "refused unless confirm_non_empty_deletion: true. Guardrail (2) is an IMPERFECT PROXY: " +
            "\"carries items\" is not \"is in use\" — an empty menu can be referenced by the theme, a " +
            "populated menu can be a draft. The connector cannot verify whether a theme references " +
            "this menu (it does so by handle, in Liquid outside this repository). menuDelete only " +
            "returns the deleted id: the menu is therefore REREAD before deletion and the success " +
            "report contains the FULL TREE of the destroyed menu — the only remaining copy and the " +
            "only possible rebuild plan. isError with no deletion at all if: menu not found, default " +
            "menu, non-empty menu without confirmation, or Shopify userErrors.",
    )
    fun deleteMenu(
        @McpToolParam(description = "gid://shopify/Menu/...", required = true)
        menu_id: String,
        @McpToolParam(
            description = "true to delete a menu that carries at least one item — NEVER overrides the " +
                "refusal on a default menu. Do not confuse with confirm_out_of_scope_deletion " +
                "(delete_collection), confirm_referenced_deletion (delete_metaobject) nor " +
                "confirm_handle_change (update_menu): a distinct risk axis (destroying a navigation " +
                "tree representing work). Defaults to false.",
            required = false,
        )
        confirm_non_empty_deletion: Boolean?,
        exchange: McpSyncServerExchange,
    ): CallToolResult {
        val confirmNonEmptyDeletion = confirm_non_empty_deletion ?: false
        val toolInput = mapOf("menu_id" to menu_id, "confirm_non_empty_deletion" to confirmNonEmptyDeletion.toString())

        return pipeline.runForActiveStore(
            "delete_menu",
            DeleteMenuToolKind,
            exchange.sessionId(),
            toolInput,
        ) { tenant, user ->
            val slug = accessExposedService.slugFor(user.identityId, tenant.storeId)
            if (!menu_id.isGidOfType(MENU_GID_TYPE)) {
                MenuToolResults.invalidGidType(slug, "menu_id", menu_id, MENU_GID_TYPE)
            } else {
                deleteMenuUseCase.execute(tenant.storeId, menu_id, confirmNonEmptyDeletion).fold(
                    { error -> MenuToolResults.errorResult(slug, error) },
                    { result -> MenuToolResults.deleteMenuResult(slug, result) },
                )
            }
        }
    }

    companion object {
        private const val MENU_GID_TYPE = "Menu"
    }
}
