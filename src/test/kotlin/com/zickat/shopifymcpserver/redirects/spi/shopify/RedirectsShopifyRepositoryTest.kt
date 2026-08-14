package com.zickat.shopifymcpserver.redirects.spi.shopify

import arrow.core.right
import com.zickat.shopifymcpserver.redirects.domain.models.CreateRedirectOutcome
import com.zickat.shopifymcpserver.shopify.ShopifyAdminGatewayFake
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.jupiter.api.Test

class RedirectsShopifyRepositoryTest {

    @Test
    fun `create should return Created and send the path and target as given when Shopify reports no userErrors`() {
        val gateway = ShopifyAdminGatewayFake()
        gateway.default = Json.parseToJsonElement(
            """{"urlRedirectCreate":{"urlRedirect":{"id":"gid://shopify/UrlRedirect/1"},"userErrors":[]}}""",
        ).right()
        val repository = RedirectsShopifyRepository(gateway)

        val outcome = repository.create("store-1", "/collections/old", "/collections/new").shouldBeRight()

        outcome shouldBe CreateRedirectOutcome.Created
        gateway.calls.shouldHaveSizeOne()
        val variables = gateway.calls.single().variables.jsonObject["urlRedirect"]!!.jsonObject
        variables["path"] shouldBe kotlinx.serialization.json.JsonPrimitive("/collections/old")
        variables["target"] shouldBe kotlinx.serialization.json.JsonPrimitive("/collections/new")
    }

    @Test
    fun `create should return AlreadyExists without a second mutation when Shopify reports path already taken`() {
        val gateway = ShopifyAdminGatewayFake()
        gateway.default = Json.parseToJsonElement(
            """{"urlRedirectCreate":{"urlRedirect":null,"userErrors":[{"field":["urlRedirect","path"],"message":"Path has already been taken"}]}}""",
        ).right()
        val repository = RedirectsShopifyRepository(gateway)

        val outcome = repository.create("store-1", "/collections/old", "/collections/new").shouldBeRight()

        outcome shouldBe CreateRedirectOutcome.AlreadyExists
        gateway.calls.shouldHaveSizeOne()
    }

    @Test
    fun `create should match the already-taken message case-insensitively`() {
        val gateway = ShopifyAdminGatewayFake()
        gateway.default = Json.parseToJsonElement(
            """{"urlRedirectCreate":{"urlRedirect":null,"userErrors":[{"field":["urlRedirect","path"],"message":"PATH HAS ALREADY BEEN TAKEN"}]}}""",
        ).right()
        val repository = RedirectsShopifyRepository(gateway)

        val outcome = repository.create("store-1", "/collections/old", "/collections/new").shouldBeRight()

        outcome shouldBe CreateRedirectOutcome.AlreadyExists
    }

    @Test
    fun `create should return Failed with the formatted detail for any other userErrors`() {
        val gateway = ShopifyAdminGatewayFake()
        gateway.default = Json.parseToJsonElement(
            """{"urlRedirectCreate":{"urlRedirect":null,"userErrors":[
                {"field":["urlRedirect","target"],"message":"Target is invalid"},
                {"field":[],"message":"Something else went wrong"}
            ]}}""",
        ).right()
        val repository = RedirectsShopifyRepository(gateway)

        val outcome = repository.create("store-1", "/collections/old", "/not-a-real-path").shouldBeRight()

        outcome shouldBe CreateRedirectOutcome.failed(
            "urlRedirect.target : Target is invalid ; Something else went wrong",
        )
    }

    @Test
    fun `create should return a failed outcome when the response carries no urlRedirectCreate payload`() {
        val gateway = ShopifyAdminGatewayFake()
        gateway.default = Json.parseToJsonElement("""{}""").right()
        val repository = RedirectsShopifyRepository(gateway)

        val outcome = repository.create("store-1", "/collections/old", "/collections/new").shouldBeRight()

        outcome shouldBe CreateRedirectOutcome.failed("Réponse Shopify inattendue pour urlRedirectCreate.")
    }

    @Test
    fun `create should send the exact mutation text the TypeScript tool recorded`() {
        val gateway = ShopifyAdminGatewayFake()
        gateway.default = Json.parseToJsonElement(
            """{"urlRedirectCreate":{"urlRedirect":{"id":"gid://shopify/UrlRedirect/1"},"userErrors":[]}}""",
        ).right()
        val repository = RedirectsShopifyRepository(gateway)

        repository.create("store-1", "/collections/old", "/collections/new")

        gateway.calls.single().query shouldBe RECORDED_MUTATION
    }

    private fun List<ShopifyAdminGatewayFake.RecordedCall>.shouldHaveSizeOne(): List<ShopifyAdminGatewayFake.RecordedCall> {
        size shouldBe 1
        return this
    }

    private companion object {
        val RECORDED_MUTATION =
            "mutation CreateUrlRedirect(\$urlRedirect: UrlRedirectInput!) {\n" +
                "      urlRedirectCreate(urlRedirect: \$urlRedirect) {\n" +
                "        urlRedirect { id }\n" +
                "        userErrors { field message }\n" +
                "      }\n" +
                "    }"
    }
}
