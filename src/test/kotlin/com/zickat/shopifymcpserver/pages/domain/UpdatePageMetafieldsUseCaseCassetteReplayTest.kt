package com.zickat.shopifymcpserver.pages.domain

import com.zickat.shopifymcpserver.api.mcp.McpToolResults
import com.zickat.shopifymcpserver.pages.exposed_interface.model.PageMetafieldInput
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
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.web.client.RestClient

/**
 * Exercises `update_page_metafields` against the `LOT4-09` cassette (`update-page-metafields-
 * velotrip-test-lot4-cassette.json`), recorded for real: `FetchPageMetafields` (existence check,
 * same helper as `get_page_metafields`) THEN `SetMetafields` (shared `shopify` socle helper, D32) —
 * two network calls, both asserted (D11).
 */
class UpdatePageMetafieldsUseCaseCassetteReplayTest {

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

    private fun useCaseReplaying(cassette: Cassette): Pair<UpdatePageMetafieldsUseCase, MockWebServer> {
        val mockServer = CassetteMockWebServer.forShopifyAdminGraphQLWithTokenExchange(cassette)
        server = mockServer
        val httpClient = ShopifyRestClientHttpClient(RestClient.builder(), baseUrlFor = { mockServer.url("").toString().removeSuffix("/") })
        val vault = VaultExposedServiceFake().apply {
            plaintextByStoreId["store-velotrip"] = """{"apiKey":"key","apiSecret":"secret","shopDomain":"velotrip.myshopify.com"}""".toByteArray()
        }
        val graphQLUseCase = ShopifyAdminGraphQLUseCase(vault, httpClient)
        val gateway = ShopifyAdminGatewayImpl(graphQLUseCase)
        return UpdatePageMetafieldsUseCase(gateway) to mockServer
    }

    @Test
    fun `replaying the update-page-metafields velotrip cassette emits existence check then SetMetafields, and reproduces the recorded toolOutput`() {
        // given
        val cassette = Cassette.fromClasspathResource("cassettes/update-page-metafields-velotrip-test-lot4-cassette.json")
        val (useCase, mockServer) = useCaseReplaying(cassette)
        val toolInput = cassette.toolInput.jsonObject
        val pageId = toolInput.getValue("page_id").jsonPrimitive.content
        val metafields = toolInput.getValue("metafields").jsonArray.map {
            val obj = it.jsonObject
            PageMetafieldInput(
                obj.getValue("key").jsonPrimitive.content,
                obj.getValue("type").jsonPrimitive.content,
                obj.getValue("value").jsonPrimitive.content,
            )
        }

        // when
        val result = useCase.execute("store-velotrip", pageId, metafields).shouldBeRight()

        // then
        mockServer.takeRequest()
        cassette.calls.forEach { call -> CassetteEquivalence.assertShopifyAdminGraphQLRequestMatches(call, mockServer.takeRequest()) }

        val rendered = McpToolResults.updatePageMetafieldsResult("velotrip", result)
        rendered.content().map { (it as TextContent).text() } shouldBe expectedTexts(cassette)
    }
}
