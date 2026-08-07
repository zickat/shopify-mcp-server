package com.zickat.shopifymcpserver.shopify.domain.repositories

import arrow.core.Either
import com.zickat.shopifymcpserver.shared_kernel.UseCaseError
import com.zickat.shopifymcpserver.shopify.domain.models.ShopifyAccessToken
import kotlinx.serialization.json.JsonElement

interface ShopifyAdminHttpClient {
    fun exchangeAccessToken(shopDomain: String, apiKey: String, apiSecret: String): Either<UseCaseError, ShopifyAccessToken>

    fun executeGraphQL(shopDomain: String, accessToken: String, query: String, variables: JsonElement): Either<UseCaseError, JsonElement>
}
