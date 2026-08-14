package com.zickat.shopifymcpserver.api.mcp

import com.zickat.shopifymcpserver.api.exposed_interface.RoutedToolPipeline
import com.zickat.shopifymcpserver.metaobjects.exposed_interface.MetaobjectsExposedService
import com.zickat.shopifymcpserver.shared_kernel.HasToolUseCase
import com.zickat.shopifymcpserver.shared_kernel.ToolUseCase
import com.zickat.shopifymcpserver.shared_kernel.UseCaseKind
import com.zickat.shopifymcpserver.tenancy.exposed_interface.AccessExposedService
import io.modelcontextprotocol.server.McpSyncServerExchange
import io.modelcontextprotocol.spec.McpSchema.CallToolResult
import org.springframework.ai.mcp.annotation.McpTool
import org.springframework.ai.mcp.annotation.McpToolParam
import org.springframework.stereotype.Service

@Service
class ListMetaobjectsTool(
    private val pipeline: RoutedToolPipeline,
    private val accessExposedService: AccessExposedService,
    private val metaobjectsExposedService: MetaobjectsExposedService,
) : HasToolUseCase {

    private object ListMetaobjectsToolUseCase : ToolUseCase {
        override val kind = UseCaseKind.READ
    }

    override val toolUseCase: ToolUseCase = ListMetaobjectsToolUseCase

    @McpTool(
        name = "list_metaobjects",
        description = "Without type: lists the metaobject definitions present on the store (type, " +
            "name, instance count) — including types the pipeline neither creates nor consumes " +
            "itself, to discover a legacy type without knowing it in advance. With type (free-form " +
            "string, no allowlist): lists every instance of that type with its raw fields and its " +
            "reference status (orphan / referenced by what), computed via Metaobject.referencedBy " +
            "(exhaustive by construction, not a catalog scan). Bounded scan, truncation reported " +
            "explicitly. Read-only.",
    )
    fun listMetaobjects(
        @McpToolParam(
            description = "Metaobject type, e.g. \"faq_item\" — free-form string, no allowlist. " +
                "Omitted = lists the available types with their instance count.",
            required = false,
        )
        type: String?,
        exchange: McpSyncServerExchange,
    ): CallToolResult {
        val toolInput = buildMap { type?.let { put("type", it) } }

        return pipeline.runForActiveStore(
            "list_metaobjects",
            ListMetaobjectsToolUseCase,
            exchange.sessionId(),
            toolInput,
        ) { tenant, user ->
            val slug = accessExposedService.slugFor(user.identityId, tenant.storeId)
            metaobjectsExposedService.listMetaobjects(tenant.storeId, type).fold(
                { error -> McpToolResults.errorResult(slug, error) },
                { result -> McpToolResults.listMetaobjectsResult(slug, result) },
            )
        }
    }
}
