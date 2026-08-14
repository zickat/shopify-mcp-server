package com.zickat.shopifymcpserver.products.domain

import arrow.core.right
import com.zickat.shopifymcpserver.products.exposed_interface.model.MarkBlockedOutcome
import com.zickat.shopifymcpserver.shopify.ShopifyAdminGatewayFake
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Test

class MarkBlockedUseCaseTest {

    private val json = Json

    private fun useCase(gateway: ShopifyAdminGatewayFake = ShopifyAdminGatewayFake()) = MarkBlockedUseCase(gateway)

    @Test
    fun `should mark the resource blocked with a single setMetafields call carrying the literal 'blocked' value`() {
        val gateway = ShopifyAdminGatewayFake().apply {
            enqueue(json.parseToJsonElement("""{"metafieldsSet":{"metafields":[{"id":"gid://shopify/Metafield/1","key":"content_status"}],"userErrors":[]}}""").right())
        }

        val result = useCase(gateway).execute("store-1", "gid://shopify/Product/1").shouldBeRight()

        result.outcome shouldBe MarkBlockedOutcome.MARKED
        gateway.calls shouldHaveSize 1
        val metafield = gateway.calls.single().variables.jsonObject.getValue("metafields").jsonArray.single().jsonObject
        metafield.getValue("ownerId").jsonPrimitive.content shouldBe "gid://shopify/Product/1"
        metafield.getValue("namespace").jsonPrimitive.content shouldBe "custom"
        metafield.getValue("key").jsonPrimitive.content shouldBe "content_status"
        metafield.getValue("value").jsonPrimitive.content shouldBe "blocked"
    }

    @Test
    fun `should return a failed outcome carrying the formatted Shopify user error when metafieldsSet rejects the write`() {
        val gateway = ShopifyAdminGatewayFake().apply {
            enqueue(
                json.parseToJsonElement(
                    """{"metafieldsSet":{"metafields":[],"userErrors":[{"field":["metafields","0","value"],"message":"is invalid"}]}}""",
                ).right(),
            )
        }

        val result = useCase(gateway).execute("store-1", "gid://shopify/Product/1").shouldBeRight()

        result.outcome shouldBe MarkBlockedOutcome.FAILED
        result.failureDetail shouldBe "metafields.0.value : is invalid"
    }
}
