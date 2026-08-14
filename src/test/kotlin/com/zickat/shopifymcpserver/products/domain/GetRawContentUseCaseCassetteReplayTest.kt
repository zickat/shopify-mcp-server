package com.zickat.shopifymcpserver.products.domain

import com.zickat.shopifymcpserver.products.api.mcp.ProductsToolResults
import com.zickat.shopifymcpserver.products.spi.shopify.ProductShopifyRepository
import com.zickat.shopifymcpserver.shared_kernel.Cassette
import com.zickat.shopifymcpserver.shared_kernel.CassetteEquivalence
import com.zickat.shopifymcpserver.shared_kernel.CassetteMockWebServer
import com.zickat.shopifymcpserver.shopify.domain.ShopifyAdminGatewayImpl
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
 * Exercises `get_raw_content` against a real `GetRawContentUseCase`, replaying the `LOT5-01`
 * cassettes — including the two-call sequence (product query then media query) that this tool
 * fires on a found product, and the single-call short-circuit on a not-found one.
 */
class GetRawContentUseCaseCassetteReplayTest {

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

    private fun useCaseReplaying(cassette: Cassette, storeId: String, shopDomain: String): Pair<GetRawContentUseCase, MockWebServer> {
        val mockServer = CassetteMockWebServer.forShopifyAdminGraphQLWithTokenExchange(cassette)
        server = mockServer
        val httpClient = ShopifyRestClientHttpClient(RestClient.builder(), baseUrlFor = { mockServer.url("").toString().removeSuffix("/") })
        val vault = VaultExposedServiceFake().apply {
            plaintextByStoreId[storeId] = """{"apiKey":"key","apiSecret":"secret","shopDomain":"$shopDomain"}""".toByteArray()
        }
        val graphQLUseCase = ShopifyAdminGraphQLUseCase(vault, httpClient)
        val gateway = ShopifyAdminGatewayImpl(graphQLUseCase)
        return GetRawContentUseCase(ProductShopifyRepository(gateway)) to mockServer
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

        val rendered = ProductsToolResults.getRawContentResult(storeSlug, result)
        rendered.content().map { (it as TextContent).text() } shouldBe expectedTexts(cassette)
    }

    @Test
    fun `replaying the velotrip variants-and-options cassette reproduces the recorded toolOutput bit for bit`() {
        replayAndAssert(
            "cassettes/get-raw-content-velotrip-variants-options.json",
            "store-velotrip",
            "velotrip.myshopify.com",
            "velotrip",
            "gid://shopify/Product/15996117647685",
        )
    }

    @Test
    fun `replaying the not-found cassette reproduces the recorded toolOutput bit for bit, with no second (media) call`() {
        replayAndAssert(
            "cassettes/get-raw-content-not-found.json",
            "store-velotrip",
            "velotrip.myshopify.com",
            "velotrip",
            "gid://shopify/Product/99999999999999",
        )
    }
}
