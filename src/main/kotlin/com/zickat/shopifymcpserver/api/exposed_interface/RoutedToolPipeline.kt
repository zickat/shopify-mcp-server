package com.zickat.shopifymcpserver.api.exposed_interface

import com.zickat.shopifymcpserver.api.mcp.relayCallResult
import com.zickat.shopifymcpserver.api.mcp.toJsonElement
import com.zickat.shopifymcpserver.relay.exposed_interface.RelayGateway
import com.zickat.shopifymcpserver.relay.exposed_interface.ToolRoute
import com.zickat.shopifymcpserver.shared_kernel.NotFoundError
import com.zickat.shopifymcpserver.shared_kernel.TenantContext
import com.zickat.shopifymcpserver.shared_kernel.ToolUseCase
import com.zickat.shopifymcpserver.shared_kernel.UseCaseErrorException
import com.zickat.shopifymcpserver.shared_kernel.UserContext
import com.zickat.shopifymcpserver.tenancy.exposed_interface.AccessExposedService
import io.modelcontextprotocol.spec.McpSchema
import org.springframework.modulith.NamedInterface
import org.springframework.stereotype.Component

@NamedInterface("exposed_interface")
@Component
class RoutedToolPipeline(
    private val pipeline: AuthenticatedToolPipeline,
    private val relayGateway: RelayGateway,
    private val accessExposedService: AccessExposedService,
) {

    fun runForStore(
        toolName: String,
        useCase: ToolUseCase,
        storeId: String,
        toolInput: Map<String, String>,
        operatorLabel: String,
        nativeAction: (TenantContext, UserContext) -> McpSchema.CallToolResult,
    ): McpSchema.CallToolResult =
        pipeline.runForStore(toolName, useCase, storeId, toolInput, operatorLabel) { tenant, user ->
            dispatch(toolName, tenant, user, toolInput, nativeAction)
        }

    fun runForActiveStore(
        toolName: String,
        useCase: ToolUseCase,
        sessionId: String,
        toolInput: Map<String, String>,
        nativeAction: (TenantContext, UserContext) -> McpSchema.CallToolResult,
    ): McpSchema.CallToolResult =
        pipeline.runForActiveStore(toolName, useCase, sessionId, toolInput) { tenant, user ->
            dispatch(toolName, tenant, user, toolInput, nativeAction)
        }

    fun <T> runForIdentity(
        toolName: String,
        useCase: ToolUseCase,
        toolInput: Map<String, String>,
        action: (String) -> T,
    ): T = pipeline.runForIdentity(toolName, useCase, toolInput, action)

    private fun dispatch(
        toolName: String,
        tenant: TenantContext,
        user: UserContext,
        toolInput: Map<String, String>,
        nativeAction: (TenantContext, UserContext) -> McpSchema.CallToolResult,
    ): McpSchema.CallToolResult =
        when (relayGateway.routeFor(toolName)) {
            ToolRoute.RELAIS -> relayCallResult(toolName, relayGateway, accessExposedService, tenant, user, toolInput.toJsonElement())
            ToolRoute.NATIF -> nativeAction(tenant, user)
            null -> throw UseCaseErrorException(NotFoundError("tool.not.in.manifest", mapOf("toolName" to toolName)))
        }
}
