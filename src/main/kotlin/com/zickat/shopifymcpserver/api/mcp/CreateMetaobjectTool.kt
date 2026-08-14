package com.zickat.shopifymcpserver.api.mcp

import com.zickat.shopifymcpserver.api.exposed_interface.RoutedToolPipeline
import com.zickat.shopifymcpserver.metaobjects.exposed_interface.MetaobjectsExposedService
import com.zickat.shopifymcpserver.metaobjects.exposed_interface.model.MetaobjectFieldInput
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
class CreateMetaobjectTool(
    private val pipeline: RoutedToolPipeline,
    private val accessExposedService: AccessExposedService,
    private val metaobjectsExposedService: MetaobjectsExposedService,
) : HasToolUseCase {

    private object CreateMetaobjectToolUseCase : ToolUseCase {
        override val kind = UseCaseKind.MUTATION
    }

    override val toolUseCase: ToolUseCase = CreateMetaobjectToolUseCase

    @McpTool(
        name = "create_metaobject",
        description = "Creates a new instance of a metaobject type already defined in the Shopify " +
            "schema (type is a free string, no allowlist — covers a legacy type like guide_theme). " +
            "A field declared rich_text_field server-side (e.g. guide_section.body) receives the " +
            "value as plain text and converts it automatically (paragraphs separated by a blank " +
            "line, bullet list via \"- \", links \"[text](url)\") — never supply already-built " +
            "Lexical JSON on these fields. No other redundant validation client-side: Shopify is the " +
            "sole source of truth on existing definitions, its userErrors are surfaced as-is (unknown " +
            "type, missing required field, incompatible value...). No editorial-consistency judgment " +
            "(unlike propose_collection) — delegated to the resource that will reference this " +
            "metaobject. Not idempotent: calling this again with the same fields creates a new " +
            "metaobject every time.",
    )
    fun createMetaobject(
        @McpToolParam(description = "Metaobject type, e.g. \"guide_theme\", \"faq_item\" — free string, no allowlist.", required = true)
        type: String,
        @McpToolParam(
            description = "Fields to write ({key, value}). A list.*_reference field (e.g. articles) " +
                "expects a JSON string of a gid array (e.g. \"[\\\"gid://shopify/Article/1\\\"]\"). A " +
                "rich_text_field field expects plain text, converted automatically — not JSON.",
            required = true,
        )
        fields: List<MetaobjectFieldInput>,
        exchange: McpSyncServerExchange,
    ): CallToolResult {
        val toolInput = mapOf("type" to type, "fields" to fields.joinToString("|") { "${it.key}::${it.value}" })

        return pipeline.runForActiveStore(
            "create_metaobject",
            CreateMetaobjectToolUseCase,
            exchange.sessionId(),
            toolInput,
        ) { tenant, user ->
            val slug = accessExposedService.slugFor(user.identityId, tenant.storeId)
            metaobjectsExposedService.createMetaobject(tenant.storeId, type, fields).fold(
                { error -> McpToolResults.errorResult(slug, error) },
                { result -> McpToolResults.createMetaobjectResult(slug, result) },
            )
        }
    }
}
