package com.zickat.shopifymcpserver.metaobjects.api.mcp

import com.zickat.shopifymcpserver.tool_dispatch.exposed_interface.RoutedToolPipeline
import com.zickat.shopifymcpserver.metaobjects.domain.DeleteMetaobjectUseCase
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
class DeleteMetaobjectTool(
    private val pipeline: RoutedToolPipeline,
    private val accessExposedService: AccessExposedService,
    private val deleteMetaobjectUseCase: DeleteMetaobjectUseCase,
) : HasToolUseCase {

    private object DeleteMetaobjectToolUseCase : ToolUseCase {
        override val kind = UseCaseKind.MUTATION
    }

    override val toolUseCase: ToolUseCase = DeleteMetaobjectToolUseCase

    @McpTool(
        name = "delete_metaobject",
        description = "Permanently deletes a metaobject (metaobjectDelete, irreversible on Shopify, " +
            "no trash). Refuses BY DEFAULT if the metaobject is still referenced by a resource " +
            "(Metaobject.referencedBy, exhaustive by construction — never declared orphan out of " +
            "excess confidence in case of truncation) — explicit override via " +
            "confirm_referenced_deletion: true. DISTINCT from confirm_out_of_scope_deletion " +
            "(delete_collection) — orthogonal risk axis (referenced or not, independent of pipeline " +
            "scope). Never cleans up the resource that referenced the deleted metaobject — explicit " +
            "warning in the report if the deletion was forced despite an active reference.",
    )
    fun deleteMetaobject(
        @McpToolParam(description = "gid://shopify/Metaobject/...", required = true)
        metaobject_id: String,
        @McpToolParam(
            description = "true to force deletion of a metaobject still referenced by at least one " +
                "resource (or whose reference detection is uncertain) — do not confuse with " +
                "delete_collection's confirm_out_of_scope_deletion. Defaults to false.",
            required = false,
        )
        confirm_referenced_deletion: Boolean?,
        exchange: McpSyncServerExchange,
    ): CallToolResult {
        val confirmReferencedDeletion = confirm_referenced_deletion ?: false
        val toolInput = mapOf("metaobject_id" to metaobject_id, "confirm_referenced_deletion" to confirmReferencedDeletion.toString())

        return pipeline.runForActiveStore(
            "delete_metaobject",
            DeleteMetaobjectToolUseCase,
            exchange.sessionId(),
            toolInput,
        ) { tenant, user ->
            val slug = accessExposedService.slugFor(user.identityId, tenant.storeId)
            if (!metaobject_id.isGidOfType(METAOBJECT_GID_TYPE)) {
                MetaobjectToolResults.invalidGidType(slug, "metaobject_id", metaobject_id, METAOBJECT_GID_TYPE)
            } else {
                deleteMetaobjectUseCase.execute(tenant.storeId, metaobject_id, confirmReferencedDeletion).fold(
                    { error -> MetaobjectToolResults.errorResult(slug, error) },
                    { result -> MetaobjectToolResults.deleteMetaobjectResult(slug, result) },
                )
            }
        }
    }

    companion object {
        private const val METAOBJECT_GID_TYPE = "Metaobject"
    }
}
