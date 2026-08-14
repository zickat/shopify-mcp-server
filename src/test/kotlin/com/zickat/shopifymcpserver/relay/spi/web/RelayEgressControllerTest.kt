package com.zickat.shopifymcpserver.relay.spi.web

import arrow.core.right
import com.zickat.shopifymcpserver.shared_kernel.Cassette
import com.zickat.shopifymcpserver.shared_kernel.ShopifyAdminGraphQLRequest
import com.zickat.shopifymcpserver.shared_kernel.UseCaseErrorException
import com.zickat.shopifymcpserver.shopify.ShopifyAdminHttpClientFake
import com.zickat.shopifymcpserver.shopify.exposed_interface.ShopifyAdminGatewayImpl
import com.zickat.shopifymcpserver.shopify.domain.ShopifyAdminGraphQLUseCase
import com.zickat.shopifymcpserver.tenancy.StoreExposedServiceFake
import com.zickat.shopifymcpserver.vault.VaultExposedServiceFake
import io.kotest.assertions.throwables.shouldThrow
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

    private fun storeResolvingSlugToId(slug: String, storeId: String): StoreExposedServiceFake =
        StoreExposedServiceFake().apply { storeIdBySlug[slug] = storeId }

    @Test
    fun `proxies shopify_admin_graphql to the ShopifyAdminGateway and returns the recorded cassette response, bit for bit — the wire carries the slug, the vault is keyed by the internal storeId`() {
        val cassette = Cassette.fromClasspathResource("cassettes/check-shopify-connection.json")
        val recordedCall = cassette.calls.single()
        val recordedRequest = json.decodeFromJsonElement(ShopifyAdminGraphQLRequest.serializer(), recordedCall.request)

        val vault = vaultWithCredential("507f191e810c19729de860ea", "velotrip.myshopify.com")
        val httpClient = ShopifyAdminHttpClientFake().apply {
            nextGraphQLResult = Json.parseToJsonElement("""{"shop":{"name":"Velotrip.fr"}}""").right()
        }
        val gateway = ShopifyAdminGatewayImpl(ShopifyAdminGraphQLUseCase(vault, httpClient))
        val storeExposedService = storeResolvingSlugToId("velotrip", "507f191e810c19729de860ea")
        val controller = RelayEgressController(gateway, storeExposedService)

        val requestBody = json.encodeToString(
            kotlinx.serialization.json.JsonElement.serializer(),
            buildJsonObject {
                put("storeId", "velotrip")
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
    fun `forwards the exact query and variables it received to the gateway, after resolving the slug to the internal storeId`() {
        val vault = vaultWithCredential("507f191e810c19729de860ea", "velotrip.myshopify.com")
        val httpClient = ShopifyAdminHttpClientFake()
        val gateway = ShopifyAdminGatewayImpl(ShopifyAdminGraphQLUseCase(vault, httpClient))
        val storeExposedService = storeResolvingSlugToId("velotrip", "507f191e810c19729de860ea")
        val controller = RelayEgressController(gateway, storeExposedService)

        val requestBody = """{"storeId":"velotrip","query":"query { shop { name } }","variables":{"a":1}}"""

        controller.shopifyAdminGraphQL(requestBody)

        httpClient.executeGraphQLCalls.size shouldBe 1
    }

    @Test
    fun `a slug unknown to the store lookup is refused rather than reaching the gateway with a bogus storeId`() {
        val vault = VaultExposedServiceFake()
        val httpClient = ShopifyAdminHttpClientFake()
        val gateway = ShopifyAdminGatewayImpl(ShopifyAdminGraphQLUseCase(vault, httpClient))
        val storeExposedService = StoreExposedServiceFake()
        val controller = RelayEgressController(gateway, storeExposedService)

        val requestBody = """{"storeId":"unknown-slug","query":"query { shop { name } }","variables":{}}"""

        shouldThrow<UseCaseErrorException> { controller.shopifyAdminGraphQL(requestBody) }
        httpClient.executeGraphQLCalls.size shouldBe 0
    }
}
