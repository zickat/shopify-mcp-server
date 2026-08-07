package com.zickat.shopifymcpserver.api.mcp

import com.zickat.shopifymcpserver.shared_kernel.ToolUseCase
import com.zickat.shopifymcpserver.shared_kernel.UseCaseKind
import org.springframework.ai.mcp.annotation.McpTool
import org.springframework.ai.mcp.annotation.McpToolParam
import org.springframework.stereotype.Service

data class TouchStoreResult(val storeId: String, val acknowledgedBy: String)

@Service
class TouchStoreTool(private val pipeline: AuthenticatedToolPipeline) {

    private object TouchStoreUseCase : ToolUseCase {
        override val kind = UseCaseKind.MUTATION
    }

    @McpTool(
        name = "touch_store",
        description = "Trivial mutation-classified tool that only proves role-based mutation gating " +
            "end to end (LOT0-08). No real side effect, never touches Shopify.",
    )
    fun touchStore(
        @McpToolParam(description = "The store this acknowledgement applies to", required = true) storeId: String,
    ): TouchStoreResult =
        pipeline.run(
            toolName = "touch_store",
            useCase = TouchStoreUseCase,
            storeId = storeId,
            toolInput = mapOf("storeId" to storeId),
        ) { tenant, user ->
            TouchStoreResult(storeId = tenant.storeId, acknowledgedBy = user.identityId)
        }
}
