package com.zickat.shopifymcpserver.products.domain

import com.zickat.shopifymcpserver.api.mcp.McpToolResults
import com.zickat.shopifymcpserver.products.exposed_interface.model.ToReviewResourceType
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
 * Exercises `list_to_review` against a real `ListToReviewUseCase`, replaying the `LOT5-01`
 * cassettes across the three resource types it supports — including the article cassette's two
 * paginated calls.
 */
class ListToReviewUseCaseCassetteReplayTest {

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

    private fun useCaseReplaying(cassette: Cassette, storeId: String, shopDomain: String): Pair<ListToReviewUseCase, MockWebServer> {
        val mockServer = CassetteMockWebServer.forShopifyAdminGraphQLWithTokenExchange(cassette)
        server = mockServer
        val httpClient = ShopifyRestClientHttpClient(RestClient.builder(), baseUrlFor = { mockServer.url("").toString().removeSuffix("/") })
        val vault = VaultExposedServiceFake().apply {
            plaintextByStoreId[storeId] = """{"apiKey":"key","apiSecret":"secret","shopDomain":"$shopDomain"}""".toByteArray()
        }
        val graphQLUseCase = ShopifyAdminGraphQLUseCase(vault, httpClient)
        val gateway = ShopifyAdminGatewayImpl(graphQLUseCase)
        return ListToReviewUseCase(gateway) to mockServer
    }

    private fun replayAndAssert(
        cassetteResource: String,
        storeId: String,
        shopDomain: String,
        storeSlug: String,
        resourceType: ToReviewResourceType,
    ) {
        // given
        val cassette = Cassette.fromClasspathResource(cassetteResource)
        val (useCase, mockServer) = useCaseReplaying(cassette, storeId, shopDomain)

        // when
        val result = useCase.execute(storeId, resourceType).shouldBeRight()

        // then
        mockServer.takeRequest()
        cassette.calls.forEach { call ->
            CassetteEquivalence.assertShopifyAdminGraphQLRequestMatches(call, mockServer.takeRequest())
        }

        val rendered = McpToolResults.listToReviewResult(storeSlug, result)
        rendered.content().map { (it as TextContent).text() } shouldBe expectedTexts(cassette)
    }

    @Test
    fun `replaying the velotrip product empty cassette reproduces the recorded toolOutput bit for bit`() {
        replayAndAssert(
            "cassettes/list-to-review-velotrip-product-empty.json",
            "store-velotrip",
            "velotrip.myshopify.com",
            "velotrip",
            ToReviewResourceType.PRODUCT,
        )
    }

    @Test
    fun `replaying the velotrip collection nonempty cassette reproduces the recorded toolOutput bit for bit`() {
        replayAndAssert(
            "cassettes/list-to-review-velotrip-collection-nonempty.json",
            "store-velotrip",
            "velotrip.myshopify.com",
            "velotrip",
            ToReviewResourceType.COLLECTION,
        )
    }

    @Test
    fun `replaying the velotrip article empty cassette across its two paginated calls reproduces the recorded toolOutput bit for bit`() {
        replayAndAssert(
            "cassettes/list-to-review-velotrip-article-empty.json",
            "store-velotrip",
            "velotrip.myshopify.com",
            "velotrip",
            ToReviewResourceType.ARTICLE,
        )
    }

    @Test
    fun `replaying the lurelab product nonempty cassette reproduces the recorded toolOutput bit for bit`() {
        replayAndAssert(
            "cassettes/list-to-review-lurelab-product-nonempty.json",
            "store-lurelab",
            "lurelab.myshopify.com",
            "lurelab",
            ToReviewResourceType.PRODUCT,
        )
    }
}
