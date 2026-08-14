package com.zickat.shopifymcpserver.seo.api.mcp

import com.zickat.shopifymcpserver.tool_dispatch.exposed_interface.RoutedToolPipeline
import com.zickat.shopifymcpserver.seo.domain.GetSeoUseCase
import com.zickat.shopifymcpserver.seo.domain.models.SeoResourceType
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
class GetSeoTool(
    private val pipeline: RoutedToolPipeline,
    private val accessExposedService: AccessExposedService,
    private val getSeoUseCase: GetSeoUseCase,
) : HasToolUseCase {

    private object GetSeoToolUseCase : ToolUseCase {
        override val kind = UseCaseKind.READ
    }

    override val toolUseCase: ToolUseCase = GetSeoToolUseCase

    @McpTool(
        name = "get_seo",
        description = "Returns the meta title and meta description currently set on a product, " +
            "collection, guide (article) or page — the SEO pair shown in search results and the " +
            "browser tab. A field left unset is explicitly reported \"non défini\" (never a silent " +
            "omission). Read-only. resource_id must be the gid of the type named by resource_type — " +
            "a gid of a different type is reported as \"resource not found\".",
    )
    fun getSeo(
        @McpToolParam(description = "Resource type: \"product\", \"collection\", \"article\" (guide) or \"page\".", required = true)
        resource_type: String,
        @McpToolParam(description = "gid://shopify/{Product|Collection|Article|Page}/... — consistent with resource_type.", required = true)
        resource_id: String,
        exchange: McpSyncServerExchange,
    ): CallToolResult {
        val toolInput = mapOf("resource_type" to resource_type, "resource_id" to resource_id)

        return pipeline.runForActiveStore(
            "get_seo",
            GetSeoToolUseCase,
            exchange.sessionId(),
            toolInput,
        ) { tenant, user ->
            val slug = accessExposedService.slugFor(user.identityId, tenant.storeId)
            val parsedResourceType = SeoResourceType.fromToolValue(resource_type)
            when {
                parsedResourceType == null -> SeoToolResults.invalidSeoResourceType(slug, resource_type)
                !resource_id.isGidOfType(parsedResourceType.gidType) ->
                    SeoToolResults.invalidGidType(slug, "resource_id", resource_id, parsedResourceType.gidType)
                else -> getSeoUseCase.execute(tenant.storeId, parsedResourceType, resource_id).fold(
                    { error -> SeoToolResults.errorResult(slug, error) },
                    { result -> SeoToolResults.getSeoResult(slug, resource_type, resource_id, result) },
                )
            }
        }
    }
}
