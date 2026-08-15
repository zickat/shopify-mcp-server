package com.zickat.shopifymcpserver.menus.domain

import com.zickat.shopifymcpserver.menus.api.mcp.MenuToolResults
import com.zickat.shopifymcpserver.menus.spi.shopify.MenusShopifyRepository
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
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.web.client.RestClient

/**
 * Exercises `update_menu` against the two `LOT3-05` cassettes — a title-only rename (handle
 * untouched, `confirm_handle_change: false`) and the unconditional handle-change refusal on the
 * default `main-menu` — each asserting request equivalence (D11) and the exact recorded
 * `toolOutput`.
 */
class UpdateMenuUseCaseCassetteReplayTest {

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

    private fun useCaseReplaying(cassette: Cassette): Pair<UpdateMenuUseCase, MockWebServer> {
        val mockServer = CassetteMockWebServer.forShopifyAdminGraphQLWithTokenExchange(cassette)
        server = mockServer
        val httpClient = ShopifyRestClientHttpClient(RestClient.builder(), baseUrlFor = { mockServer.url("").toString().removeSuffix("/") })
        val vault = VaultExposedServiceFake().apply {
            plaintextByStoreId["store-velotrip"] = """{"apiKey":"key","apiSecret":"secret","shopDomain":"velotrip.myshopify.com"}""".toByteArray()
        }
        val graphQLUseCase = ShopifyAdminGraphQLUseCase(vault, httpClient)
        val gateway = ShopifyAdminGatewayImpl(graphQLUseCase)
        val engine = MenuRewriteEngine(MenusShopifyRepository(gateway))
        return UpdateMenuUseCase(engine) to mockServer
    }

    private fun replayAndAssert(cassetteResource: String) {
        // given
        val cassette = Cassette.fromClasspathResource(cassetteResource)
        val (useCase, mockServer) = useCaseReplaying(cassette)
        val toolInput = cassette.toolInput.jsonObject
        val menuId = toolInput.getValue("menu_id").jsonPrimitive.content
        val title = toolInput["title"]?.jsonPrimitive?.content
        val handle = toolInput["handle"]?.jsonPrimitive?.content
        val confirmHandleChange = toolInput["confirm_handle_change"]?.jsonPrimitive?.boolean ?: false

        // when
        val result = useCase.execute("store-velotrip", menuId, title, handle, confirmHandleChange).shouldBeRight()

        // then
        mockServer.takeRequest()
        cassette.calls.forEach { call -> CassetteEquivalence.assertShopifyAdminGraphQLRequestMatches(call, mockServer.takeRequest()) }

        val rendered = MenuToolResults.updateMenuResult("velotrip", result)
        rendered.content().map { (it as TextContent).text() } shouldBe expectedTexts(cassette)
    }

    @Test
    fun `replaying the title-only rename success cassette reproduces the recorded toolOutput bit for bit`() {
        replayAndAssert("cassettes/update-menu-velotrip-test-lot3-cassette-title.json")
    }

    @Test
    fun `replaying the default-menu handle-change refusal cassette reproduces the recorded toolOutput and emits no mutation call`() {
        replayAndAssert("cassettes/update-menu-velotrip-refusal-handle-default-main-menu.json")
    }
}
