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
 * Exercises `update_page` against the `LOT4-09` cassette (`update-page-velotrip-test-lot4-cassette-
 * partial-title.json`), recorded for real: `FetchPageNative` (existence check) THEN `pageUpdate` —
 * two network calls, both asserted (D11). Only `title` is supplied — the cassette proves the
 * `PageUpdateInput` payload carries `title` alone, `body`/`handle` absent (not sent with their
 * current value).
 */
class UpdatePageUseCaseCassetteReplayTest {

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

    private fun useCaseReplaying(cassette: Cassette): Pair<UpdatePageUseCase, MockWebServer> {
        val mockServer = CassetteMockWebServer.forShopifyAdminGraphQLWithTokenExchange(cassette)
        server = mockServer
        val httpClient = ShopifyRestClientHttpClient(RestClient.builder(), baseUrlFor = { mockServer.url("").toString().removeSuffix("/") })
        val vault = VaultExposedServiceFake().apply {
            plaintextByStoreId["store-velotrip"] = """{"apiKey":"key","apiSecret":"secret","shopDomain":"velotrip.myshopify.com"}""".toByteArray()
        }
        val graphQLUseCase = ShopifyAdminGraphQLUseCase(vault, httpClient)
        val gateway = ShopifyAdminGatewayImpl(graphQLUseCase)
        val pageRepository = PageShopifyRepository(gateway)
        return UpdatePageUseCase(pageRepository) to mockServer
    }

    @Test
    fun `replaying the update-page velotrip partial-title cassette emits existence check then pageUpdate with only title in the payload, and reproduces the recorded toolOutput`() {
        // given
        val cassette = Cassette.fromClasspathResource("cassettes/update-page-velotrip-test-lot4-cassette-partial-title.json")
        val (useCase, mockServer) = useCaseReplaying(cassette)
        val toolInput = cassette.toolInput.jsonObject
        val pageId = toolInput.getValue("page_id").jsonPrimitive.content
        val title = toolInput["title"]?.jsonPrimitive?.content
        val body = toolInput["body"]?.jsonPrimitive?.content
        val handle = toolInput["handle"]?.jsonPrimitive?.content

        // when
        val result = useCase.execute("store-velotrip", pageId, title, body, handle).shouldBeRight()

        // then
        mockServer.takeRequest()
        cassette.calls.forEach { call -> CassetteEquivalence.assertShopifyAdminGraphQLRequestMatches(call, mockServer.takeRequest()) }

        val rendered = PageToolResults.updatePageResult("velotrip", result)
        rendered.content().map { (it as TextContent).text() } shouldBe expectedTexts(cassette)
    }
}
