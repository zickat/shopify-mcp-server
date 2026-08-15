package com.zickat.shopifymcpserver.menus.api.mcp

import com.zickat.shopifymcpserver.tool_dispatch.exposed_interface.RoutedToolPipeline
import com.zickat.shopifymcpserver.menus.domain.CreateMenuItemInput
import com.zickat.shopifymcpserver.menus.domain.CreateMenuUseCase
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
class CreateMenuTool(
    private val pipeline: RoutedToolPipeline,
    private val accessExposedService: AccessExposedService,
    private val createMenuUseCase: CreateMenuUseCase,
) : HasToolUseCase {

    private object CreateMenuToolKind : ToolUseCase {
        override val kind = UseCaseKind.MUTATION
    }

    override val toolUseCase: ToolUseCase = CreateMenuToolKind

    @McpTool(
        name = "create_menu",
        description = "Creates a new navigation menu (menuCreate). items is a FLAT list of top-level " +
            "items, optional: NO NESTED TREE is accepted as input — this is deliberate, a second item " +
            "constructor would be a second place to validate a url and derive a type. To populate " +
            "depth, call add_menu_item afterwards with parent_item_id: a 3-level menu is built in a " +
            "few calls. Each item requires EXACTLY one of resource_id or url, same rules as " +
            "add_menu_item. handle is NOT pre-validated client-side: Shopify normalizes it (lowercase, " +
            "dashes) and refuses collisions — the report shows the ACTUALLY ASSIGNED handle and " +
            "explicitly signals if it differs from the requested one. The created menu is never a " +
            "default menu: it will be deletable by delete_menu, and its handle stays editable by " +
            "update_menu.",
    )
    fun createMenu(
        @McpToolParam(
            description = "Desired handle (e.g. \"guides-sidebar\"). Shopify normalizes it and refuses " +
                "collisions — the actually assigned handle is returned in the report. A theme " +
                "references the menu by this value.",
            required = true,
        )
        handle: String,
        @McpToolParam(description = "Displayed menu title", required = true)
        title: String,
        @McpToolParam(
            description = "OPTIONAL — TOP-LEVEL items only, in the desired order. Omitted or empty: " +
                "menu created with no item. No sub-items here: use add_menu_item (parent_item_id) " +
                "afterwards. Each item accepts only title/resource_id/url.",
            required = false,
        )
        items: List<CreateMenuItemInput>?,
        exchange: McpSyncServerExchange,
    ): CallToolResult {
        val requestedItems = items.orEmpty()
        val toolInput = mapOf(
            "handle" to handle,
            "title" to title,
            "items" to requestedItems.joinToString("|") { "${it.title}::${it.resource_id.orEmpty()}::${it.url.orEmpty()}" },
        )

        return pipeline.runForActiveStore(
            "create_menu",
            CreateMenuToolKind,
            exchange.sessionId(),
            toolInput,
        ) { tenant, user ->
            val slug = accessExposedService.slugFor(user.identityId, tenant.storeId)
            createMenuUseCase.execute(tenant.storeId, handle, title, requestedItems).fold(
                { error -> MenuToolResults.errorResult(slug, error) },
                { result -> MenuToolResults.createMenuResult(slug, result) },
            )
        }
    }
}
