package com.zickat.shopifymcpserver.shopify.spi.http

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import com.zickat.shopifymcpserver.shared_kernel.DomainError
import com.zickat.shopifymcpserver.shared_kernel.TechnicalError
import com.zickat.shopifymcpserver.shared_kernel.UseCaseError
import com.zickat.shopifymcpserver.shopify.domain.models.ShopifyAccessToken
import com.zickat.shopifymcpserver.shopify.domain.repositories.ShopifyAdminHttpClient
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientException

@Component
class ShopifyRestClientHttpClient(
    restClientBuilder: RestClient.Builder,
    private val baseUrlFor: (String) -> String = { shopDomain -> "https://$shopDomain" },
) : ShopifyAdminHttpClient {

    private val restClient = restClientBuilder.build()
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    override fun exchangeAccessToken(shopDomain: String, apiKey: String, apiSecret: String): Either<UseCaseError, ShopifyAccessToken> {
        val requestBody = json.encodeToString(TokenExchangeRequest.serializer(), TokenExchangeRequest(apiKey, apiSecret))

        val responseBody: String? = try {
            restClient.post()
                .uri("${baseUrlFor(shopDomain)}/admin/oauth/access_token")
                .contentType(MediaType.APPLICATION_JSON)
                .body(requestBody.toByteArray(Charsets.UTF_8))
                .retrieve()
                .onStatus({ status -> status.isError }, { _, response -> throw ShopifyHttpStatusException(response.statusCode.value()) })
                .body(String::class.java)
        } catch (e: ShopifyHttpStatusException) {
            return TechnicalError("shopify.auth.rejected", mapOf("status" to e.status.toString())).left()
        } catch (e: RestClientException) {
            return TechnicalError("shopify.auth.network.failed").left()
        }

        val payload = try {
            json.decodeFromString(TokenExchangeResponse.serializer(), responseBody.orEmpty())
        } catch (e: SerializationException) {
            return TechnicalError("shopify.auth.response.malformed").left()
        }

        return payload.accessToken?.let { ShopifyAccessToken(it).right() }
            ?: TechnicalError("shopify.auth.token.missing").left()
    }

    override fun executeGraphQL(shopDomain: String, accessToken: String, query: String, variables: JsonElement): Either<UseCaseError, JsonElement> {
        val requestBody = json.encodeToString(
            JsonElement.serializer(),
            buildJsonObject {
                put("query", JsonPrimitive(query))
                put("variables", variables)
            },
        )

        val responseBody: String? = try {
            restClient.post()
                .uri("${baseUrlFor(shopDomain)}/admin/api/$ADMIN_API_VERSION/graphql.json")
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-Shopify-Access-Token", accessToken)
                .body(requestBody.toByteArray(Charsets.UTF_8))
                .retrieve()
                .onStatus({ status -> status.isError }, { _, response -> throw ShopifyHttpStatusException(response.statusCode.value()) })
                .body(String::class.java)
        } catch (e: ShopifyHttpStatusException) {
            return TechnicalError("shopify.graphql.http.rejected", mapOf("status" to e.status.toString())).left()
        } catch (e: RestClientException) {
            return TechnicalError("shopify.graphql.network.failed").left()
        }

        val envelope = try {
            json.decodeFromString(ShopifyGraphQLEnvelope.serializer(), responseBody.orEmpty())
        } catch (e: SerializationException) {
            return TechnicalError("shopify.graphql.response.malformed").left()
        }

        if (!envelope.errors.isNullOrEmpty()) {
            val detail = envelope.errors.joinToString("; ") { error -> error.message ?: "unknown error" }
            return DomainError("shopify.graphql.refused", mapOf("detail" to detail)).left()
        }

        return envelope.data?.right() ?: TechnicalError("shopify.graphql.response.empty").left()
    }

    companion object {
        private const val ADMIN_API_VERSION = "2025-01"
    }
}

private class ShopifyHttpStatusException(val status: Int) : RuntimeException()

@Serializable
private data class TokenExchangeRequest(
    @SerialName("client_id") val clientId: String,
    @SerialName("client_secret") val clientSecret: String,
    @SerialName("grant_type") val grantType: String = "client_credentials",
)

@Serializable
private data class TokenExchangeResponse(@SerialName("access_token") val accessToken: String? = null)

@Serializable
private data class ShopifyGraphQLEnvelope(
    val data: JsonElement? = null,
    val errors: List<ShopifyGraphQLErrorMessage>? = null,
)

@Serializable
private data class ShopifyGraphQLErrorMessage(val message: String? = null)
