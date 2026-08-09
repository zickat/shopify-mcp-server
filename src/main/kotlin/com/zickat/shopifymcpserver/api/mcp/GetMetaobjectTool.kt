package com.zickat.shopifymcpserver.api.mcp

import com.zickat.shopifymcpserver.metaobjects.exposed_interface.MetaobjectsExposedService
import com.zickat.shopifymcpserver.shared_kernel.ToolUseCase
import com.zickat.shopifymcpserver.shared_kernel.UseCaseKind
import com.zickat.shopifymcpserver.tenancy.exposed_interface.AccessExposedService
import io.modelcontextprotocol.server.McpSyncServerExchange
import io.modelcontextprotocol.spec.McpSchema.CallToolResult
import org.springframework.ai.mcp.annotation.McpTool
import org.springframework.ai.mcp.annotation.McpToolParam
import org.springframework.stereotype.Service

@Service
class GetMetaobjectTool(
    private val pipeline: AuthenticatedToolPipeline,
    private val accessExposedService: AccessExposedService,
    private val metaobjectsExposedService: MetaobjectsExposedService,
) {

    private object GetMetaobjectToolUseCase : ToolUseCase {
        override val kind = UseCaseKind.READ
    }

    @McpTool(
        name = "get_metaobject",
        description = "Returns the same level of information as a list_metaobjects entry (type, " +
            "raw fields, orphan/referenced reference status) for a metaobject already identified by " +
            "its id — avoids going back through a full listing. Read-only.",
    )
    fun getMetaobject(
        @McpToolParam(description = "gid://shopify/Metaobject/...", required = true)
        metaobject_id: String,
        exchange: McpSyncServerExchange,
    ): CallToolResult {
        val toolInput = mapOf("metaobject_id" to metaobject_id)

        return pipeline.runForActiveStore(
            "get_metaobject",
            GetMetaobjectToolUseCase,
            exchange.sessionId(),
            toolInput,
        ) { tenant, user ->
            val slug = accessExposedService.slugFor(user.identityId, tenant.storeId)
            metaobjectsExposedService.getMetaobject(tenant.storeId, metaobject_id).fold(
                { error -> McpToolResults.errorResult(slug, error) },
                { result -> McpToolResults.getMetaobjectResult(slug, result) },
            )
        }
    }
}
