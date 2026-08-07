package com.zickat.shopifymcpserver.api.mcp

import com.zickat.shopifymcpserver.redirects.exposed_interface.RedirectsExposedService
import com.zickat.shopifymcpserver.shared_kernel.ToolUseCase
import com.zickat.shopifymcpserver.shared_kernel.UseCaseKind
import com.zickat.shopifymcpserver.tenancy.exposed_interface.AccessExposedService
import io.modelcontextprotocol.server.McpSyncServerExchange
import io.modelcontextprotocol.spec.McpSchema.CallToolResult
import org.springframework.ai.mcp.annotation.McpTool
import org.springframework.ai.mcp.annotation.McpToolParam
import org.springframework.stereotype.Service

@Service
class CreateRedirectTool(
    private val pipeline: AuthenticatedToolPipeline,
    private val accessExposedService: AccessExposedService,
    private val redirectsExposedService: RedirectsExposedService,
) {

    private object CreateRedirectToolUseCase : ToolUseCase {
        override val kind = UseCaseKind.MUTATION
    }

    @McpTool(
        name = "create_redirect",
        description = "Creates an Admin URL redirect from an old public path (from_path) to a new one " +
            "(to_path), so a broken URL does not fall through to a 404 (deleted collection, " +
            "reorganized guide/page, after-the-fact fix). Idempotent: recreating a redirect that " +
            "already exists for this from_path is a success, not an error — but the target is then " +
            "neither checked nor updated (create only, no target update). Both paths are required. " +
            "Touches no other resource on the store.",
    )
    fun createRedirect(
        @McpToolParam(
            description = "REQUIRED — old public path to redirect, e.g. \"/collections/old-slug\"",
            required = true,
        )
        from_path: String,
        @McpToolParam(
            description = "REQUIRED — new target path, e.g. \"/collections/new-slug\" or \"/pages/replacement-guide\"",
            required = true,
        )
        to_path: String,
        exchange: McpSyncServerExchange,
    ): CallToolResult =
        pipeline.runForActiveStore(
            "create_redirect",
            CreateRedirectToolUseCase,
            exchange.sessionId(),
            mapOf("from_path" to from_path, "to_path" to to_path),
        ) { tenant, user ->
            val slug = accessExposedService.slugFor(user.identityId, tenant.storeId)
            redirectsExposedService.createRedirect(tenant.storeId, from_path, to_path).fold(
                { error -> McpToolResults.errorResult(slug, error) },
                { outcome -> McpToolResults.createRedirectResult(slug, from_path, to_path, outcome) },
            )
        }
}
