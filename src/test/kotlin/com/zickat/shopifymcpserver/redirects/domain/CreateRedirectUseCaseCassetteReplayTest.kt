package com.zickat.shopifymcpserver.redirects.domain

import com.zickat.shopifymcpserver.redirects.api.mcp.RedirectsToolResults
import com.zickat.shopifymcpserver.redirects.domain.models.CreateRedirectOutcome
import com.zickat.shopifymcpserver.redirects.spi.shopify.RedirectsShopifyRepository
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
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.web.client.RestClient

class CreateRedirectUseCaseCassetteReplayTest {

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

    private fun useCaseReplaying(cassette: Cassette): Pair<CreateRedirectUseCase, MockWebServer> {
        val mockServer = CassetteMockWebServer.forShopifyAdminGraphQLWithTokenExchange(cassette)
        server = mockServer
        val httpClient = ShopifyRestClientHttpClient(RestClient.builder(), baseUrlFor = { mockServer.url("").toString().removeSuffix("/") })
        val vault = VaultExposedServiceFake().apply {
            plaintextByStoreId["store-velotrip"] =
                """{"apiKey":"key","apiSecret":"secret","shopDomain":"velotrip.myshopify.com"}""".toByteArray()
        }
        val graphQLUseCase = ShopifyAdminGraphQLUseCase(vault, httpClient)
        val gateway = ShopifyAdminGatewayImpl(graphQLUseCase)
        return CreateRedirectUseCase(RedirectsShopifyRepository(gateway)) to mockServer
    }

    @Test
    fun `replaying the create-redirect-velotrip-created cassette reproduces the recorded outcome and toolOutput, and the request the use case sends matches the recorded request bit for bit`() {
        // given
        val cassette = Cassette.fromClasspathResource("cassettes/create-redirect-velotrip-created.json")
        val (useCase, mockServer) = useCaseReplaying(cassette)

        // when
        val outcome = useCase.execute(
            "store-velotrip",
            "/collections/test-lot2-cassette-2026-08-07",
            "/collections/outils-et-reparation-velo",
        ).shouldBeRight()

        // then
        outcome shouldBe CreateRedirectOutcome.Created
        mockServer.takeRequest()
        CassetteEquivalence.assertShopifyAdminGraphQLRequestMatches(cassette.calls.single(), mockServer.takeRequest())

        val rendered = RedirectsToolResults.createRedirectResult(
            "velotrip",
            "/collections/test-lot2-cassette-2026-08-07",
            "/collections/outils-et-reparation-velo",
            outcome,
        )
        rendered.content().map { (it as TextContent).text() } shouldBe expectedTexts(cassette)
    }

    @Test
    fun `replaying the create-redirect-velotrip-already-exists cassette reproduces the recorded outcome and toolOutput, and does not emit a second mutation`() {
        // given
        val cassette = Cassette.fromClasspathResource("cassettes/create-redirect-velotrip-already-exists.json")
        val (useCase, mockServer) = useCaseReplaying(cassette)

        // when
        val outcome = useCase.execute(
            "store-velotrip",
            "/collections/test-lot2-cassette-2026-08-07",
            "/collections/outils-et-reparation-velo",
        ).shouldBeRight()

        // then
        outcome shouldBe CreateRedirectOutcome.AlreadyExists
        mockServer.requestCount shouldBe 2
        mockServer.takeRequest()
        CassetteEquivalence.assertShopifyAdminGraphQLRequestMatches(cassette.calls.single(), mockServer.takeRequest())

        val rendered = RedirectsToolResults.createRedirectResult(
            "velotrip",
            "/collections/test-lot2-cassette-2026-08-07",
            "/collections/outils-et-reparation-velo",
            outcome,
        )
        rendered.content().map { (it as TextContent).text() } shouldBe expectedTexts(cassette)
    }
}
