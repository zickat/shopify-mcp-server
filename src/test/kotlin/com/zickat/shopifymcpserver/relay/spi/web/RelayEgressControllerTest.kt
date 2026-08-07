package com.zickat.shopifymcpserver.relay.spi.web

import arrow.core.right
import com.zickat.shopifymcpserver.shared_kernel.Cassette
import com.zickat.shopifymcpserver.shared_kernel.ShopifyAdminGraphQLRequest
import com.zickat.shopifymcpserver.shopify.ShopifyAdminHttpClientFake
import com.zickat.shopifymcpserver.shopify.domain.ShopifyAdminGatewayImpl
import com.zickat.shopifymcpserver.shopify.domain.ShopifyAdminGraphQLUseCase
import com.zickat.shopifymcpserver.vault.VaultExposedServiceFake
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus

class RelayEgressControllerTest {

    private val json = Json { ignoreUnknownKeys = true }

    private fun vaultWithCredential(storeId: String, shopDomain: String): VaultExposedServiceFake =
        VaultExposedServiceFake().apply {
            plaintextByStoreId[storeId] =
                """{"apiKey":"key","apiSecret":"secret","shopDomain":"$shopDomain"}""".toByteArray()
        }

    @Test
    fun `proxies shopify_admin_graphql to the ShopifyAdminGateway and returns the recorded cassette response, bit for bit`() {
        val cassette = Cassette.fromClasspathResource("cassettes/check-shopify-connection.json")
        val recordedCall = cassette.calls.single()
        val recordedRequest = json.decodeFromJsonElement(ShopifyAdminGraphQLRequest.serializer(), recordedCall.request)

        val vault = vaultWithCredential("store-1", "velotrip.myshopify.com")
        val httpClient = ShopifyAdminHttpClientFake().apply {
            nextGraphQLResult = Json.parseToJsonElement("""{"shop":{"name":"Velotrip.fr"}}""").right()
        }
        val gateway = ShopifyAdminGatewayImpl(ShopifyAdminGraphQLUseCase(vault, httpClient))
        val controller = RelayEgressController(gateway)

        val requestBody = json.encodeToString(
            kotlinx.serialization.json.JsonElement.serializer(),
            buildJsonObject {
                put("storeId", "store-1")
                put("query", recordedRequest.query)
                put("variables", recordedRequest.variables)
            },
        )

        val response = controller.shopifyAdminGraphQL(requestBody)

        response.statusCode shouldBe HttpStatus.OK
        json.parseToJsonElement(response.body!!) shouldBe json.parseToJsonElement("""{"status":200,"body":{"shop":{"name":"Velotrip.fr"}}}""")
        httpClient.executeGraphQLCalls.single().first shouldBe "velotrip.myshopify.com"
    }

    @Test
    fun `forwards the exact query and variables it received to the gateway`() {
        val vault = vaultWithCredential("store-1", "velotrip.myshopify.com")
        val httpClient = ShopifyAdminHttpClientFake()
        val gateway = ShopifyAdminGatewayImpl(ShopifyAdminGraphQLUseCase(vault, httpClient))
        val controller = RelayEgressController(gateway)

        val requestBody = """{"storeId":"store-1","query":"query { shop { name } }","variables":{"a":1}}"""

        controller.shopifyAdminGraphQL(requestBody)

        httpClient.executeGraphQLCalls.size shouldBe 1
    }
}
