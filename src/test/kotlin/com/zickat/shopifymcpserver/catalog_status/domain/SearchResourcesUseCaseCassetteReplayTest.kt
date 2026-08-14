package com.zickat.shopifymcpserver.catalog_status.domain

import com.zickat.shopifymcpserver.catalog_status.api.mcp.CatalogStatusToolResults
import com.zickat.shopifymcpserver.catalog_status.domain.models.SearchResourceType
import com.zickat.shopifymcpserver.catalog_status.domain.models.SearchStatusFilter
import com.zickat.shopifymcpserver.catalog_status.spi.shopify.CatalogStatusShopifyRepository
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

class SearchResourcesUseCaseCassetteReplayTest {

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

    private fun useCaseReplaying(cassette: Cassette, storeId: String, shopDomain: String): Pair<SearchResourcesUseCase, MockWebServer> {
        val mockServer = CassetteMockWebServer.forShopifyAdminGraphQLWithTokenExchange(cassette)
        server = mockServer
        val httpClient = ShopifyRestClientHttpClient(RestClient.builder(), baseUrlFor = { mockServer.url("").toString().removeSuffix("/") })
        val vault = VaultExposedServiceFake().apply {
            plaintextByStoreId[storeId] = """{"apiKey":"key","apiSecret":"secret","shopDomain":"$shopDomain"}""".toByteArray()
        }
        val graphQLUseCase = ShopifyAdminGraphQLUseCase(vault, httpClient)
        val gateway = ShopifyAdminGatewayImpl(graphQLUseCase)
        return SearchResourcesUseCase(CatalogStatusShopifyRepository(gateway)) to mockServer
    }

    private fun replayAndAssert(
        cassetteResource: String,
        storeId: String,
        shopDomain: String,
        storeSlug: String,
        resourceType: SearchResourceType,
        statusFilter: SearchStatusFilter,
    ) {
        // given
        val cassette = Cassette.fromClasspathResource(cassetteResource)
        val (useCase, mockServer) = useCaseReplaying(cassette, storeId, shopDomain)

        // when
        val result = useCase.execute(storeId, resourceType, null, statusFilter).shouldBeRight()

        // then
        mockServer.takeRequest()
        cassette.calls.forEach { call ->
            CassetteEquivalence.assertShopifyAdminGraphQLRequestMatches(call, mockServer.takeRequest())
        }

        val rendered = CatalogStatusToolResults.searchResourcesResult(storeSlug, result)
        rendered.content().map { (it as TextContent).text() } shouldBe expectedTexts(cassette)
    }

    @Test
    fun `replaying the velotrip collection untreated cassette reproduces the recorded toolOutput bit for bit`() {
        replayAndAssert(
            "cassettes/search-resources-velotrip-collection-untreated.json",
            "store-velotrip",
            "velotrip.myshopify.com",
            "velotrip",
            SearchResourceType.COLLECTION,
            SearchStatusFilter.UNTREATED,
        )
    }

    @Test
    fun `replaying the velotrip collection to-review cassette reproduces the recorded toolOutput bit for bit`() {
        replayAndAssert(
            "cassettes/search-resources-velotrip-collection-to-review.json",
            "store-velotrip",
            "velotrip.myshopify.com",
            "velotrip",
            SearchResourceType.COLLECTION,
            SearchStatusFilter.TO_REVIEW,
        )
    }

    @Test
    fun `replaying the velotrip collection blocked cassette reproduces the recorded toolOutput bit for bit`() {
        replayAndAssert(
            "cassettes/search-resources-velotrip-collection-blocked.json",
            "store-velotrip",
            "velotrip.myshopify.com",
            "velotrip",
            SearchResourceType.COLLECTION,
            SearchStatusFilter.BLOCKED,
        )
    }

    @Test
    fun `replaying the velotrip collection all cassette reproduces the recorded toolOutput bit for bit`() {
        replayAndAssert(
            "cassettes/search-resources-velotrip-collection-all.json",
            "store-velotrip",
            "velotrip.myshopify.com",
            "velotrip",
            SearchResourceType.COLLECTION,
            SearchStatusFilter.ALL,
        )
    }

    @Test
    fun `replaying the velotrip article all cassette across its two recorded pages reproduces the recorded toolOutput bit for bit`() {
        replayAndAssert(
            "cassettes/search-resources-velotrip-article-all.json",
            "store-velotrip",
            "velotrip.myshopify.com",
            "velotrip",
            SearchResourceType.ARTICLE,
            SearchStatusFilter.ALL,
        )
    }

    @Test
    fun `replaying the lurelab collection all cassette reproduces the recorded toolOutput bit for bit`() {
        replayAndAssert(
            "cassettes/search-resources-lurelab-collection-all.json",
            "store-lurelab",
            "lurelab.myshopify.com",
            "lurelab",
            SearchResourceType.COLLECTION,
            SearchStatusFilter.ALL,
        )
    }

    @Test
    fun `replaying the lurelab article all cassette reproduces the recorded toolOutput bit for bit`() {
        replayAndAssert(
            "cassettes/search-resources-lurelab-article-all.json",
            "store-lurelab",
            "lurelab.myshopify.com",
            "lurelab",
            SearchResourceType.ARTICLE,
            SearchStatusFilter.ALL,
        )
    }
}
