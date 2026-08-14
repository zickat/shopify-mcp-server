package com.zickat.shopifymcpserver.shopify.exposed_interface

import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import org.junit.jupiter.api.Test

class ShopifyUserErrorsTest {

    private val json = Json { ignoreUnknownKeys = true }

    private fun payload(text: String): JsonObject = json.parseToJsonElement(text) as JsonObject

    @Test
    fun `should parse an empty userErrors array as an empty list`() {
        val errors = ShopifyUserErrors.parse(payload("""{"userErrors":[]}"""))

        errors.shouldBeEmpty()
    }

    @Test
    fun `should parse a null payload as an empty list`() {
        val errors = ShopifyUserErrors.parse(null)

        errors.shouldBeEmpty()
    }

    @Test
    fun `should parse field and message out of each userErrors entry`() {
        val errors = ShopifyUserErrors.parse(payload("""{"userErrors":[{"field":["metafields","0","value"],"message":"is invalid"}]}"""))

        errors shouldBe listOf(ShopifyUserError(listOf("metafields", "0", "value"), "is invalid"))
    }

    @Test
    fun `should format an error carrying a field path with the field joined by dots before the message`() {
        val formatted = ShopifyUserErrors.format(listOf(ShopifyUserError(listOf("metafields", "0", "value"), "is invalid")))

        formatted shouldBe "metafields.0.value : is invalid"
    }

    @Test
    fun `should format an error carrying no field as the bare message`() {
        val formatted = ShopifyUserErrors.format(listOf(ShopifyUserError(emptyList(), "cannot unpublish")))

        formatted shouldBe "cannot unpublish"
    }

    @Test
    fun `should join multiple errors with a semicolon`() {
        val formatted = ShopifyUserErrors.format(
            listOf(
                ShopifyUserError(listOf("title"), "is too long"),
                ShopifyUserError(emptyList(), "unknown failure"),
            ),
        )

        formatted shouldBe "title : is too long ; unknown failure"
    }
}
