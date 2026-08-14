package com.zickat.shopifymcpserver.metaobjects.domain

import com.zickat.shopifymcpserver.metaobjects.api.mcp.MetaobjectToolResults
import com.zickat.shopifymcpserver.metaobjects.domain.MetaobjectFieldInput
import com.zickat.shopifymcpserver.metaobjects.spi.shopify.MetaobjectsShopifyRepository
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
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.web.client.RestClient

/**
 * Exercises `create_metaobject` against the `LOT4-07` cassette (`create-metaobject-velotrip-guide-
 * theme.json`), recorded for real against the velotrip store: `metaobjectDefinitionByType`
 * (field-type resolution) THEN `metaobjectCreate` — two network calls, both replayed and asserted
 * (D11).
 */
class CreateMetaobjectUseCaseCassetteReplayTest {

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

    private fun useCaseReplaying(cassette: Cassette): Pair<CreateMetaobjectUseCase, MockWebServer> {
        val mockServer = CassetteMockWebServer.forShopifyAdminGraphQLWithTokenExchange(cassette)
        server = mockServer
        val httpClient = ShopifyRestClientHttpClient(RestClient.builder(), baseUrlFor = { mockServer.url("").toString().removeSuffix("/") })
        val vault = VaultExposedServiceFake().apply {
            plaintextByStoreId["store-velotrip"] = """{"apiKey":"key","apiSecret":"secret","shopDomain":"velotrip.myshopify.com"}""".toByteArray()
        }
        val graphQLUseCase = ShopifyAdminGraphQLUseCase(vault, httpClient)
        val gateway = ShopifyAdminGatewayImpl(graphQLUseCase)
        val metaobjectsRepository = MetaobjectsShopifyRepository(gateway)
        return CreateMetaobjectUseCase(metaobjectsRepository) to mockServer
    }

    @Test
    fun `replaying the create-metaobject velotrip guide_theme cassette emits the field-type resolution then metaobjectCreate, and reproduces the recorded toolOutput`() {
        // given
        val cassette = Cassette.fromClasspathResource("cassettes/create-metaobject-velotrip-guide-theme.json")
        val (useCase, mockServer) = useCaseReplaying(cassette)
        val toolInput = cassette.toolInput.jsonObject
        val type = toolInput.getValue("type").jsonPrimitive.content
        val fields = toolInput.getValue("fields").jsonArray.map {
            val obj = it.jsonObject
            MetaobjectFieldInput(obj.getValue("key").jsonPrimitive.content, obj.getValue("value").jsonPrimitive.content)
        }

        // when
        val result = useCase.execute("store-velotrip", type, fields).shouldBeRight()

        // then
        mockServer.takeRequest()
        cassette.calls.forEach { call -> CassetteEquivalence.assertShopifyAdminGraphQLRequestMatches(call, mockServer.takeRequest()) }

        val rendered = MetaobjectToolResults.createMetaobjectResult("velotrip", result)
        rendered.content().map { (it as TextContent).text() } shouldBe expectedTexts(cassette)
    }
}
