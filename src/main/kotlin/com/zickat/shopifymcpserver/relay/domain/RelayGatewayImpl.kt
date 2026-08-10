package com.zickat.shopifymcpserver.relay.domain

import arrow.core.Either
import com.zickat.shopifymcpserver.relay.domain.models.RelayToolOutcome
import com.zickat.shopifymcpserver.relay.exposed_interface.RelayGateway
import com.zickat.shopifymcpserver.relay.exposed_interface.RelayToolDescriptor
import com.zickat.shopifymcpserver.relay.exposed_interface.ToolRoute
import com.zickat.shopifymcpserver.shared_kernel.AccessRole
import com.zickat.shopifymcpserver.shared_kernel.UseCaseError
import kotlinx.serialization.json.JsonElement
import org.springframework.stereotype.Service

@Service
class RelayGatewayImpl(
    private val manifest: RelayManifest,
    private val dispatcher: RelayDispatcher,
) : RelayGateway {

    override fun relayedTools(): List<RelayToolDescriptor> =
        manifest.relayedTools().map { RelayToolDescriptor(it.toolName, it.kind) }

    override fun routeFor(toolName: String): ToolRoute? = manifest.entryFor(toolName)?.route

    override fun invoke(
        toolName: String,
        toolInput: JsonElement,
        storeId: String,
        role: AccessRole,
    ): Either<UseCaseError, RelayToolOutcome> =
        dispatcher.callRelayedTool(toolName, toolInput, storeId, role)
}
