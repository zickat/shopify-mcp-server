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
 * Exercises `delete_menu` against the three `LOT3-05` cassettes — the throwaway menu actually
 * destroyed, the unconditional refusal on the default `footer`, and the non-empty refusal without
 * confirmation — each asserting request equivalence (D11) and the exact recorded `toolOutput`.
 */
class DeleteMenuUseCaseCassetteReplayTest {

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

    private fun useCaseReplaying(cassette: Cassette): Pair<DeleteMenuUseCase, MockWebServer> {
        val mockServer = CassetteMockWebServer.forShopifyAdminGraphQLWithTokenExchange(cassette)
        server = mockServer
        val httpClient = ShopifyRestClientHttpClient(RestClient.builder(), baseUrlFor = { mockServer.url("").toString().removeSuffix("/") })
        val vault = VaultExposedServiceFake().apply {
            plaintextByStoreId["store-velotrip"] = """{"apiKey":"key","apiSecret":"secret","shopDomain":"velotrip.myshopify.com"}""".toByteArray()
        }
        val graphQLUseCase = ShopifyAdminGraphQLUseCase(vault, httpClient)
        val gateway = ShopifyAdminGatewayImpl(graphQLUseCase)
        return DeleteMenuUseCase(MenusShopifyRepository(gateway)) to mockServer
    }

    private fun replayAndAssert(cassetteResource: String) {
        // given
        val cassette = Cassette.fromClasspathResource(cassetteResource)
        val (useCase, mockServer) = useCaseReplaying(cassette)
        val toolInput = cassette.toolInput.jsonObject
        val menuId = toolInput.getValue("menu_id").jsonPrimitive.content
        val confirmNonEmptyDeletion = toolInput["confirm_non_empty_deletion"]?.jsonPrimitive?.boolean ?: false

        // when
        val result = useCase.execute("store-velotrip", menuId, confirmNonEmptyDeletion).shouldBeRight()

        // then
        mockServer.takeRequest()
        cassette.calls.forEach { call -> CassetteEquivalence.assertShopifyAdminGraphQLRequestMatches(call, mockServer.takeRequest()) }

        val rendered = MenuToolResults.deleteMenuResult("velotrip", result)
        rendered.content().map { (it as TextContent).text() } shouldBe expectedTexts(cassette)
    }

    @Test
    fun `replaying the throwaway-menu deletion cassette reproduces the recorded toolOutput bit for bit`() {
        replayAndAssert("cassettes/delete-menu-velotrip-test-lot3-cassette.json")
    }

    @Test
    fun `replaying the default-footer refusal cassette reproduces the recorded toolOutput and emits no delete call`() {
        replayAndAssert("cassettes/delete-menu-velotrip-refusal-default-footer.json")
    }

    @Test
    fun `replaying the non-empty refusal cassette reproduces the recorded toolOutput and emits no delete call`() {
        replayAndAssert("cassettes/delete-menu-velotrip-refusal-non-empty.json")
    }
}
