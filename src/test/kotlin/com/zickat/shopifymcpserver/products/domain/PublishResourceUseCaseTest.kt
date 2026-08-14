package com.zickat.shopifymcpserver.products.domain

import arrow.core.right
import com.zickat.shopifymcpserver.products.exposed_interface.model.PublishResourceOutcome
import com.zickat.shopifymcpserver.shopify.ShopifyAdminGatewayFake
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Test

class PublishResourceUseCaseTest {

    private val json = Json

    private fun useCase(gateway: ShopifyAdminGatewayFake = ShopifyAdminGatewayFake()) = PublishResourceUseCase(gateway)

    @Test
    fun `should return NotFound and emit no further call when the product does not exist`() {
        val gateway = ShopifyAdminGatewayFake().apply {
            enqueue(json.parseToJsonElement("""{"product":null}""").right())
        }

        val result = useCase(gateway).execute("store-1", "gid://shopify/Product/999").shouldBeRight()

        result.outcome shouldBe PublishResourceOutcome.NOT_FOUND
        result.resourceId shouldBe "gid://shopify/Product/999"
        gateway.calls shouldHaveSizeOf 1
    }

    @Test
    fun `should return a failed outcome and emit no further call when productUpdate rejects the activation`() {
        val gateway = ShopifyAdminGatewayFake().apply {
            enqueue(json.parseToJsonElement("""{"product":{"id":"gid://shopify/Product/1","title":"Item","status":"DRAFT","resourcePublicationsCount":{"count":0}}}""").right())
            enqueue(json.parseToJsonElement("""{"productUpdate":{"product":null,"userErrors":[{"field":["status"],"message":"cannot activate"}]}}""").right())
        }

        val result = useCase(gateway).execute("store-1", "gid://shopify/Product/1").shouldBeRight()

        result.outcome shouldBe PublishResourceOutcome.FAILED
        result.failureDetail shouldBe "status : cannot activate"
        gateway.calls shouldHaveSizeOf 2
    }

    @Test
    fun `should skip the publications lookup and publishablePublish call when the product is already published on a channel`() {
        val gateway = ShopifyAdminGatewayFake().apply {
            enqueue(json.parseToJsonElement("""{"product":{"id":"gid://shopify/Product/1","title":"Item","status":"ACTIVE","resourcePublicationsCount":{"count":2}}}""").right())
            enqueue(json.parseToJsonElement("""{"productUpdate":{"product":{"id":"gid://shopify/Product/1"},"userErrors":[]}}""").right())
            enqueue(json.parseToJsonElement("""{"metafieldsDelete":{"deletedMetafields":[null],"userErrors":[]}}""").right())
        }

        val result = useCase(gateway).execute("store-1", "gid://shopify/Product/1").shouldBeRight()

        result.outcome shouldBe PublishResourceOutcome.PUBLISHED
        result.wasNeverPublished shouldBe false
        gateway.calls shouldHaveSizeOf 3
    }

    private infix fun List<*>.shouldHaveSizeOf(expected: Int) {
        size shouldBe expected
    }
}
