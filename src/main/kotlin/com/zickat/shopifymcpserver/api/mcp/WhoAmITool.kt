package com.zickat.shopifymcpserver.api.mcp

import com.zickat.shopifymcpserver.shared_kernel.ToolUseCase
import com.zickat.shopifymcpserver.shared_kernel.UseCaseKind
import org.springframework.ai.mcp.annotation.McpTool
import org.springframework.ai.mcp.annotation.McpToolParam
import org.springframework.stereotype.Service

data class WhoAmIResult(val identityId: String, val storeId: String, val role: String)

@Service
class WhoAmITool(private val pipeline: AuthenticatedToolPipeline) {

    private object WhoAmIUseCase : ToolUseCase {
        override val kind = UseCaseKind.READ
    }

    @McpTool(
        name = "whoami",
        description = "Reports the caller's resolved identity and role on a given store — proves the " +
            "authentication and tenancy chain end to end. Touches no external system.",
    )
    fun whoami(
        @McpToolParam(description = "The store to check access for", required = true) storeId: String,
    ): WhoAmIResult =
        pipeline.run(
            toolName = "whoami",
            useCase = WhoAmIUseCase,
            storeId = storeId,
            toolInput = mapOf("storeId" to storeId),
        ) { tenant, user ->
            WhoAmIResult(identityId = user.identityId, storeId = tenant.storeId, role = user.role.name)
        }
}
