package com.zickat.shopifymcpserver.metaobjects.api.mcp

import com.zickat.shopifymcpserver.api.exposed_interface.RoutedToolPipeline
import com.zickat.shopifymcpserver.metaobjects.domain.MetaobjectFieldInput
import com.zickat.shopifymcpserver.metaobjects.domain.UpdateMetaobjectUseCase
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
class UpdateMetaobjectTool(
    private val pipeline: RoutedToolPipeline,
    private val accessExposedService: AccessExposedService,
    private val updateMetaobjectUseCase: UpdateMetaobjectUseCase,
) : HasToolUseCase {

    private object UpdateMetaobjectToolUseCase : ToolUseCase {
        override val kind = UseCaseKind.MUTATION
    }

    override val toolUseCase: ToolUseCase = UpdateMetaobjectToolUseCase

    @McpTool(
        name = "update_metaobject",
        description = "Updates one or more fields of an existing metaobject — partial update: any " +
            "existing field not mentioned in fields is left unchanged. Rereads the metaobject's " +
            "existence before any write (isError: true if not found, same guard as " +
            "delete_metaobject). A list.*_reference field supplied (e.g. articles of a guide_theme) " +
            "is replaced wholesale by the given value — native Shopify API property, not a merge: " +
            "call get_metaobject first to compose the full list to write (e.g. drop a dead reference " +
            "without losing the others). A field declared rich_text_field server-side receives plain " +
            "text and is converted automatically (paragraphs/bullet list \"- \"/links " +
            "\"[text](url)\") — never already-built Lexical JSON. No other redundant validation " +
            "client-side: Shopify's userErrors are surfaced as-is.",
    )
    fun updateMetaobject(
        @McpToolParam(description = "gid://shopify/Metaobject/...", required = true)
        metaobject_id: String,
        @McpToolParam(
            description = "Fields to change ({key, value}) — only these fields are written. A " +
                "list.*_reference field expects the full replacement JSON list, not a merge. A " +
                "rich_text_field field expects plain text, converted automatically — not JSON.",
            required = true,
        )
        fields: List<MetaobjectFieldInput>,
        exchange: McpSyncServerExchange,
    ): CallToolResult {
        val toolInput = mapOf("metaobject_id" to metaobject_id, "fields" to fields.joinToString("|") { "${it.key}::${it.value}" })

        return pipeline.runForActiveStore(
            "update_metaobject",
            UpdateMetaobjectToolUseCase,
            exchange.sessionId(),
            toolInput,
        ) { tenant, user ->
            val slug = accessExposedService.slugFor(user.identityId, tenant.storeId)
            if (!metaobject_id.isGidOfType(METAOBJECT_GID_TYPE)) {
                MetaobjectToolResults.invalidGidType(slug, "metaobject_id", metaobject_id, METAOBJECT_GID_TYPE)
            } else {
                updateMetaobjectUseCase.execute(tenant.storeId, metaobject_id, fields).fold(
                    { error -> MetaobjectToolResults.errorResult(slug, error) },
                    { result -> MetaobjectToolResults.updateMetaobjectResult(slug, result) },
                )
            }
        }
    }

    companion object {
        private const val METAOBJECT_GID_TYPE = "Metaobject"
    }
}
