package com.zickat.shopifymcpserver.shared_kernel

import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.Json
import okhttp3.mockwebserver.RecordedRequest

object CassetteEquivalence {

    private val json = Json { ignoreUnknownKeys = true }

    fun assertShopifyAdminGraphQLRequestMatches(cassetteCall: CassetteCall, recordedRequest: RecordedRequest) {
        require(cassetteCall.sink == SINK_SHOPIFY_ADMIN_GRAPHQL) {
            "cannot compare a $SINK_SHOPIFY_ADMIN_GRAPHQL request against a cassette call recorded for sink '${cassetteCall.sink}'"
        }

        val expected = json.decodeFromJsonElement(ShopifyAdminGraphQLRequest.serializer(), cassetteCall.request)
        val actual = json.decodeFromString(ShopifyAdminGraphQLRequest.serializer(), recordedRequest.body.readUtf8())

        actual shouldBe expected
    }
}
