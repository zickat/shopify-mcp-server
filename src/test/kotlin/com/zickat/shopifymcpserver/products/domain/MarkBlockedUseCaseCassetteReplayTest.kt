package com.zickat.shopifymcpserver.products.domain

import com.zickat.shopifymcpserver.products.api.mcp.ProductsToolResults
import com.zickat.shopifymcpserver.products.spi.shopify.ProductShopifyRepository
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
import java.util.concurrent.TimeUnit

class MarkBlockedUseCaseCassetteReplayTest {

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

    private fun useCaseReplaying(cassette: Cassette): Pair<MarkBlockedUseCase, MockWebServer> {
        val mockServer = CassetteMockWebServer.forShopifyAdminGraphQLWithTokenExchange(cassette)
        server = mockServer
        val httpClient = ShopifyRestClientHttpClient(RestClient.builder(), baseUrlFor = { mockServer.url("").toString().removeSuffix("/") })
        val vault = VaultExposedServiceFake().apply {
            plaintextByStoreId["store-velotrip"] =
                """{"apiKey":"key","apiSecret":"secret","shopDomain":"velotrip.myshopify.com"}""".toByteArray()
        }
        val graphQLUseCase = ShopifyAdminGraphQLUseCase(vault, httpClient)
        val gateway = ShopifyAdminGatewayImpl(graphQLUseCase)
        return MarkBlockedUseCase(ProductShopifyRepository(gateway)) to mockServer
    }

    @Test
    fun `replaying the mark-blocked-velotrip-zz-test-cobaye cassette reproduces the recorded toolOutput, and the request the use case sends matches the recorded request bit for bit`() {
        // given
        val cassette = Cassette.fromClasspathResource("cassettes/mark-blocked-velotrip-zz-test-cobaye.json")
        val (useCase, mockServer) = useCaseReplaying(cassette)
        val toolInput = cassette.toolInput.jsonObject
        val resourceType = toolInput.getValue("resource_type").jsonPrimitive.content
        val resourceId = toolInput.getValue("resource_id").jsonPrimitive.content
        val reason = toolInput.getValue("reason").jsonPrimitive.content

        // when
        val result = useCase.execute("store-velotrip", resourceId).shouldBeRight()

        // then
        requireNotNull(mockServer.takeRequest(10, TimeUnit.SECONDS)) { "expected the token exchange request, none arrived within 10s" }
        val recorded = requireNotNull(mockServer.takeRequest(10, TimeUnit.SECONDS)) { "expected the setMetafields request, none arrived within 10s" }
        CassetteEquivalence.assertShopifyAdminGraphQLRequestMatches(cassette.calls.single(), recorded)

        val rendered = ProductsToolResults.markBlockedResult("velotrip", resourceType, resourceId, reason, result)
        rendered.content().map { (it as TextContent).text() } shouldBe expectedTexts(cassette)
    }
}
