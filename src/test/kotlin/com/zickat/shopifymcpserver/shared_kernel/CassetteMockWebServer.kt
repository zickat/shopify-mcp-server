package com.zickat.shopifymcpserver.shared_kernel

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer

object CassetteMockWebServer {

    private val json = Json { ignoreUnknownKeys = true }

    fun forShopifyAdminGraphQL(cassette: Cassette): MockWebServer {
        val server = MockWebServer()
        cassette.calls
            .filter { it.sink == SINK_SHOPIFY_ADMIN_GRAPHQL }
            .forEach { server.enqueue(graphQLEnvelopeResponseOf(it)) }
        server.start()
        return server
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
