package com.zickat.shopifymcpserver.tool_dispatch.api.mcp

import com.zickat.shopifymcpserver.tool_dispatch.exposed_interface.AuthenticatedToolPipeline
import com.zickat.shopifymcpserver.tool_dispatch.exposed_interface.relayCallResult
import com.zickat.shopifymcpserver.tool_dispatch.exposed_interface.relayErrorResult
import com.zickat.shopifymcpserver.tool_dispatch.exposed_interface.toJsonElement
import com.zickat.shopifymcpserver.relay.exposed_interface.RelayGateway
import com.zickat.shopifymcpserver.relay.exposed_interface.RelayToolDescriptor
import com.zickat.shopifymcpserver.shared_kernel.ToolUseCase
import com.zickat.shopifymcpserver.shared_kernel.UseCaseErrorException
import com.zickat.shopifymcpserver.tenancy.exposed_interface.AccessExposedService
import io.modelcontextprotocol.json.McpJsonMapper
import io.modelcontextprotocol.json.jackson3.JacksonMcpJsonMapper
import io.modelcontextprotocol.server.McpServerFeatures
import io.modelcontextprotocol.server.McpSyncServerExchange
import io.modelcontextprotocol.spec.McpSchema
import java.util.function.BiFunction
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import tools.jackson.databind.json.JsonMapper

@Configuration
class RelayToolRegistrarConfiguration(
    private val relayGateway: RelayGateway,
    private val pipeline: AuthenticatedToolPipeline,
    private val accessExposedService: AccessExposedService,
    private val nativeToolNames: NativeToolNames,
    private val contracts: RelayToolContracts,
) {

    @Bean
    fun relayToolJsonMapper(@Qualifier("mcpServerJsonMapper") jsonMapper: JsonMapper): McpJsonMapper =
        JacksonMcpJsonMapper(jsonMapper)

    @Bean
    fun relayToolSpecifications(relayToolJsonMapper: McpJsonMapper): List<McpServerFeatures.SyncToolSpecification> =
        relayGateway.relayedTools()
            .filterNot { it.toolName in nativeToolNames.names }
            .map { descriptor -> relayToolSpecification(descriptor, relayGateway, pipeline, accessExposedService, contracts, relayToolJsonMapper) }
}

fun relayToolSpecification(
    descriptor: RelayToolDescriptor,
    relayGateway: RelayGateway,
    pipeline: AuthenticatedToolPipeline,
    accessExposedService: AccessExposedService,
    contracts: RelayToolContracts,
    jsonMapper: McpJsonMapper,
): McpServerFeatures.SyncToolSpecification {
    val contract = contracts.byToolName[descriptor.toolName]
        ?: error("relay contract missing for tool '${descriptor.toolName}' — regenerate the artifact (LOT2-19)")

    val tool = McpSchema.Tool.builder(descriptor.toolName)
        .description(contract.description)
        .inputSchema(jsonMapper, contract.inputSchemaJson)
        .build()

    val handler = BiFunction<McpSyncServerExchange, McpSchema.CallToolRequest, McpSchema.CallToolResult> { exchange, request ->
        relayToolCallResult(descriptor, relayGateway, pipeline, accessExposedService, exchange.sessionId(), request.arguments().orEmpty())
    }

    return McpServerFeatures.SyncToolSpecification.builder().tool(tool).callHandler(handler).build()
}

fun relayToolCallResult(
    descriptor: RelayToolDescriptor,
    relayGateway: RelayGateway,
    pipeline: AuthenticatedToolPipeline,
    accessExposedService: AccessExposedService,
    sessionId: String,
    arguments: Map<String, Any?>,
): McpSchema.CallToolResult {
    val useCase = object : ToolUseCase {
        override val kind = descriptor.kind
    }
    val toolInputJson = arguments.toJsonElement()
    val toolInputDigest = arguments.mapValues { (_, value) -> value?.toString().orEmpty() }

    return try {
        pipeline.runForActiveStore(descriptor.toolName, useCase, sessionId, toolInputDigest) { tenant, user ->
            relayCallResult(descriptor.toolName, relayGateway, accessExposedService, tenant, user, toolInputJson)
        }
    } catch (e: UseCaseErrorException) {
        relayErrorResult(e.error)
    }
}
