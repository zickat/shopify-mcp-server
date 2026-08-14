package com.zickat.shopifymcpserver.pages.domain

import com.zickat.shopifymcpserver.pages.api.mcp.PageToolResults
import com.zickat.shopifymcpserver.pages.spi.shopify.PageShopifyRepository
import com.zickat.shopifymcpserver.shared_kernel.Cassette
import com.zickat.shopifymcpserver.shared_kernel.CassetteEquivalence
import com.zickat.shopifymcpserver.shared_kernel.CassetteMockWebServer
import com.zickat.shopifymcpserver.shopify.exposed_interface.ShopifyAdminGatewayImpl
import com.zickat.shopifymcpserver.shopify.domain.ShopifyAdminGraphQLUseCase
import com.zickat.shopifymcpserver.shopify.spi.http.ShopifyRestClientHttpClient
import com.zickat.shopifymcpserver.vault.VaultExposedServiceFake
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.modelcontextprotocol.spec.McpSchema.TextContent
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.web.client.RestClient

/**
 * Exercises `publish_page`/`unpublish_page` (single [TogglePagePublishUseCase]) against the three
 * `LOT4-09` cassettes recorded for real:
 * - `publish-page-...json` — draft -> published: `FetchPageNative` THEN `TogglePagePublish`
 *   mutation (two network calls).
 * - `publish-page-...-noop-already-published.json` — already published: only `FetchPageNative`,
 *   no mutation emitted (one network call) — the no-op documented in the task.
 * - `unpublish-page-...json` — published -> draft: same two-call shape as publish.
 */
class TogglePagePublishUseCaseCassetteReplayTest {

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

    private fun useCaseReplaying(cassette: Cassette): Pair<TogglePagePublishUseCase, MockWebServer> {
        val mockServer = CassetteMockWebServer.forShopifyAdminGraphQLWithTokenExchange(cassette)
        server = mockServer
        val httpClient = ShopifyRestClientHttpClient(RestClient.builder(), baseUrlFor = { mockServer.url("").toString().removeSuffix("/") })
        val vault = VaultExposedServiceFake().apply {
            plaintextByStoreId["store-velotrip"] = """{"apiKey":"key","apiSecret":"secret","shopDomain":"velotrip.myshopify.com"}""".toByteArray()
        }
        val graphQLUseCase = ShopifyAdminGraphQLUseCase(vault, httpClient)
        val gateway = ShopifyAdminGatewayImpl(graphQLUseCase)
        val pageRepository = PageShopifyRepository(gateway)
        return TogglePagePublishUseCase(pageRepository) to mockServer
    }

    private fun replayAndAssert(cassetteResource: String, target: Boolean, expectedNetworkCalls: Int) {
        // given
        val cassette = Cassette.fromClasspathResource(cassetteResource)
        val (useCase, mockServer) = useCaseReplaying(cassette)
        val pageId = cassette.toolInput.jsonObject.getValue("page_id").jsonPrimitive.content

        // when
        val result = useCase.execute("store-velotrip", pageId, target).shouldBeRight()

        // then
        mockServer.takeRequest()
        cassette.calls shouldHaveSize expectedNetworkCalls
        cassette.calls.forEach { call -> CassetteEquivalence.assertShopifyAdminGraphQLRequestMatches(call, mockServer.takeRequest()) }

        val rendered = PageToolResults.togglePagePublishResult("velotrip", result)
        rendered.content().map { (it as TextContent).text() } shouldBe expectedTexts(cassette)
    }

    @Test
    fun `replaying the publish-page velotrip cassette emits existence check then TogglePagePublish, and reproduces the recorded toolOutput`() {
        replayAndAssert("cassettes/publish-page-velotrip-test-lot4-cassette.json", target = true, expectedNetworkCalls = 2)
    }

    @Test
    fun `replaying the publish-page velotrip noop-already-published cassette emits only the existence check — no mutation — and reproduces the recorded no-op message`() {
        replayAndAssert(
            "cassettes/publish-page-velotrip-test-lot4-cassette-noop-already-published.json",
            target = true,
            expectedNetworkCalls = 1,
        )
    }

    @Test
    fun `replaying the unpublish-page velotrip cassette emits existence check then TogglePagePublish, and reproduces the recorded toolOutput`() {
        replayAndAssert("cassettes/unpublish-page-velotrip-test-lot4-cassette.json", target = false, expectedNetworkCalls = 2)
    }
}
