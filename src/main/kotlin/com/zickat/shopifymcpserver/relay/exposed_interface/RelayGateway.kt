package com.zickat.shopifymcpserver.relay.exposed_interface

import arrow.core.Either
import com.zickat.shopifymcpserver.relay.domain.models.RelayToolOutcome
import com.zickat.shopifymcpserver.shared_kernel.AccessRole
import com.zickat.shopifymcpserver.shared_kernel.UseCaseError
import com.zickat.shopifymcpserver.shared_kernel.UseCaseKind
import kotlinx.serialization.json.JsonElement
import org.springframework.modulith.NamedInterface

@NamedInterface("exposed_interface")
interface RelayGateway {
    fun relayedTools(): List<RelayToolDescriptor>

    fun routeFor(toolName: String): ToolRoute?

    fun invoke(
        toolName: String,
        toolInput: JsonElement,
        storeId: String,
        role: AccessRole,
    ): Either<UseCaseError, RelayToolOutcome>
}

data class RelayToolDescriptor(val toolName: String, val kind: UseCaseKind)
