package com.zickat.shopifymcpserver.seo.domain

import com.zickat.shopifymcpserver.api.mcp.McpToolResults
import com.zickat.shopifymcpserver.seo.domain.models.SeoResourceType
import com.zickat.shopifymcpserver.seo.spi.shopify.SeoShopifyRepository
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
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.web.client.RestClient
import java.util.concurrent.TimeUnit

class UpdateSeoUseCaseCassetteReplayTest {

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

    private fun CassetteCall.withPatchedNode(
        typename: String,
        title: String,
        currentMetaTitle: String?,
        currentMetaDescription: String?,
    ): CassetteCall {
        val responseObj = response as JsonObject
        val bodyObj = responseObj.getValue("body") as JsonObject
        val nodeObj = bodyObj.getValue("node") as JsonObject
        var patchedFields = nodeObj + ("__typename" to JsonPrimitive(typename)) + ("title" to JsonPrimitive(title))
        (nodeObj["seo"] as? JsonObject)?.let { seoObj ->
            val patchedSeo = seoObj +
                (currentMetaTitle?.let { "title" to JsonPrimitive(it) } ?: ("title" to seoObj.getValue("title"))) +
                (currentMetaDescription?.let { "description" to JsonPrimitive(it) } ?: ("description" to seoObj.getValue("description")))
            patchedFields = patchedFields + ("seo" to JsonObject(patchedSeo))
        }
        (nodeObj["titleTag"] as? JsonObject)?.let { titleTagObj ->
            currentMetaTitle?.let { patchedFields = patchedFields + ("titleTag" to JsonObject(titleTagObj + ("value" to JsonPrimitive(it)))) }
        }
        (nodeObj["descriptionTag"] as? JsonObject)?.let { descriptionTagObj ->
            currentMetaDescription?.let {
                patchedFields = patchedFields + ("descriptionTag" to JsonObject(descriptionTagObj + ("value" to JsonPrimitive(it))))
            }
        }
        val patchedNode = JsonObject(patchedFields)
        val patchedBody = JsonObject(bodyObj + ("node" to patchedNode))
        return copy(response = JsonObject(responseObj + ("body" to patchedBody)))
    }

    private fun useCaseReplaying(calls: List<CassetteCall>): Pair<UpdateSeoUseCase, MockWebServer> {
        val mockServer = CassetteMockWebServer.forShopifyAdminGraphQLWithTokenExchange(calls)
        server = mockServer
        val httpClient = ShopifyRestClientHttpClient(RestClient.builder(), baseUrlFor = { mockServer.url("").toString().removeSuffix("/") })
        val vault = VaultExposedServiceFake().apply {
            plaintextByStoreId["store-velotrip"] =
                """{"apiKey":"key","apiSecret":"secret","shopDomain":"velotrip.myshopify.com"}""".toByteArray()
        }
        val graphQLUseCase = ShopifyAdminGraphQLUseCase(vault, httpClient)
        val gateway = ShopifyAdminGatewayImpl(graphQLUseCase)
        return UpdateSeoUseCase(SeoShopifyRepository(gateway)) to mockServer
    }

    private fun replayAndAssert(
        cassetteResource: String,
        realTitle: String,
        currentMetaTitle: String? = null,
        currentMetaDescription: String? = null,
    ) {
        // given
        val cassette = Cassette.fromClasspathResource(cassetteResource)
        val toolInput = cassette.toolInput.jsonObject
        val resourceType = toolInput.getValue("resource_type").jsonPrimitive.content
        val resourceId = toolInput.getValue("resource_id").jsonPrimitive.content
        val seoTitle = toolInput["seo_title"]?.jsonPrimitive?.content
        val seoDescription = toolInput["seo_description"]?.jsonPrimitive?.content
        val parsedResourceType = requireNotNull(SeoResourceType.fromToolValue(resourceType))
        val patchedCalls = cassette.calls.mapIndexed { index, call ->
            if (index == 0) call.withPatchedNode(parsedResourceType.typename, realTitle, currentMetaTitle, currentMetaDescription) else call
        }
        val (useCase, mockServer) = useCaseReplaying(patchedCalls)

        // when
        val result = useCase.execute("store-velotrip", parsedResourceType, resourceId, seoTitle, seoDescription).shouldBeRight()

        // then
        mockServer.takeRequest(10, TimeUnit.SECONDS) shouldNotBe null
        cassette.calls.forEach { call ->
            val recorded = requireNotNull(mockServer.takeRequest(10, TimeUnit.SECONDS)) {
                "expected a request matching cassette call for query starting with '${call.request}', none arrived within 10s"
            }
            CassetteEquivalence.assertShopifyAdminGraphQLRequestMatches(call, recorded)
        }

        val rendered = McpToolResults.updateSeoResult("velotrip", resourceType, resourceId, result)
        rendered.content().map { (it as TextContent).text() } shouldBe expectedTexts(cassette)
    }

    @Test
    fun `replaying the update-seo native full-write cassette, with its over-redacted __typename and title fields patched back to their real captured values, reproduces the recorded toolOutput, both fields modified`() {
        replayAndAssert("cassettes/update-seo-velotrip-zz-test-cobaye-native-full-write.json", realTitle = "ZZ TEST cobaye — ne pas vendre")
    }

    @Test
    fun `replaying the update-seo native partial-write fusion cassette, with its over-redacted __typename, title and current seo fields patched back to their real captured values, reproduces the recorded toolOutput, the omitted field carried through as unchanged`() {
        replayAndAssert(
            "cassettes/update-seo-velotrip-zz-test-cobaye-native-partial-write-fusion.json",
            realTitle = "ZZ TEST cobaye — ne pas vendre",
            currentMetaTitle = "[test-lot4-cassette] Titre SEO de test",
            currentMetaDescription = "[test-lot4-cassette] Description SEO de test",
        )
    }

    @Test
    fun `replaying the update-seo page metafield full-write cassette, with its over-redacted __typename and title fields patched back to their real captured values, reproduces the recorded toolOutput, a single metafieldsSet call with both keys`() {
        replayAndAssert(
            "cassettes/update-seo-velotrip-test-lot4-cassette-seo-page-metafield-full-write.json",
            realTitle = "[test-lot4-cassette] Page SEO jetable",
        )
    }

    @Test
    fun `replaying the update-seo page metafield partial-write independent cassette, with its over-redacted __typename, title and current metafield value fields patched back to their real captured values, reproduces the recorded toolOutput, a single metafieldsSet call with only the provided key`() {
        replayAndAssert(
            "cassettes/update-seo-velotrip-test-lot4-cassette-seo-page-metafield-partial-write-independent.json",
            realTitle = "[test-lot4-cassette] Page SEO jetable",
            currentMetaTitle = "[test-lot4-cassette] Titre SEO page",
            currentMetaDescription = "[test-lot4-cassette] Description SEO page",
        )
    }
}
