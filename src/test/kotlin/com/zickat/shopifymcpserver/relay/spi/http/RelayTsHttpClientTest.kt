package com.zickat.shopifymcpserver.relay.spi.http

import com.zickat.shopifymcpserver.relay.RelayProperties
import com.zickat.shopifymcpserver.relay.domain.repositories.RelayToolInvocationRequest
import com.zickat.shopifymcpserver.shared_kernel.TechnicalError
import io.kotest.assertions.arrow.core.shouldBeLeft
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.web.client.RestClient

class RelayTsHttpClientTest {

    private val server = MockWebServer().apply { start() }

    private fun clientFor(server: MockWebServer): RelayTsHttpClient =
        RelayTsHttpClient(RestClient.builder(), RelayProperties(ts = RelayProperties.Ts(baseUrl = server.url("").toString().trimEnd('/'))))

    @AfterEach
    fun shutdown() {
        server.shutdown()
    }

    @Test
    fun `invoke posts toolName, toolInput, storeId and role to the relay invoke endpoint`() {
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"content":[{"type":"text","text":"ok"}],"isError":false}"""))
        val client = clientFor(server)

        client.invoke(RelayToolInvocationRequest("check_shopify_connection", JsonPrimitive("null"), "store-1", "OPERATOR")).shouldBeRight()

        val recorded = server.takeRequest()
        recorded.path shouldBe "/relay/invoke"
        recorded.method shouldBe "POST"
        val body = Json.parseToJsonElement(recorded.body.readUtf8()).toString()
        body shouldBe """{"toolName":"check_shopify_connection","toolInput":"null","storeId":"store-1","role":"OPERATOR"}"""
    }

    @Test
    fun `invoke parses a successful text response into a RelayToolOutcome`() {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """{"content":[{"type":"text","text":"Boutique : velotrip"},{"type":"text","text":"Connexion Shopify OK"}],"isError":false}""",
            ),
        )
        val client = clientFor(server)

        val outcome = client.invoke(RelayToolInvocationRequest("check_shopify_connection", JsonPrimitive("null"), "store-1", "OPERATOR")).shouldBeRight()

        outcome.isError shouldBe false
        outcome.content.map { it.text } shouldBe listOf("Boutique : velotrip", "Connexion Shopify OK")
    }

    @Test
    fun `a non-2xx response from the TS process is a technical error naming the status, never a raw exception`() {
        server.enqueue(MockResponse().setResponseCode(503))
        val client = clientFor(server)

        val error = client.invoke(RelayToolInvocationRequest("check_shopify_connection", JsonPrimitive("null"), "store-1", "OPERATOR")).shouldBeLeft()

        error.shouldBeInstanceOf<TechnicalError>()
        (error as TechnicalError).messageKey shouldBe "relay.ts.http.rejected"
        error.parameters?.get("status") shouldBe "503"
    }

    @Test
    fun `a malformed JSON response from the TS process is a technical error, never an unhandled exception`() {
        server.enqueue(MockResponse().setResponseCode(200).setBody("not json at all"))
        val client = clientFor(server)

        val error = client.invoke(RelayToolInvocationRequest("check_shopify_connection", JsonPrimitive("null"), "store-1", "OPERATOR")).shouldBeLeft()

        error.shouldBeInstanceOf<TechnicalError>().messageKey shouldBe "relay.ts.response.malformed"
    }

    @Test
    fun `a network failure reaching the TS process is a technical error, never a raw exception`() {
        server.shutdown()
        val client = clientFor(server)

        val error = client.invoke(RelayToolInvocationRequest("check_shopify_connection", JsonPrimitive("null"), "store-1", "OPERATOR")).shouldBeLeft()

        error.shouldBeInstanceOf<TechnicalError>().messageKey shouldBe "relay.ts.network.failed"
    }
}
