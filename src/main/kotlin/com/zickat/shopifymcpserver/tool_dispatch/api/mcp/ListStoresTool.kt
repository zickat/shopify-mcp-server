package com.zickat.shopifymcpserver.tool_dispatch.api.mcp

import com.zickat.shopifymcpserver.tool_dispatch.exposed_interface.RoutedToolPipeline
import com.zickat.shopifymcpserver.shared_kernel.HasToolUseCase
import com.zickat.shopifymcpserver.shared_kernel.ToolUseCase
import com.zickat.shopifymcpserver.shared_kernel.UseCaseKind
import com.zickat.shopifymcpserver.tenancy.exposed_interface.AccessExposedService
import com.zickat.shopifymcpserver.tenancy.exposed_interface.ActiveStoreExposedService
import io.modelcontextprotocol.server.McpSyncServerExchange
import io.modelcontextprotocol.spec.McpSchema.CallToolResult
import org.springframework.ai.mcp.annotation.McpTool
import org.springframework.stereotype.Service

@Service
class ListStoresTool(
    private val pipeline: RoutedToolPipeline,
    private val accessExposedService: AccessExposedService,
    private val activeStoreExposedService: ActiveStoreExposedService,
) : HasToolUseCase {

    private object ListStoresUseCase : ToolUseCase {
        override val kind = UseCaseKind.READ
    }

    override val toolUseCase: ToolUseCase = ListStoresUseCase

    @McpTool(
        name = "list_stores",
        description = "Lists the stores granted to the caller's identity and names which one is active " +
            "for this MCP session, if any. Names no secret — store identifiers only. Touches no " +
            "external system.",
    )
    fun listStores(exchange: McpSyncServerExchange): CallToolResult =
        pipeline.runForIdentity("list_stores", ListStoresUseCase, emptyMap()) { identityId ->
            val stores = accessExposedService.listGrantedStores(identityId).fold({ emptyList() }, { it })
            val activeStoreId = activeStoreExposedService.activeStoreFor(identityId, exchange.sessionId())
            ToolDispatchToolResults.describeStores(stores, activeStoreId)
        }
}
