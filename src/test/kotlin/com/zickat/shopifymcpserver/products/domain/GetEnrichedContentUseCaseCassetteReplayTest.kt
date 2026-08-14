package com.zickat.shopifymcpserver.products.domain

import com.zickat.shopifymcpserver.products.api.mcp.ProductsToolResults
import com.zickat.shopifymcpserver.products.spi.shopify.ProductShopifyRepository
import com.zickat.shopifymcpserver.shared_kernel.Cassette
import com.zickat.shopifymcpserver.shared_kernel.CassetteEquivalence
import com.zickat.shopifymcpserver.shared_kernel.CassetteMockWebServer
import com.zickat.shopifymcpserver.shopify.exposed_interface.ShopifyAdminGatewayImpl
import com.zickat.shopifymcpserver.shopify.domain.ShopifyAdminGraphQLUseCase
import com.zickat.shopifymcpserver.shopify.spi.http.ShopifyRestClientHttpClient
import com.zickat.shopifymcpserver.vault.VaultExposedServiceFake
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.matchers.shouldBe
import io.modelcontextprotocol.spec.McpSchema.TextContent
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.web.client.RestClient

/**
 * Exercises `get_enriched_content` against a real `GetEnrichedContentUseCase`, replaying the
 * `LOT5-01` cassettes — including the "auto" related-guides projection (Vélotrip) and a product
 * never touched by `enrich_product` (LureLab, every custom.* field absent).
 */
class GetEnrichedContentUseCaseCassetteReplayTest {

    @Serializable
    private data class ContentBlock(val type: String, val text: String)

    @Serializable
    private data class ToolOutput(val content: List<ContentBlock>)

    private val json = Json { ignoreUnknownKeys = true }

    private var server: MockWebServer? = null

    @AfterEach
    fun shutdownServer() {
        server?.shutdown()
    }

    private fun expectedTexts(cassette: Cassette): List<String> =
        json.decodeFromJsonElement(ToolOutput.serializer(), cassette.toolOutput).content.map { it.text }

    private fun useCaseReplaying(cassette: Cassette, storeId: String, shopDomain: String): Pair<GetEnrichedContentUseCase, MockWebServer> {
        val mockServer = CassetteMockWebServer.forShopifyAdminGraphQLWithTokenExchange(cassette)
        server = mockServer
        val httpClient = ShopifyRestClientHttpClient(RestClient.builder(), baseUrlFor = { mockServer.url("").toString().removeSuffix("/") })
        val vault = VaultExposedServiceFake().apply {
            plaintextByStoreId[storeId] = """{"apiKey":"key","apiSecret":"secret","shopDomain":"$shopDomain"}""".toByteArray()
        }
        val graphQLUseCase = ShopifyAdminGraphQLUseCase(vault, httpClient)
        val gateway = ShopifyAdminGatewayImpl(graphQLUseCase)
        return GetEnrichedContentUseCase(ProductShopifyRepository(gateway)) to mockServer
    }

    private fun replayAndAssert(cassetteResource: String, storeId: String, shopDomain: String, storeSlug: String, productId: String) {
        // given
        val cassette = Cassette.fromClasspathResource(cassetteResource)
        val (useCase, mockServer) = useCaseReplaying(cassette, storeId, shopDomain)

        // when
        val result = useCase.execute(storeId, productId).shouldBeRight()

        // then
        mockServer.takeRequest()
        cassette.calls.forEach { call ->
            CassetteEquivalence.assertShopifyAdminGraphQLRequestMatches(call, mockServer.takeRequest())
        }

        val rendered = ProductsToolResults.getEnrichedContentResult(storeSlug, result)
        rendered.content().map { (it as TextContent).text() } shouldBe expectedTexts(cassette)
    }

    @Test
    fun `replaying the velotrip auto related-guides projection cassette reproduces the recorded toolOutput bit for bit`() {
        replayAndAssert(
            "cassettes/get-enriched-content-velotrip-auto.json",
            "store-velotrip",
            "velotrip.myshopify.com",
            "velotrip",
            "gid://shopify/Product/15991706353989",
        )
    }

    @Test
    fun `replaying the lurelab never-enriched-by-enrich_product cassette reproduces the recorded toolOutput bit for bit`() {
        replayAndAssert(
            "cassettes/get-enriched-content-lurelab-never-enriched.json",
            "store-lurelab",
            "lurelab.myshopify.com",
            "lurelab",
            "gid://shopify/Product/15490878505345",
        )
    }

    @Test
    fun `replaying the not-found cassette reproduces the recorded toolOutput bit for bit`() {
        replayAndAssert(
            "cassettes/get-enriched-content-not-found.json",
            "store-lurelab",
            "lurelab.myshopify.com",
            "lurelab",
            "gid://shopify/Product/99999999999999",
        )
    }
}
