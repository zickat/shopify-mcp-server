package com.zickat.shopifymcpserver.tool_dispatch.exposed_interface

import com.zickat.shopifymcpserver.relay.exposed_interface.RelayGateway
import com.zickat.shopifymcpserver.shared_kernel.TenantContext
import com.zickat.shopifymcpserver.shared_kernel.UseCaseError
import com.zickat.shopifymcpserver.shared_kernel.UseCaseErrorException
import com.zickat.shopifymcpserver.shared_kernel.UserContext
import com.zickat.shopifymcpserver.tenancy.exposed_interface.AccessExposedService
import com.zickat.shopifymcpserver.tenancy.exposed_interface.slugFor
import io.modelcontextprotocol.spec.McpSchema
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

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
