package com.zickat.shopifymcpserver.api.mcp

import com.zickat.shopifymcpserver.relay.exposed_interface.RelayGateway
import com.zickat.shopifymcpserver.relay.exposed_interface.RelayToolDescriptor
import com.zickat.shopifymcpserver.shared_kernel.ToolUseCase
import com.zickat.shopifymcpserver.shared_kernel.UseCaseError
import com.zickat.shopifymcpserver.shared_kernel.UseCaseErrorException
import io.modelcontextprotocol.server.McpServerFeatures
import io.modelcontextprotocol.server.McpSyncServerExchange
import io.modelcontextprotocol.spec.McpSchema
import java.util.function.BiFunction
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class RelayToolRegistrarConfiguration(
    private val relayGateway: RelayGateway,
    private val pipeline: AuthenticatedToolPipeline,
) {

    @Bean
    fun relayToolSpecifications(): List<McpServerFeatures.SyncToolSpecification> =
        relayGateway.relayedTools().map { descriptor -> relayToolSpecification(descriptor, relayGateway, pipeline) }
}

fun relayToolSpecification(
    descriptor: RelayToolDescriptor,
    relayGateway: RelayGateway,
    pipeline: AuthenticatedToolPipeline,
): McpServerFeatures.SyncToolSpecification {
    val tool = McpSchema.Tool.builder(descriptor.toolName)
        .description("Relayed tool — routed through the loopback TypeScript process (LOT2-03 scaffolding).")
        .inputSchema(McpSchema.JsonSchema.builder().type("object").properties(emptyMap()).additionalProperties(true).build())
        .build()

    val handler = BiFunction<McpSyncServerExchange, McpSchema.CallToolRequest, McpSchema.CallToolResult> { exchange, request ->
        relayToolCallResult(descriptor, relayGateway, pipeline, exchange.sessionId(), request.arguments().orEmpty())
    }

    return McpServerFeatures.SyncToolSpecification.builder().tool(tool).callHandler(handler).build()
}

fun relayToolCallResult(
    descriptor: RelayToolDescriptor,
    relayGateway: RelayGateway,
    pipeline: AuthenticatedToolPipeline,
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
            relayGateway.invoke(descriptor.toolName, toolInputJson, tenant.storeId, user.role).fold(
                { error -> relayErrorResult(error) },
                { outcome ->
                    val builder = McpSchema.CallToolResult.builder().isError(outcome.isError)
                    outcome.content.forEach { block -> builder.addTextContent(block.text) }
                    builder.build()
                },
            )
        }
    } catch (e: UseCaseErrorException) {
        relayErrorResult(e.error)
    }
}

private fun relayErrorResult(error: UseCaseError): McpSchema.CallToolResult =
    McpSchema.CallToolResult.builder()
        .isError(true)
        .addTextContent(UseCaseErrorException(error).message ?: "technical.error")
        .build()

private fun Map<String, Any?>.toJsonElement(): JsonElement = JsonObject(mapValues { (_, value) -> value.toJsonElement() })

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
