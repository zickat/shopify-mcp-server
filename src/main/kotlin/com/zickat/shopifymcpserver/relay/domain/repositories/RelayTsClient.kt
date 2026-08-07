package com.zickat.shopifymcpserver.relay.domain.repositories

import arrow.core.Either
import com.zickat.shopifymcpserver.relay.domain.models.RelayToolOutcome
import com.zickat.shopifymcpserver.shared_kernel.UseCaseError
import kotlinx.serialization.json.JsonElement

interface RelayTsClient {
    fun invoke(request: RelayToolInvocationRequest): Either<UseCaseError, RelayToolOutcome>
}

data class RelayToolInvocationRequest(
    val toolName: String,
    val toolInput: JsonElement,
    val storeId: String,
    val role: String,
)
