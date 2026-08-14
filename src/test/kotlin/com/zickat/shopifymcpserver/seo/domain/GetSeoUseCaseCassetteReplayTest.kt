package com.zickat.shopifymcpserver.seo.domain

import com.zickat.shopifymcpserver.api.mcp.McpToolResults
import com.zickat.shopifymcpserver.seo.domain.models.SeoResourceType
import com.zickat.shopifymcpserver.seo.spi.shopify.SeoShopifyRepository
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
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.web.client.RestClient

/**
 * Exercises the whole `LOT4-01` cassette-recorded behaviour of `get_seo` against a real
 * `GetSeoUseCase` — both storage mechanisms (native `seo{}` for a collection, `global.*`
 * metafields for an article), each in a defined and an undefined variant, plus the not-found case.
 * Every expected response is read straight from the cassette's own `toolOutput`, never restated by
 * hand, so a corrupted cassette fails this test rather than passing silently — see the tautology
 * check recorded in progress.md for how that guarantee was verified.
 */
class GetSeoUseCaseCassetteReplayTest {

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

    private fun useCaseReplaying(cassette: Cassette, storeId: String, shopDomain: String): Pair<GetSeoUseCase, MockWebServer> {
        val mockServer = CassetteMockWebServer.forShopifyAdminGraphQLWithTokenExchange(cassette)
        server = mockServer
        val httpClient = ShopifyRestClientHttpClient(RestClient.builder(), baseUrlFor = { mockServer.url("").toString().removeSuffix("/") })
        val vault = VaultExposedServiceFake().apply {
            plaintextByStoreId[storeId] = """{"apiKey":"key","apiSecret":"secret","shopDomain":"$shopDomain"}""".toByteArray()
        }
        val graphQLUseCase = ShopifyAdminGraphQLUseCase(vault, httpClient)
        val gateway = ShopifyAdminGatewayImpl(graphQLUseCase)
        return GetSeoUseCase(SeoShopifyRepository(gateway)) to mockServer
    }

    private fun replayAndAssert(cassetteResource: String, storeId: String, shopDomain: String, storeSlug: String) {
        // given
        val cassette = Cassette.fromClasspathResource(cassetteResource)
        val (useCase, mockServer) = useCaseReplaying(cassette, storeId, shopDomain)
        val toolInput = cassette.toolInput.jsonObject
        val resourceType = toolInput.getValue("resource_type").jsonPrimitive.content
        val resourceId = toolInput.getValue("resource_id").jsonPrimitive.content
        val parsedResourceType = requireNotNull(SeoResourceType.fromToolValue(resourceType)) {
            "cassette $cassetteResource carries an unknown resource_type '$resourceType'"
        }

        // when
        val result = useCase.execute(storeId, parsedResourceType, resourceId).shouldBeRight()

        // then
        mockServer.takeRequest()
        cassette.calls.forEach { call ->
            CassetteEquivalence.assertShopifyAdminGraphQLRequestMatches(call, mockServer.takeRequest())
        }

        val rendered = McpToolResults.getSeoResult(storeSlug, resourceType, resourceId, result)
        rendered.content().map { (it as TextContent).text() } shouldBe expectedTexts(cassette)
    }

    @Test
    fun `replaying the velotrip collection native-mechanism defined cassette reproduces the recorded toolOutput bit for bit`() {
        replayAndAssert(
            "cassettes/get-seo-velotrip-collection-native-defined.json",
            "store-velotrip",
            "velotrip.myshopify.com",
            "velotrip",
        )
    }

    @Test
    fun `replaying the velotrip article metafield-mechanism defined cassette reproduces the recorded toolOutput bit for bit`() {
        replayAndAssert(
            "cassettes/get-seo-velotrip-article-metafield-defined.json",
            "store-velotrip",
            "velotrip.myshopify.com",
            "velotrip",
        )
    }

    @Test
    fun `replaying the velotrip collection not-found cassette reproduces the recorded toolOutput bit for bit`() {
        replayAndAssert(
            "cassettes/get-seo-velotrip-collection-not-found.json",
            "store-velotrip",
            "velotrip.myshopify.com",
            "velotrip",
        )
    }

    @Test
    fun `replaying the lurelab collection native-mechanism undefined cassette reproduces the recorded toolOutput bit for bit`() {
        replayAndAssert(
            "cassettes/get-seo-lurelab-collection-native-undefined.json",
            "store-lurelab",
            "lurelab.myshopify.com",
            "lurelab",
        )
    }

    @Test
    fun `replaying the lurelab article metafield-mechanism undefined cassette reproduces the recorded toolOutput bit for bit`() {
        replayAndAssert(
            "cassettes/get-seo-lurelab-article-metafield-undefined.json",
            "store-lurelab",
            "lurelab.myshopify.com",
            "lurelab",
        )
    }
}
