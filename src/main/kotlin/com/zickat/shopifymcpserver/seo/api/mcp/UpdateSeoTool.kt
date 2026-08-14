package com.zickat.shopifymcpserver.seo.api.mcp

import com.zickat.shopifymcpserver.api.exposed_interface.RoutedToolPipeline
import com.zickat.shopifymcpserver.seo.domain.UpdateSeoUseCase
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
class UpdateSeoTool(
    private val pipeline: RoutedToolPipeline,
    private val accessExposedService: AccessExposedService,
    private val updateSeoUseCase: UpdateSeoUseCase,
) : HasToolUseCase {

    private object UpdateSeoToolUseCase : ToolUseCase {
        override val kind = UseCaseKind.MUTATION
    }

    override val toolUseCase: ToolUseCase = UpdateSeoToolUseCase

    @McpTool(
        name = "update_seo",
        description = "Updates the meta title and/or meta description of a product, collection, guide " +
            "(article) or page. Either field may be omitted — an omitted field keeps its current value " +
            "(merge, not erase). Omitting both is a no-op. resource_id must be the gid of the type " +
            "named by resource_type — a gid of a different type is reported as \"resource not found\". " +
            "Never changes the resource's editorial pipeline status.",
    )
    fun updateSeo(
        @McpToolParam(description = "Resource type: \"product\", \"collection\", \"article\" (guide) or \"page\".", required = true)
        resource_type: String,
        @McpToolParam(description = "gid://shopify/{Product|Collection|Article|Page}/... — consistent with resource_type.", required = true)
        resource_id: String,
        @McpToolParam(description = "OPTIONAL — new meta title. Omitted = keep the current value.", required = false)
        seo_title: String?,
        @McpToolParam(description = "OPTIONAL — new meta description. Omitted = keep the current value.", required = false)
        seo_description: String?,
        exchange: McpSyncServerExchange,
    ): CallToolResult {
        val toolInput = buildMap {
            put("resource_type", resource_type)
            put("resource_id", resource_id)
            seo_title?.let { put("seo_title", it) }
            seo_description?.let { put("seo_description", it) }
        }

        return pipeline.runForActiveStore(
            "update_seo",
            UpdateSeoToolUseCase,
            exchange.sessionId(),
            toolInput,
        ) { tenant, user ->
            val slug = accessExposedService.slugFor(user.identityId, tenant.storeId)
            val parsedResourceType = SeoResourceType.fromToolValue(resource_type)
            when {
                parsedResourceType == null -> SeoToolResults.invalidSeoResourceType(slug, resource_type)
                !resource_id.isGidOfType(parsedResourceType.gidType) ->
                    SeoToolResults.invalidGidType(slug, "resource_id", resource_id, parsedResourceType.gidType)
                else -> updateSeoUseCase.execute(tenant.storeId, parsedResourceType, resource_id, seo_title, seo_description).fold(
                    { error -> SeoToolResults.errorResult(slug, error) },
                    { result -> SeoToolResults.updateSeoResult(slug, resource_type, resource_id, result) },
                )
            }
        }
    }
}
