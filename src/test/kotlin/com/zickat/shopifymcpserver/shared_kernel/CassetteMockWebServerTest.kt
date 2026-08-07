package com.zickat.shopifymcpserver.shared_kernel

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.Test

class CassetteMockWebServerTest {

    private val client = OkHttpClient()
    private val jsonMediaType = "application/json".toMediaType()

    private val threeCallCassetteJson = """
        {
          "tool": "sync_products",
          "toolInput": { "collectionId": "gid://shopify/Collection/1" },
          "toolOutput": { "content": [] },
          "calls": [
            {
              "sink": "shopify_admin_graphql",
              "request": { "query": "query CheckConnection { shop { name } }", "variables": {} },
              "response": { "status": 200, "body": { "shop": { "name": "Velotrip.fr" } } }
            },
            {
              "sink": "shopify_admin_graphql",
              "request": { "query": "query GetProduct(${'$'}id: ID!) { product(id: ${'$'}id) { title } }", "variables": { "id": "gid://shopify/Product/1" } },
              "response": { "status": 200, "body": { "product": { "title": "Velo gravel" } } }
            },
            {
              "sink": "shopify_admin_graphql",
              "request": { "query": "mutation UpdateProduct(${'$'}input: ProductInput!) { productUpdate(input: ${'$'}input) { product { id } } }", "variables": { "input": { "id": "gid://shopify/Product/1", "title": "Velo gravel 2.0" } } },
              "response": { "status": 200, "body": { "productUpdate": { "product": { "id": "gid://shopify/Product/1" } } } }
            }
          ]
        }
    """.trimIndent()

    private fun postGraphQL(server: MockWebServer, query: String, variables: JsonElement): Response {
        val body = buildJsonObject {
            put("query", JsonPrimitive(query))
            put("variables", variables)
        }
        val requestBody = Json.encodeToString(JsonElement.serializer(), body).toRequestBody(jsonMediaType)
        return client.newCall(
            Request.Builder().url(server.url("/admin/api/2025-01/graphql.json")).post(requestBody).build(),
        ).execute()
    }

    @Test
    fun `forShopifyAdminGraphQL replays cassette responses over real HTTP requests, in cassette order`() {
        // given
        val cassette = Cassette.fromJson(threeCallCassetteJson)
        val server = CassetteMockWebServer.forShopifyAdminGraphQL(cassette)

        // when
        val responses = cassette.calls.map { call ->
            val request = Json.decodeFromJsonElement(ShopifyAdminGraphQLRequest.serializer(), call.request)
            postGraphQL(server, request.query, request.variables)
        }

        // then
        responses.zip(cassette.calls).forEach { (response, call) ->
            val expected = Json.decodeFromJsonElement(ShopifyAdminGraphQLResponse.serializer(), call.response)
            response.code shouldBe expected.status
            val actualEnvelope = Json.parseToJsonElement(response.body!!.string())
            actualEnvelope shouldBe buildJsonObject { put("data", expected.body) }
        }
        server.shutdown()
    }

    @Test
    fun `Cassette fromClasspathResource loads a cassette committed under src slash test slash resources slash cassettes`() {
        // when
        val cassette = Cassette.fromClasspathResource("cassettes/demo-check-shopify-connection.json")

        // then
        cassette.tool shouldBe "check_shopify_connection"
        cassette.calls shouldHaveSize 2
        cassette.calls.map { it.sink } shouldBe listOf(SINK_SHOPIFY_ADMIN_GRAPHQL, SINK_SHOPIFY_ADMIN_GRAPHQL)
    }

    @Test
    fun `assertShopifyAdminGraphQLRequestMatches passes when the recorded request equals the cassette request`() {
        // given
        val cassetteCall = CassetteCall(
            sink = SINK_SHOPIFY_ADMIN_GRAPHQL,
            request = Json.parseToJsonElement(
                """{"query":"query CheckConnection { shop { name } }","variables":{}}""",
            ),
            response = Json.parseToJsonElement("""{"status":200,"body":{"shop":{"name":"Velotrip.fr"}}}"""),
        )
        val server = MockWebServer().apply {
            enqueue(MockResponse().setResponseCode(200).setBody("{}"))
            start()
        }

        // when
        postGraphQL(server, "query CheckConnection { shop { name } }", buildJsonObject {})
        val recordedRequest = server.takeRequest()

        // then
        CassetteEquivalence.assertShopifyAdminGraphQLRequestMatches(cassetteCall, recordedRequest)
        server.shutdown()
    }

    @Test
    fun `assertShopifyAdminGraphQLRequestMatches fails with a message showing both the cassette request and the mismatched request`() {
        // given
        val cassetteCall = CassetteCall(
            sink = SINK_SHOPIFY_ADMIN_GRAPHQL,
            request = Json.parseToJsonElement(
                """{"query":"query GetProduct(${'$'}id: ID!) { product(id: ${'$'}id) { title } }","variables":{"id":"gid://shopify/Product/1"}}""",
            ),
            response = Json.parseToJsonElement("""{"status":200,"body":{}}"""),
        )
        val server = MockWebServer().apply {
            enqueue(MockResponse().setResponseCode(200).setBody("{}"))
            start()
        }

        // when
        postGraphQL(
            server,
            "query GetProduct(${'$'}id: ID!) { product(id: ${'$'}id) { title description } }",
            buildJsonObject { put("id", JsonPrimitive("gid://shopify/Product/999")) },
        )
        val recordedRequest = server.takeRequest()
        val failure = shouldThrow<AssertionError> {
            CassetteEquivalence.assertShopifyAdminGraphQLRequestMatches(cassetteCall, recordedRequest)
        }

        // then
        failure.message shouldContain "gid://shopify/Product/1\""
        failure.message shouldContain "gid://shopify/Product/999"
        server.shutdown()
    }
}
