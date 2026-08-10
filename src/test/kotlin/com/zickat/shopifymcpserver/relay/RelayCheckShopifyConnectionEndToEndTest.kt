package com.zickat.shopifymcpserver.relay

import arrow.core.right
import com.zickat.shopifymcpserver.relay.domain.RelayDispatcher
import com.zickat.shopifymcpserver.relay.domain.RelayManifest
import com.zickat.shopifymcpserver.relay.domain.models.RelayManifestEntry
import com.zickat.shopifymcpserver.relay.exposed_interface.ToolRoute
import com.zickat.shopifymcpserver.relay.spi.http.RelayTsHttpClient
import com.zickat.shopifymcpserver.relay.spi.web.RelayEgressController
import com.zickat.shopifymcpserver.shared_kernel.AccessRole
import com.zickat.shopifymcpserver.shared_kernel.Cassette
import com.zickat.shopifymcpserver.shared_kernel.ShopifyAdminGraphQLRequest
import com.zickat.shopifymcpserver.shared_kernel.UseCaseKind
import com.zickat.shopifymcpserver.shopify.ShopifyAdminHttpClientFake
import com.zickat.shopifymcpserver.shopify.domain.ShopifyAdminGatewayImpl
import com.zickat.shopifymcpserver.shopify.domain.ShopifyAdminGraphQLUseCase
import com.zickat.shopifymcpserver.tenancy.StoreExposedServiceFake
import com.zickat.shopifymcpserver.vault.VaultExposedServiceFake
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.matchers.shouldBe
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.jupiter.api.Test
import org.springframework.web.client.RestClient

class RelayCheckShopifyConnectionEndToEndTest {

    @Serializable
    private data class ContentBlock(val type: String, val text: String)

    @Serializable
    private data class ToolOutput(val content: List<ContentBlock>)

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `Kotlin to TS to the shopify_admin_graphql proxy back to Kotlin to Shopify reproduces the check_shopify_connection cassette output, bit for bit`() {
        val storeSlug = "velotrip"
        val internalStoreId = "507f191e810c19729de860ea"
        val cassette = Cassette.fromClasspathResource("cassettes/check-shopify-connection.json")
        val expectedOutput = json.decodeFromJsonElement(ToolOutput.serializer(), cassette.toolOutput)
        val recordedCall = cassette.calls.single()
        val recordedRequest = json.decodeFromJsonElement(ShopifyAdminGraphQLRequest.serializer(), recordedCall.request)

        val vault = VaultExposedServiceFake().apply {
            plaintextByStoreId[internalStoreId] = """{"apiKey":"key","apiSecret":"secret","shopDomain":"velotrip.myshopify.com"}""".toByteArray()
        }
        val httpClient = ShopifyAdminHttpClientFake().apply {
            nextGraphQLResult = Json.parseToJsonElement("""{"shop":{"name":"Velotrip.fr"}}""").right()
        }
        val storeExposedService = StoreExposedServiceFake().apply { storeIdBySlug[storeSlug] = internalStoreId }
        val egressController = RelayEgressController(ShopifyAdminGatewayImpl(ShopifyAdminGraphQLUseCase(vault, httpClient)), storeExposedService)

        var egressCalled = false
        val tsDouble = MockWebServer().apply {
            dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    val invokeRequest = json.parseToJsonElement(request.body.readUtf8()) as JsonObject
                    val storeIdSeenByTs = (invokeRequest["storeId"] as JsonPrimitive).content

                    val proxyBody = json.encodeToString(
                        JsonElement.serializer(),
                        buildJsonObject {
                            put("storeId", JsonPrimitive(storeIdSeenByTs))
                            put("query", JsonPrimitive(recordedRequest.query))
                            put("variables", recordedRequest.variables)
                        },
                    )
                    val proxyResponse = egressController.shopifyAdminGraphQL(proxyBody)
                    check(proxyResponse.statusCode.is2xxSuccessful) { "egress proxy call failed: ${proxyResponse.body}" }
                    egressCalled = true

                    val toolOutputWire = buildJsonObject {
                        put(
                            "content",
                            JsonArray(
                                expectedOutput.content.map {
                                    buildJsonObject {
                                        put("type", JsonPrimitive("text"))
                                        put("text", JsonPrimitive(it.text))
                                    }
                                },
                            ),
                        )
                        put("isError", JsonPrimitive(false))
                    }
                    return MockResponse()
                        .setResponseCode(200)
                        .setHeader("Content-Type", "application/json")
                        .setBody(json.encodeToString(JsonElement.serializer(), toolOutputWire))
                }
            }
            start()
        }

        try {
            val relayTsClient = RelayTsHttpClient(
                RestClient.builder(),
                RelayProperties(ts = RelayProperties.Ts(baseUrl = tsDouble.url("").toString().trimEnd('/'))),
            )
            val manifest = RelayManifest(listOf(RelayManifestEntry("check_shopify_connection", ToolRoute.RELAIS, UseCaseKind.READ)))
            val dispatcher = RelayDispatcher(manifest, relayTsClient)

            val outcome = dispatcher.callRelayedTool("check_shopify_connection", JsonPrimitive("null"), storeSlug, AccessRole.OPERATOR).shouldBeRight()

            egressCalled shouldBe true
            outcome.isError shouldBe false
            outcome.content.map { it.text } shouldBe expectedOutput.content.map { it.text }
        } finally {
            tsDouble.shutdown()
        }
    }
}
