package com.zickat.shopifymcpserver.shared_kernel

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer

object CassetteMockWebServer {

    private val json = Json { ignoreUnknownKeys = true }
    private val client = OkHttpClient()
    private val jsonMediaType = "application/json".toMediaType()

    fun forShopifyAdminGraphQL(cassette: Cassette): MockWebServer {
        val server = MockWebServer()
        cassette.calls
            .filter { it.sink == SINK_SHOPIFY_ADMIN_GRAPHQL }
            .forEach { server.enqueue(graphQLEnvelopeResponseOf(it)) }
        server.start()
        return server
    }

    /**
     * Same as [forShopifyAdminGraphQL], but for tests that exercise the real use case chain
     * (`ShopifyAdminGraphQLUseCase` → vault → token exchange → GraphQL call) instead of posting
     * directly to the mock server. That chain fetches an access token before its first GraphQL
     * call, which the cassette itself never recorded — LOT1-01's capture point sits after the
     * token is already resolved. A synthetic token-exchange response is enqueued first so the
     * cassette's own responses land on the GraphQL calls they were recorded for, not on the token
     * exchange.
     */
    fun forShopifyAdminGraphQLWithTokenExchange(cassette: Cassette, accessToken: String = "test-access-token"): MockWebServer =
        forShopifyAdminGraphQLWithTokenExchange(cassette.calls, accessToken)

    fun forShopifyAdminGraphQLWithTokenExchange(calls: List<CassetteCall>, accessToken: String = "test-access-token"): MockWebServer {
        val server = MockWebServer()
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("""{"access_token":"$accessToken"}"""),
        )
        calls
            .filter { it.sink == SINK_SHOPIFY_ADMIN_GRAPHQL }
            .forEach { server.enqueue(graphQLEnvelopeResponseOf(it)) }
        server.start()
        return server
    }

    fun postGraphQL(server: MockWebServer, query: String, variables: JsonElement): Response {
        val body = buildJsonObject {
            put("query", JsonPrimitive(query))
            put("variables", variables)
        }
        val requestBody = json.encodeToString(JsonElement.serializer(), body).toRequestBody(jsonMediaType)
        return client.newCall(
            Request.Builder().url(server.url("/admin/api/2025-01/graphql.json")).post(requestBody).build(),
        ).execute()
    }

    private fun graphQLEnvelopeResponseOf(call: CassetteCall): MockResponse {
        val response = json.decodeFromJsonElement(ShopifyAdminGraphQLResponse.serializer(), call.response)
        val envelope: JsonElement = JsonObject(mapOf("data" to response.body))
        return MockResponse()
            .setResponseCode(response.status)
            .setHeader("Content-Type", "application/json")
            .setBody(json.encodeToString(JsonElement.serializer(), envelope))
    }
}
