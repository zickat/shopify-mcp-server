package com.zickat.shopifymcpserver.metaobjects.domain

import com.zickat.shopifymcpserver.metaobjects.api.mcp.MetaobjectToolResults
import com.zickat.shopifymcpserver.metaobjects.spi.shopify.MetaobjectsShopifyRepository
import com.zickat.shopifymcpserver.shared_kernel.Cassette
import com.zickat.shopifymcpserver.shared_kernel.CassetteEquivalence
import com.zickat.shopifymcpserver.shared_kernel.CassetteMockWebServer
import com.zickat.shopifymcpserver.shopify.domain.ShopifyAdminGatewayImpl
import com.zickat.shopifymcpserver.shopify.domain.ShopifyAdminGraphQLUseCase
import com.zickat.shopifymcpserver.shopify.spi.http.ShopifyRestClientHttpClient
import com.zickat.shopifymcpserver.vault.VaultExposedServiceFake
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.matchers.collections.shouldHaveSize
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
 * Exercises `delete_metaobject` against the two `LOT4-07` cassettes recorded for real:
 * - `delete-metaobject-velotrip-guide-theme.json` — orphan metaobject, deletion proceeds:
 *   `GetMetaobjectBeforeDelete` THEN `FetchMetaobjectReferences` THEN `metaobjectDelete` (D11:
 *   three network calls, all asserted).
 * - `delete-metaobject-velotrip-guide-theme-refused-referenced.json` — referenced metaobject,
 *   `confirm_referenced_deletion` omitted: refused BEFORE the delete mutation — only two network
 *   calls (existence + references), never `metaobjectDelete`.
 */
class DeleteMetaobjectUseCaseCassetteReplayTest {

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

    private fun useCaseReplaying(cassette: Cassette): Pair<DeleteMetaobjectUseCase, MockWebServer> {
        val mockServer = CassetteMockWebServer.forShopifyAdminGraphQLWithTokenExchange(cassette)
        server = mockServer
        val httpClient = ShopifyRestClientHttpClient(RestClient.builder(), baseUrlFor = { mockServer.url("").toString().removeSuffix("/") })
        val vault = VaultExposedServiceFake().apply {
            plaintextByStoreId["store-velotrip"] = """{"apiKey":"key","apiSecret":"secret","shopDomain":"velotrip.myshopify.com"}""".toByteArray()
        }
        val graphQLUseCase = ShopifyAdminGraphQLUseCase(vault, httpClient)
        val gateway = ShopifyAdminGatewayImpl(graphQLUseCase)
        val metaobjectsRepository = MetaobjectsShopifyRepository(gateway)
        return DeleteMetaobjectUseCase(metaobjectsRepository) to mockServer
    }

    private fun replayAndAssert(cassetteResource: String, expectedNetworkCalls: Int) {
        // given
        val cassette = Cassette.fromClasspathResource(cassetteResource)
        val (useCase, mockServer) = useCaseReplaying(cassette)
        val toolInput = cassette.toolInput.jsonObject
        val metaobjectId = toolInput.getValue("metaobject_id").jsonPrimitive.content
        val confirmReferencedDeletion = toolInput["confirm_referenced_deletion"]?.jsonPrimitive?.boolean ?: false

        // when
        val result = useCase.execute("store-velotrip", metaobjectId, confirmReferencedDeletion).shouldBeRight()

        // then
        mockServer.takeRequest()
        cassette.calls shouldHaveSize expectedNetworkCalls
        cassette.calls.forEach { call -> CassetteEquivalence.assertShopifyAdminGraphQLRequestMatches(call, mockServer.takeRequest()) }

        val rendered = MetaobjectToolResults.deleteMetaobjectResult("velotrip", result)
        rendered.content().map { (it as TextContent).text() } shouldBe expectedTexts(cassette)
    }

    @Test
    fun `replaying the delete-metaobject velotrip orphan cassette emits existence check, references check, then metaobjectDelete, and reproduces the recorded toolOutput`() {
        replayAndAssert("cassettes/delete-metaobject-velotrip-guide-theme.json", expectedNetworkCalls = 3)
    }

    @Test
    fun `replaying the delete-metaobject velotrip refused-referenced cassette stops after the references check — no metaobjectDelete emitted — and reproduces the recorded refusal`() {
        replayAndAssert("cassettes/delete-metaobject-velotrip-guide-theme-refused-referenced.json", expectedNetworkCalls = 2)
    }
}
