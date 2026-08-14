package com.zickat.shopifymcpserver.pages.domain

import com.zickat.shopifymcpserver.pages.api.mcp.PageToolResults
import com.zickat.shopifymcpserver.pages.spi.shopify.PageShopifyRepository
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
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.web.client.RestClient

/**
 * Exercises the whole `LOT4-01` cassette-recorded behaviour of `get_page_metafields` (no keys,
 * mixed present/absent keys, and page not found) against a real `GetPageMetafieldsUseCase` — every
 * expected response is read straight from the cassette's own `toolOutput`, never restated by hand,
 * so a corrupted cassette fails this test rather than passing silently — see the tautology check
 * recorded in progress.md for how that guarantee was verified.
 */
class GetPageMetafieldsUseCaseCassetteReplayTest {

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

    private fun useCaseReplaying(cassette: Cassette, storeId: String, shopDomain: String): Pair<GetPageMetafieldsUseCase, MockWebServer> {
        val mockServer = CassetteMockWebServer.forShopifyAdminGraphQLWithTokenExchange(cassette)
        server = mockServer
        val httpClient = ShopifyRestClientHttpClient(RestClient.builder(), baseUrlFor = { mockServer.url("").toString().removeSuffix("/") })
        val vault = VaultExposedServiceFake().apply {
            plaintextByStoreId[storeId] = """{"apiKey":"key","apiSecret":"secret","shopDomain":"$shopDomain"}""".toByteArray()
        }
        val graphQLUseCase = ShopifyAdminGraphQLUseCase(vault, httpClient)
        val gateway = ShopifyAdminGatewayImpl(graphQLUseCase)
        val pageRepository = PageShopifyRepository(gateway)
        return GetPageMetafieldsUseCase(pageRepository) to mockServer
    }

    private fun replayAndAssert(cassetteResource: String, storeId: String, shopDomain: String, storeSlug: String) {
        // given
        val cassette = Cassette.fromClasspathResource(cassetteResource)
        val (useCase, mockServer) = useCaseReplaying(cassette, storeId, shopDomain)
        val pageId = cassette.toolInput.jsonObject.getValue("page_id").jsonPrimitive.content
        val keys = (cassette.toolInput.jsonObject["keys"] as? JsonArray)?.map { it.jsonPrimitive.content }

        // when
        val result = useCase.execute(storeId, pageId, keys).shouldBeRight()

        // then
        mockServer.takeRequest()
        cassette.calls.forEach { call ->
            CassetteEquivalence.assertShopifyAdminGraphQLRequestMatches(call, mockServer.takeRequest())
        }

        val rendered = PageToolResults.getPageMetafieldsResult(storeSlug, result)
        rendered.content().map { (it as TextContent).text() } shouldBe expectedTexts(cassette)
    }

    @Test
    fun `replaying the velotrip no-keys cassette reproduces the recorded toolOutput bit for bit`() {
        replayAndAssert(
            "cassettes/get-page-metafields-velotrip-no-keys.json",
            "store-velotrip",
            "velotrip.myshopify.com",
            "velotrip",
        )
    }

    @Test
    fun `replaying the velotrip mixed-keys cassette reproduces the recorded toolOutput bit for bit`() {
        replayAndAssert(
            "cassettes/get-page-metafields-velotrip-keys-mixed.json",
            "store-velotrip",
            "velotrip.myshopify.com",
            "velotrip",
        )
    }

    @Test
    fun `replaying the not-found cassette reproduces the recorded toolOutput bit for bit`() {
        replayAndAssert(
            "cassettes/get-page-metafields-not-found.json",
            "store-velotrip",
            "velotrip.myshopify.com",
            "velotrip",
        )
    }
}
