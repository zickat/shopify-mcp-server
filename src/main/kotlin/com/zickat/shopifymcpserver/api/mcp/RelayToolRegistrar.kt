package com.zickat.shopifymcpserver.api.mcp

import com.zickat.shopifymcpserver.api.exposed_interface.AuthenticatedToolPipeline
import com.zickat.shopifymcpserver.relay.exposed_interface.RelayGateway
import com.zickat.shopifymcpserver.relay.exposed_interface.RelayToolDescriptor
import com.zickat.shopifymcpserver.shared_kernel.TenantContext
import com.zickat.shopifymcpserver.shared_kernel.ToolUseCase
import com.zickat.shopifymcpserver.shared_kernel.UseCaseError
import com.zickat.shopifymcpserver.shared_kernel.UseCaseErrorException
import com.zickat.shopifymcpserver.shared_kernel.UserContext
import com.zickat.shopifymcpserver.tenancy.exposed_interface.AccessExposedService
import com.zickat.shopifymcpserver.tenancy.exposed_interface.slugFor
import io.modelcontextprotocol.json.McpJsonMapper
import io.modelcontextprotocol.json.jackson3.JacksonMcpJsonMapper
import io.modelcontextprotocol.server.McpServerFeatures
import io.modelcontextprotocol.server.McpSyncServerExchange
import io.modelcontextprotocol.spec.McpSchema
import java.util.function.BiFunction
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
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

fun relayCallResult(
    toolName: String,
    relayGateway: RelayGateway,
    accessExposedService: AccessExposedService,
    tenant: TenantContext,
    user: UserContext,
    toolInputJson: JsonElement,
): McpSchema.CallToolResult {
    val storeSlugRecognizedByTheRelayedTsProcess = accessExposedService.slugFor(user.identityId, tenant.storeId)
    return relayGateway.invoke(toolName, toolInputJson, storeSlugRecognizedByTheRelayedTsProcess, user.role).fold(
        { error -> relayErrorResult(error) },
        { outcome ->
            val builder = McpSchema.CallToolResult.builder().isError(outcome.isError)
            outcome.content.forEach { block -> builder.addTextContent(block.text) }
            builder.build()
        },
    )
}

fun relayErrorResult(error: UseCaseError): McpSchema.CallToolResult =
    McpSchema.CallToolResult.builder()
        .isError(true)
        .addTextContent(UseCaseErrorException(error).message ?: "technical.error")
        .build()

fun Map<String, Any?>.toJsonElement(): JsonElement = JsonObject(mapValues { (_, value) -> value.toJsonElement() })

private fun Any?.toJsonElement(): JsonElement = when (this) {
    null -> JsonNull
    is JsonElement -> this
    is String -> JsonPrimitive(this)
    is Boolean -> JsonPrimitive(this)
    is Int -> JsonPrimitive(this)
    is Long -> JsonPrimitive(this)
    is Double -> JsonPrimitive(this)
    is Number -> JsonPrimitive(toDouble())
    is Map<*, *> -> JsonObject(entries.associate { (key, value) -> key.toString() to value.toJsonElement() })
    is List<*> -> JsonArray(map { it.toJsonElement() })
    else -> JsonPrimitive(toString())
}
