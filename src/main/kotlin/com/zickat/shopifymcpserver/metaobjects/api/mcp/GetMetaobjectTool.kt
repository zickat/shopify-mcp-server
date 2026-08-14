package com.zickat.shopifymcpserver.metaobjects.api.mcp

import com.zickat.shopifymcpserver.tool_dispatch.exposed_interface.RoutedToolPipeline
import com.zickat.shopifymcpserver.metaobjects.domain.GetMetaobjectUseCase
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
class GetMetaobjectTool(
    private val pipeline: RoutedToolPipeline,
    private val accessExposedService: AccessExposedService,
    private val getMetaobjectUseCase: GetMetaobjectUseCase,
) : HasToolUseCase {

    private object GetMetaobjectToolUseCase : ToolUseCase {
        override val kind = UseCaseKind.READ
    }

    override val toolUseCase: ToolUseCase = GetMetaobjectToolUseCase

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
            if (!metaobject_id.isGidOfType(METAOBJECT_GID_TYPE)) {
                MetaobjectToolResults.invalidGidType(slug, "metaobject_id", metaobject_id, METAOBJECT_GID_TYPE)
            } else {
                getMetaobjectUseCase.execute(tenant.storeId, metaobject_id).fold(
                    { error -> MetaobjectToolResults.errorResult(slug, error) },
                    { result -> MetaobjectToolResults.getMetaobjectResult(slug, result) },
                )
            }
        }
    }

    companion object {
        private const val METAOBJECT_GID_TYPE = "Metaobject"
    }
}
