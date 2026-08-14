package com.zickat.shopifymcpserver.api.mcp

import arrow.core.getOrElse
import com.zickat.shopifymcpserver.api.exposed_interface.RoutedToolPipeline
import com.zickat.shopifymcpserver.shared_kernel.HasToolUseCase
import com.zickat.shopifymcpserver.shared_kernel.ToolUseCase
import com.zickat.shopifymcpserver.shared_kernel.UseCaseKind
import com.zickat.shopifymcpserver.tenancy.exposed_interface.AccessExposedService
import com.zickat.shopifymcpserver.tenancy.exposed_interface.ActiveStoreExposedService
import com.zickat.shopifymcpserver.tenancy.exposed_interface.StoreExposedService
import io.modelcontextprotocol.server.McpSyncServerExchange
import io.modelcontextprotocol.spec.McpSchema.CallToolResult
import org.springframework.ai.mcp.annotation.McpTool
import org.springframework.ai.mcp.annotation.McpToolParam
import org.springframework.stereotype.Service

@Service
class UseStoreTool(
    private val pipeline: RoutedToolPipeline,
    private val accessExposedService: AccessExposedService,
    private val activeStoreExposedService: ActiveStoreExposedService,
    private val storeExposedService: StoreExposedService,
) : HasToolUseCase {

    private object UseStoreUseCase : ToolUseCase {
        override val kind = UseCaseKind.READ
    }

    override val toolUseCase: ToolUseCase = UseStoreUseCase

    @McpTool(
        name = "use_store",
        description = "Selects the store every following tool call in this MCP session will act on. " +
            "The selection is scoped to this session only — it never leaks to another session of the " +
            "same identity, and it does not survive a server restart.",
    )
    fun useStore(
        @McpToolParam(
            description = "The store name, as returned by list_stores — e.g. \"velotrip\". Not an internal id.",
            required = true,
        ) store_id: String,
        exchange: McpSyncServerExchange,
    ): CallToolResult {
        val resolvedStoreId = storeExposedService.resolveStoreIdBySlug(store_id).getOrElse { store_id }
        return pipeline.runForStore("use_store", UseStoreUseCase, resolvedStoreId, mapOf("storeId" to store_id), store_id) { tenant, user ->
            activeStoreExposedService.select(user.identityId, exchange.sessionId(), tenant.storeId)
            val slug = accessExposedService.listGrantedStores(user.identityId).fold({ emptyList() }, { it })
                .firstOrNull { it.storeId == tenant.storeId }
                ?.slug
                ?: tenant.storeId
            McpToolResults.storeActivated(slug)
        }
    }
}
