package com.zickat.shopifymcpserver.shopify

import arrow.core.Either
import arrow.core.right
import com.zickat.shopifymcpserver.shared_kernel.UseCaseError
import com.zickat.shopifymcpserver.shopify.exposed_interface.ShopifyAdminGateway
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive

class ShopifyAdminGatewayFake : ShopifyAdminGateway {

    data class RecordedCall(val storeId: String, val query: String, val variables: JsonElement)

    val calls = mutableListOf<RecordedCall>()
    private val queuedResponses = ArrayDeque<Either<UseCaseError, JsonElement>>()
    var nextResponseProvider: ((RecordedCall) -> Either<UseCaseError, JsonElement>)? = null
    var default: Either<UseCaseError, JsonElement> = JsonPrimitive("ok").right()

    fun enqueue(response: Either<UseCaseError, JsonElement>) {
        queuedResponses.addLast(response)
    }

    override fun executeGraphQL(storeId: String, query: String, variables: JsonElement): Either<UseCaseError, JsonElement> {
        val call = RecordedCall(storeId, query, variables)
        calls.add(call)
        return nextResponseProvider?.invoke(call)
            ?: if (queuedResponses.isNotEmpty()) queuedResponses.removeFirst() else default
    }
}
