package com.zickat.shopifymcpserver.api.mcp

import com.zickat.shopifymcpserver.shared_kernel.ToolUseCase
import com.zickat.shopifymcpserver.shared_kernel.UseCaseKind
import com.zickat.shopifymcpserver.tenancy.exposed_interface.AccessExposedService
import com.zickat.shopifymcpserver.tenancy.exposed_interface.ActiveStoreExposedService
import io.modelcontextprotocol.server.McpSyncServerExchange
import io.modelcontextprotocol.spec.McpSchema.CallToolResult
import org.springframework.ai.mcp.annotation.McpTool
import org.springframework.ai.mcp.annotation.McpToolParam
import org.springframework.stereotype.Service

@Service
class UseStoreTool(
    private val pipeline: AuthenticatedToolPipeline,
    private val accessExposedService: AccessExposedService,
    private val activeStoreExposedService: ActiveStoreExposedService,
) {

    private object UseStoreUseCase : ToolUseCase {
        override val kind = UseCaseKind.READ
    }

    @McpTool(
        name = "use_store",
        description = "Selects the store every following tool call in this MCP session will act on. " +
            "The selection is scoped to this session only — it never leaks to another session of the " +
            "same identity, and it does not survive a server restart.",
    )
    fun useStore(
        @McpToolParam(description = "The store to select, as returned by list_stores", required = true) store_id: String,
        exchange: McpSyncServerExchange,
    ): CallToolResult =
        pipeline.runForStore("use_store", UseStoreUseCase, store_id, mapOf("storeId" to store_id)) { tenant, user ->
            activeStoreExposedService.select(user.identityId, exchange.sessionId(), tenant.storeId)
            val slug = accessExposedService.listGrantedStores(user.identityId).fold({ emptyList() }, { it })
                .firstOrNull { it.storeId == tenant.storeId }
                ?.slug
                ?: tenant.storeId
            McpToolResults.storeActivated(slug)
        }
}
