package com.zickat.shopifymcpserver.shared_kernel

import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Test

class RealCassetteReplayTest {

    private val responseObservedLiveAgainstVelotrip = Json.parseToJsonElement(
        """{"data":{"shop":{"name":"Velotrip.fr"}}}""",
    )

    @Test
    fun `replaying the real check_shopify_connection cassette reproduces the response observed live against Velotrip, bit for bit`() {
        // given
        val cassette = Cassette.fromClasspathResource("cassettes/check-shopify-connection.json")
        val server = CassetteMockWebServer.forShopifyAdminGraphQL(cassette)
        val call = cassette.calls.single()
        val request = Json.decodeFromJsonElement(ShopifyAdminGraphQLRequest.serializer(), call.request)

        // when
        val response = CassetteMockWebServer.postGraphQL(server, request.query, request.variables)

        // then
        response.code shouldBe 200
        Json.parseToJsonElement(response.body!!.string()) shouldBe responseObservedLiveAgainstVelotrip
        server.shutdown()
    }
}
