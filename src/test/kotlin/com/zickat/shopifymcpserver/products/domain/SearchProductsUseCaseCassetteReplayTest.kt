package com.zickat.shopifymcpserver.products.domain

import com.zickat.shopifymcpserver.api.mcp.McpToolResults
import com.zickat.shopifymcpserver.products.exposed_interface.model.ProductStatusFilter
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
 * Exercises `search_products` against a real `SearchProductsUseCase`, replaying the `LOT5-01`
 * cassettes recorded read-only against production under `D19` — same non-tautology discipline as
 * `ListMenusUseCaseCassetteReplayTest`/`GetSeoUseCaseCassetteReplayTest`: every expected text comes
 * straight from the cassette's own `toolOutput`.
 */
class SearchProductsUseCaseCassetteReplayTest {

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

    private fun useCaseReplaying(cassette: Cassette, storeId: String, shopDomain: String): Pair<SearchProductsUseCase, MockWebServer> {
        val mockServer = CassetteMockWebServer.forShopifyAdminGraphQLWithTokenExchange(cassette)
        server = mockServer
        val httpClient = ShopifyRestClientHttpClient(RestClient.builder(), baseUrlFor = { mockServer.url("").toString().removeSuffix("/") })
        val vault = VaultExposedServiceFake().apply {
            plaintextByStoreId[storeId] = """{"apiKey":"key","apiSecret":"secret","shopDomain":"$shopDomain"}""".toByteArray()
        }
        val graphQLUseCase = ShopifyAdminGraphQLUseCase(vault, httpClient)
        val gateway = ShopifyAdminGatewayImpl(graphQLUseCase)
        return SearchProductsUseCase(gateway) to mockServer
    }

    private fun replayAndAssert(
        cassetteResource: String,
        storeId: String,
        shopDomain: String,
        storeSlug: String,
        query: String?,
        statusFilter: ProductStatusFilter,
    ) {
        // given
        val cassette = Cassette.fromClasspathResource(cassetteResource)
        val (useCase, mockServer) = useCaseReplaying(cassette, storeId, shopDomain)

        // when
        val result = useCase.execute(storeId, query, statusFilter).shouldBeRight()

        // then
        mockServer.takeRequest()
        cassette.calls.forEach { call ->
            CassetteEquivalence.assertShopifyAdminGraphQLRequestMatches(call, mockServer.takeRequest())
        }

        val rendered = McpToolResults.searchProductsResult(storeSlug, result)
        rendered.content().map { (it as TextContent).text() } shouldBe expectedTexts(cassette)
    }

    @Test
    fun `replaying the velotrip all-no-query cassette reproduces the recorded toolOutput bit for bit`() {
        replayAndAssert(
            "cassettes/search-products-velotrip-all-no-query.json",
            "store-velotrip",
            "velotrip.myshopify.com",
            "velotrip",
            query = null,
            statusFilter = ProductStatusFilter.ALL,
        )
    }

    @Test
    fun `replaying the velotrip untreated-no-query cassette reproduces the recorded toolOutput bit for bit`() {
        replayAndAssert(
            "cassettes/search-products-velotrip-untreated-no-query.json",
            "store-velotrip",
            "velotrip.myshopify.com",
            "velotrip",
            query = null,
            statusFilter = ProductStatusFilter.UNTREATED,
        )
    }

    @Test
    fun `replaying the velotrip to_review-no-query cassette reproduces the recorded toolOutput bit for bit`() {
        replayAndAssert(
            "cassettes/search-products-velotrip-to-review-no-query.json",
            "store-velotrip",
            "velotrip.myshopify.com",
            "velotrip",
            query = null,
            statusFilter = ProductStatusFilter.TO_REVIEW,
        )
    }

    @Test
    fun `replaying the velotrip blocked-no-query cassette reproduces the recorded toolOutput bit for bit`() {
        replayAndAssert(
            "cassettes/search-products-velotrip-blocked-no-query.json",
            "store-velotrip",
            "velotrip.myshopify.com",
            "velotrip",
            query = null,
            statusFilter = ProductStatusFilter.BLOCKED,
        )
    }

    @Test
    fun `replaying the velotrip all-query-sacoche cassette reproduces the recorded toolOutput bit for bit`() {
        replayAndAssert(
            "cassettes/search-products-velotrip-all-query-sacoche.json",
            "store-velotrip",
            "velotrip.myshopify.com",
            "velotrip",
            query = "sacoche",
            statusFilter = ProductStatusFilter.ALL,
        )
    }

    @Test
    fun `replaying the lurelab untreated-no-query cassette reproduces the recorded toolOutput bit for bit`() {
        replayAndAssert(
            "cassettes/search-products-lurelab-untreated-no-query.json",
            "store-lurelab",
            "lurelab.myshopify.com",
            "lurelab",
            query = null,
            statusFilter = ProductStatusFilter.UNTREATED,
        )
    }

    @Test
    fun `replaying the lurelab to_review-no-query cassette reproduces the recorded toolOutput bit for bit`() {
        replayAndAssert(
            "cassettes/search-products-lurelab-to-review-no-query.json",
            "store-lurelab",
            "lurelab.myshopify.com",
            "lurelab",
            query = null,
            statusFilter = ProductStatusFilter.TO_REVIEW,
        )
    }

    @Test
    fun `replaying the lurelab all-no-query cassette reproduces the recorded toolOutput bit for bit`() {
        replayAndAssert(
            "cassettes/search-products-lurelab-all-no-query.json",
            "store-lurelab",
            "lurelab.myshopify.com",
            "lurelab",
            query = null,
            statusFilter = ProductStatusFilter.ALL,
        )
    }

    @Test
    fun `replaying the lurelab untreated-query-noeby cassette reproduces the recorded toolOutput bit for bit`() {
        replayAndAssert(
            "cassettes/search-products-lurelab-untreated-query-noeby.json",
            "store-lurelab",
            "lurelab.myshopify.com",
            "lurelab",
            query = "NOEBY",
            statusFilter = ProductStatusFilter.UNTREATED,
        )
    }
}
