package com.zickat.shopifymcpserver.products.domain

import com.zickat.shopifymcpserver.api.mcp.McpToolResults
import com.zickat.shopifymcpserver.shared_kernel.Cassette
import com.zickat.shopifymcpserver.shared_kernel.CassetteCall
import com.zickat.shopifymcpserver.shared_kernel.CassetteEquivalence
import com.zickat.shopifymcpserver.shared_kernel.CassetteMockWebServer
import com.zickat.shopifymcpserver.shopify.domain.ShopifyAdminGatewayImpl
import com.zickat.shopifymcpserver.shopify.domain.ShopifyAdminGraphQLUseCase
import com.zickat.shopifymcpserver.shopify.spi.http.ShopifyRestClientHttpClient
import com.zickat.shopifymcpserver.vault.VaultExposedServiceFake
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.modelcontextprotocol.spec.McpSchema.TextContent
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.web.client.RestClient
import java.util.concurrent.TimeUnit

class PublishResourceUseCaseCassetteReplayTest {

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

    private fun resourceIdOf(cassette: Cassette): String =
        cassette.toolInput.jsonObject.getValue("resource_id").jsonPrimitive.content

    private fun useCaseReplaying(calls: List<CassetteCall>): Pair<PublishResourceUseCase, MockWebServer> {
        val mockServer = CassetteMockWebServer.forShopifyAdminGraphQLWithTokenExchange(calls)
        server = mockServer
        val httpClient = ShopifyRestClientHttpClient(RestClient.builder(), baseUrlFor = { mockServer.url("").toString().removeSuffix("/") })
        val vault = VaultExposedServiceFake().apply {
            plaintextByStoreId["store-velotrip"] =
                """{"apiKey":"key","apiSecret":"secret","shopDomain":"velotrip.myshopify.com"}""".toByteArray()
        }
        val graphQLUseCase = ShopifyAdminGraphQLUseCase(vault, httpClient)
        val gateway = ShopifyAdminGatewayImpl(graphQLUseCase)
        return PublishResourceUseCase(gateway) to mockServer
    }

    private fun CassetteCall.withPatchedProduct(title: String, count: Int): CassetteCall {
        val responseObj = response as JsonObject
        val bodyObj = responseObj.getValue("body") as JsonObject
        val productObj = bodyObj.getValue("product") as JsonObject
        val countHolder = productObj.getValue("resourcePublicationsCount") as JsonObject
        val patchedCountHolder = JsonObject(countHolder + ("count" to JsonPrimitive(count)))
        val patchedProduct = JsonObject(productObj + ("title" to JsonPrimitive(title)) + ("resourcePublicationsCount" to patchedCountHolder))
        val patchedBody = JsonObject(bodyObj + ("product" to patchedProduct))
        return copy(response = JsonObject(responseObj + ("body" to patchedBody)))
    }

    private fun CassetteCall.withPatchedOnlineStorePublicationName(): CassetteCall {
        val responseObj = response as JsonObject
        val bodyObj = responseObj.getValue("body") as JsonObject
        val publicationsObj = bodyObj.getValue("publications") as JsonObject
        val edges = publicationsObj.getValue("edges") as JsonArray
        val firstEdge = edges[0] as JsonObject
        val firstNode = firstEdge.getValue("node") as JsonObject
        val patchedNode = JsonObject(firstNode + ("id" to JsonPrimitive(REAL_ONLINE_STORE_PUBLICATION_ID)) + ("name" to JsonPrimitive("Online Store")))
        val patchedEdge = JsonObject(firstEdge + ("node" to patchedNode))
        val patchedEdges = JsonArray(listOf(patchedEdge) + edges.drop(1))
        val patchedPublications = JsonObject(publicationsObj + ("edges" to patchedEdges))
        val patchedBody = JsonObject(bodyObj + ("publications" to patchedPublications))
        return copy(response = JsonObject(responseObj + ("body" to patchedBody)))
    }

    private fun takeRequestOrFail(mockServer: MockWebServer, expectedFor: String) =
        requireNotNull(mockServer.takeRequest(10, TimeUnit.SECONDS)) {
            "expected a request for '$expectedFor', none arrived within 10s"
        }

    private fun replayAndAssert(cassetteResource: String, count: Int, publicationsCallIndex: Int?) {
        // given
        val cassette = Cassette.fromClasspathResource(cassetteResource)
        val patchedCalls = cassette.calls.mapIndexed { index, call ->
            when (index) {
                0 -> call.withPatchedProduct("ZZ TEST cobaye — ne pas vendre", count)
                publicationsCallIndex -> call.withPatchedOnlineStorePublicationName()
                else -> call
            }
        }
        val (useCase, mockServer) = useCaseReplaying(patchedCalls)

        // when
        val result = useCase.execute("store-velotrip", resourceIdOf(cassette)).shouldBeRight()

        // then
        takeRequestOrFail(mockServer, "token exchange") shouldNotBe null
        cassette.calls.forEach { call ->
            CassetteEquivalence.assertShopifyAdminGraphQLRequestMatches(call, takeRequestOrFail(mockServer, call.request.toString()))
        }
        mockServer.requestCount shouldBe 1 + cassette.calls.size

        val rendered = McpToolResults.publishResourceResult("velotrip", result)
        rendered.content().map { (it as TextContent).text() } shouldBe expectedTexts(cassette)
    }

    @Test
    fun `replaying the publish-resource never-published cassette, with its over-redacted title, publicationsCount and Online Store publication name fields patched back to their real captured values, reproduces the recorded toolOutput and all five requests`() {
        replayAndAssert("cassettes/publish-resource-velotrip-zz-test-cobaye-never-published.json", count = 0, publicationsCallIndex = 3)
    }

    @Test
    fun `replaying the publish-resource already-published cassette, with its over-redacted title field and its publicationsCount field patched to a nonzero value, reproduces the recorded toolOutput and skips the publish-to-channel calls`() {
        replayAndAssert("cassettes/publish-resource-velotrip-zz-test-cobaye-already-published.json", count = 1, publicationsCallIndex = null)
    }

    private companion object {
        const val REAL_ONLINE_STORE_PUBLICATION_ID = "gid://shopify/Publication/355822338373"
    }
}
