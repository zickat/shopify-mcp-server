package com.zickat.shopifymcpserver.shopify

import arrow.core.Either
import arrow.core.right
import com.zickat.shopifymcpserver.shared_kernel.UseCaseError
import com.zickat.shopifymcpserver.shopify.domain.models.ShopifyAccessToken
import com.zickat.shopifymcpserver.shopify.domain.repositories.ShopifyAdminHttpClient
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive

class ShopifyAdminHttpClientFake(
    private val exchangeAccessTokenDelayMillis: Long = 0,
) : ShopifyAdminHttpClient {

    private val exchangeCount = AtomicInteger(0)
    val exchangeAccessTokenCalls = mutableListOf<Triple<String, String, String>>()
    val executeGraphQLCalls = mutableListOf<Pair<String, String>>()

    var nextAccessToken: (String) -> Either<UseCaseError, ShopifyAccessToken> = { domain ->
        ShopifyAccessToken("token-for-$domain-call-${exchangeCount.get() + 1}").right()
    }
    var nextGraphQLResult: Either<UseCaseError, JsonElement> = JsonPrimitive("ok").right()

    val exchangeAccessTokenCallCount: Int get() = exchangeCount.get()

    override fun exchangeAccessToken(shopDomain: String, apiKey: String, apiSecret: String): Either<UseCaseError, ShopifyAccessToken> {
        exchangeCount.incrementAndGet()
        synchronized(exchangeAccessTokenCalls) { exchangeAccessTokenCalls.add(Triple(shopDomain, apiKey, apiSecret)) }
        if (exchangeAccessTokenDelayMillis > 0) Thread.sleep(exchangeAccessTokenDelayMillis)
        return nextAccessToken(shopDomain)
    }

    override fun executeGraphQL(shopDomain: String, accessToken: String, query: String, variables: JsonElement): Either<UseCaseError, JsonElement> {
        synchronized(executeGraphQLCalls) { executeGraphQLCalls.add(shopDomain to accessToken) }
        return nextGraphQLResult
    }
}
