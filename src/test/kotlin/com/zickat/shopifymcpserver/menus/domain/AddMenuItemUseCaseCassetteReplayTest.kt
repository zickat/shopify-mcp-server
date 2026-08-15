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
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.web.client.RestClient

/**
 * Exercises `add_menu_item` against the four `LOT3-05` cassettes recorded on a throwaway menu
 * (`f4cdb41`) — two successes (insertion at N2, then N3) and two refusals (depth cap, unknown
 * parent), each asserting request equivalence (D11) and the exact recorded `toolOutput`.
 */
class AddMenuItemUseCaseCassetteReplayTest {

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

    private fun useCaseReplaying(cassette: Cassette): Pair<AddMenuItemUseCase, MockWebServer> {
        val mockServer = CassetteMockWebServer.forShopifyAdminGraphQLWithTokenExchange(cassette)
        server = mockServer
        val httpClient = ShopifyRestClientHttpClient(RestClient.builder(), baseUrlFor = { mockServer.url("").toString().removeSuffix("/") })
        val vault = VaultExposedServiceFake().apply {
            plaintextByStoreId["store-velotrip"] = """{"apiKey":"key","apiSecret":"secret","shopDomain":"velotrip.myshopify.com"}""".toByteArray()
        }
        val graphQLUseCase = ShopifyAdminGraphQLUseCase(vault, httpClient)
        val gateway = ShopifyAdminGatewayImpl(graphQLUseCase)
        val engine = MenuRewriteEngine(MenusShopifyRepository(gateway))
        return AddMenuItemUseCase(engine) to mockServer
    }

    private fun replayAndAssert(cassetteResource: String) {
        // given
        val cassette = Cassette.fromClasspathResource(cassetteResource)
        val (useCase, mockServer) = useCaseReplaying(cassette)
        val toolInput = cassette.toolInput.jsonObject
        val menuId = toolInput.getValue("menu_id").jsonPrimitive.content
        val title = toolInput.getValue("title").jsonPrimitive.content
        val resourceId = toolInput["resource_id"]?.jsonPrimitive?.content
        val url = toolInput["url"]?.jsonPrimitive?.content
        val parentItemId = toolInput["parent_item_id"]?.jsonPrimitive?.content
        val position = toolInput["position"]?.jsonPrimitive?.content?.toInt()

        // when
        val result = useCase.execute("store-velotrip", menuId, title, resourceId, url, parentItemId, position).shouldBeRight()

        // then
        mockServer.takeRequest()
        cassette.calls.forEach { call -> CassetteEquivalence.assertShopifyAdminGraphQLRequestMatches(call, mockServer.takeRequest()) }

        val rendered = MenuToolResults.addMenuItemResult("velotrip", result)
        rendered.content().map { (it as TextContent).text() } shouldBe expectedTexts(cassette)
    }

    @Test
    fun `replaying the N2 insertion success cassette reproduces the recorded toolOutput bit for bit`() {
        replayAndAssert("cassettes/add-menu-item-velotrip-test-lot3-cassette-success-l2.json")
    }

    @Test
    fun `replaying the N3 insertion success cassette reproduces the recorded toolOutput bit for bit`() {
        replayAndAssert("cassettes/add-menu-item-velotrip-test-lot3-cassette-success-l3.json")
    }

    @Test
    fun `replaying the depth-cap refusal cassette reproduces the recorded toolOutput and emits no mutation call`() {
        replayAndAssert("cassettes/add-menu-item-velotrip-refusal-depth.json")
    }

    @Test
    fun `replaying the unknown-parent refusal cassette reproduces the recorded toolOutput and emits no mutation call`() {
        replayAndAssert("cassettes/add-menu-item-velotrip-refusal-parent-unknown.json")
    }
}
