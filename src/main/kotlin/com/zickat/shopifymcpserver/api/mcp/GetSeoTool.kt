package com.zickat.shopifymcpserver.api.mcp

import com.zickat.shopifymcpserver.seo.exposed_interface.SeoExposedService
import com.zickat.shopifymcpserver.shared_kernel.HasToolUseCase
import com.zickat.shopifymcpserver.shared_kernel.ToolUseCase
import com.zickat.shopifymcpserver.shared_kernel.UseCaseKind
import com.zickat.shopifymcpserver.shared_kernel.isGidOfType
import com.zickat.shopifymcpserver.tenancy.exposed_interface.AccessExposedService
import io.modelcontextprotocol.server.McpSyncServerExchange
import io.modelcontextprotocol.spec.McpSchema.CallToolResult
import org.springframework.ai.mcp.annotation.McpTool
import org.springframework.ai.mcp.annotation.McpToolParam
import org.springframework.stereotype.Service

@Service
class GetSeoTool(
    private val pipeline: RoutedToolPipeline,
    private val accessExposedService: AccessExposedService,
    private val seoExposedService: SeoExposedService,
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
            val expectedGidType = SEO_RESOURCE_GID_TYPES[resource_type]
            when {
                expectedGidType == null -> McpToolResults.invalidSeoResourceType(slug, resource_type)
                !resource_id.isGidOfType(expectedGidType) ->
                    McpToolResults.invalidGidType(slug, "resource_id", resource_id, expectedGidType)
                else -> seoExposedService.getSeo(tenant.storeId, resource_type, resource_id).fold(
                    { error -> McpToolResults.errorResult(slug, error) },
                    { result -> McpToolResults.getSeoResult(slug, resource_type, resource_id, result) },
                )
            }
        }
    }

    companion object {
        private val SEO_RESOURCE_GID_TYPES = mapOf(
            "product" to "Product",
            "collection" to "Collection",
            "article" to "Article",
            "page" to "Page",
        )
    }
}
