package com.zickat.shopifymcpserver.relay.domain

import arrow.core.Either
import arrow.core.left
import com.zickat.shopifymcpserver.relay.domain.models.RelayToolOutcome
import com.zickat.shopifymcpserver.relay.domain.models.ToolRoute
import com.zickat.shopifymcpserver.relay.domain.repositories.RelayToolInvocationRequest
import com.zickat.shopifymcpserver.relay.domain.repositories.RelayTsClient
import com.zickat.shopifymcpserver.shared_kernel.AccessRole
import com.zickat.shopifymcpserver.shared_kernel.TechnicalError
import com.zickat.shopifymcpserver.shared_kernel.UseCaseError
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock
import kotlinx.serialization.json.JsonElement
import org.springframework.stereotype.Component

@Component
class RelayDispatcher(
    private val manifest: RelayManifest,
    private val tsClient: RelayTsClient,
) {

    private val entryLock = ReentrantLock()

    fun callRelayedTool(
        toolName: String,
        toolInput: JsonElement,
        storeId: String,
        role: AccessRole,
    ): Either<UseCaseError, RelayToolOutcome> {
        val entry = manifest.entryFor(toolName)
        if (entry == null || entry.route != ToolRoute.RELAIS) {
            return TechnicalError("relay.tool.unmanifested", mapOf("toolName" to toolName)).left()
        }

        return entryLock.withLock {
            tsClient.invoke(RelayToolInvocationRequest(toolName, toolInput, storeId, role.name))
        }
    }
}
