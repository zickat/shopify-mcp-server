package com.zickat.shopifymcpserver.shopify.spi.http

import com.zickat.shopifymcpserver.shared_kernel.DomainError
import com.zickat.shopifymcpserver.shared_kernel.TechnicalError
import io.kotest.assertions.arrow.core.shouldBeLeft
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.web.client.RestClient

class ShopifyRestClientHttpClientTest {

    private val server = MockWebServer().apply { start() }
    private val client = ShopifyRestClientHttpClient(RestClient.builder(), baseUrlFor = { server.baseUrl() })

    @AfterEach
    fun shutdownServer() {
        server.shutdown()
    }

    private fun MockWebServer.baseUrl(): String = url("").toString().removeSuffix("/")

    @Test
    fun `exchangeAccessToken should return the access token and post the client credentials grant to admin oauth access_token`() {
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"access_token":"shpat_test-token"}"""))

        val token = client.exchangeAccessToken("store.myshopify.com", "the-api-key", "the-api-secret").shouldBeRight()

        token.value shouldBe "shpat_test-token"
        val recorded = server.takeRequest()
        recorded.path shouldBe "/admin/oauth/access_token"
        val body = Json.parseToJsonElement(recorded.body.readUtf8()).jsonObject
        body.getValue("client_id") shouldBe JsonPrimitive("the-api-key")
        body.getValue("client_secret") shouldBe JsonPrimitive("the-api-secret")
        body.getValue("grant_type") shouldBe JsonPrimitive("client_credentials")
    }

    @Test
    fun `exchangeAccessToken should fail with a status-only message and no response body when Shopify rejects the grant`() {
        server.enqueue(MockResponse().setResponseCode(401).setBody("""{"errors":"secret-marker-in-response-body"}"""))

        val error = client.exchangeAccessToken("store.myshopify.com", "wrong-key", "wrong-secret").shouldBeLeft()

        val technicalError = error.shouldBeInstanceOf<TechnicalError>()
        technicalError.messageKey shouldBe "shopify.auth.rejected"
        technicalError.parameters shouldBe mapOf("status" to "401")
    }

    @Test
    fun `exchangeAccessToken should fail with a generic message containing neither the store domain nor the port when the connection itself fails`() {
        val unreachableServer = MockWebServer().apply { start() }
        val unreachableBaseUrl = unreachableServer.baseUrl()
        unreachableServer.shutdown()
        val clientPointingAtDeadServer = ShopifyRestClientHttpClient(RestClient.builder(), baseUrlFor = { unreachableBaseUrl })

        val error = clientPointingAtDeadServer.exchangeAccessToken("store.myshopify.com", "key", "secret").shouldBeLeft()

        val technicalError = error.shouldBeInstanceOf<TechnicalError>()
        technicalError.messageKey shouldBe "shopify.auth.network.failed"
        technicalError.parameters shouldBe emptyMap()
        technicalError.messageKey.contains(unreachableBaseUrl) shouldBe false
    }

    @Test
    fun `exchangeAccessToken should fail with a generic message when the response body is not valid JSON`() {
        server.enqueue(MockResponse().setResponseCode(200).setBody("not-json-at-all"))

        val error = client.exchangeAccessToken("store.myshopify.com", "key", "secret").shouldBeLeft()

        error.shouldBeInstanceOf<TechnicalError>().messageKey shouldBe "shopify.auth.response.malformed"
    }

    @Test
    fun `exchangeAccessToken should fail explicitly when the response is valid JSON but carries no access_token`() {
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"scope":"read_products"}"""))

        val error = client.exchangeAccessToken("store.myshopify.com", "key", "secret").shouldBeLeft()

        error.shouldBeInstanceOf<TechnicalError>().messageKey shouldBe "shopify.auth.token.missing"
    }

    @Test
    fun `executeGraphQL should return the data payload and post the query with the access token header to admin api graphql`() {
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"data":{"shop":{"name":"Test Shop"}}}"""))
        val variables = Json.parseToJsonElement("""{"id":"gid://shopify/Shop/1"}""")

        val data = client.executeGraphQL("store.myshopify.com", "shpat_the-token", "query { shop { name } }", variables).shouldBeRight()

        data shouldBe Json.parseToJsonElement("""{"shop":{"name":"Test Shop"}}""")
        val recorded = server.takeRequest()
        recorded.path shouldBe "/admin/api/2025-01/graphql.json"
        recorded.headers["X-Shopify-Access-Token"] shouldBe "shpat_the-token"
        val body = Json.parseToJsonElement(recorded.body.readUtf8()).jsonObject
        body.getValue("query") shouldBe JsonPrimitive("query { shop { name } }")
        body.getValue("variables") shouldBe variables
    }

    @Test
    fun `executeGraphQL should fail with a status-only message and no response body or header when Shopify rejects the call`() {
        server.enqueue(
            MockResponse().setResponseCode(500)
                .setHeader("X-Sensitive-Debug-Info", "secret-marker-in-header")
                .setBody("secret-marker-in-response-body"),
        )

        val error = client.executeGraphQL("store.myshopify.com", "shpat_the-token", "query { shop { name } }", JsonPrimitive("null")).shouldBeLeft()

        val technicalError = error.shouldBeInstanceOf<TechnicalError>()
        technicalError.messageKey shouldBe "shopify.graphql.http.rejected"
        technicalError.parameters shouldBe mapOf("status" to "500")
    }

    @Test
    fun `executeGraphQL should propagate the GraphQL error detail when Shopify refuses the query, unlike an HTTP-level rejection`() {
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"errors":[{"message":"Field 'bogus' doesn't exist on type 'Shop'"}]}"""))

        val error = client.executeGraphQL("store.myshopify.com", "shpat_the-token", "query { shop { bogus } }", JsonPrimitive("null")).shouldBeLeft()

        val domainError = error.shouldBeInstanceOf<DomainError>()
        domainError.messageKey shouldBe "shopify.graphql.refused"
        domainError.parameters?.getValue("detail") shouldBe "Field 'bogus' doesn't exist on type 'Shop'"
    }

    @Test
    fun `executeGraphQL should fail with a generic message when the response body is not valid JSON`() {
        server.enqueue(MockResponse().setResponseCode(200).setBody("not-json-at-all"))

        val error = client.executeGraphQL("store.myshopify.com", "shpat_the-token", "query { shop { name } }", JsonPrimitive("null")).shouldBeLeft()

        error.shouldBeInstanceOf<TechnicalError>().messageKey shouldBe "shopify.graphql.response.malformed"
    }

    @Test
    fun `executeGraphQL should fail explicitly when the response is valid JSON but carries neither data nor errors`() {
        server.enqueue(MockResponse().setResponseCode(200).setBody("{}"))

        val error = client.executeGraphQL("store.myshopify.com", "shpat_the-token", "query { shop { name } }", JsonPrimitive("null")).shouldBeLeft()

        error.shouldBeInstanceOf<TechnicalError>().messageKey shouldBe "shopify.graphql.response.empty"
    }
}
