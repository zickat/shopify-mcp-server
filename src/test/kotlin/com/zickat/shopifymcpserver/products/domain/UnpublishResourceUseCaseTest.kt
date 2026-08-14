package com.zickat.shopifymcpserver.products.domain

import arrow.core.right
import com.zickat.shopifymcpserver.products.exposed_interface.model.UnpublishResourceOutcome
import com.zickat.shopifymcpserver.shopify.ShopifyAdminGatewayFake
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Test

class UnpublishResourceUseCaseTest {

    private val json = Json

    private fun useCase(gateway: ShopifyAdminGatewayFake = ShopifyAdminGatewayFake()) = UnpublishResourceUseCase(gateway)

    @Test
    fun `should return NotFound and emit no further call when the product does not exist`() {
        val gateway = ShopifyAdminGatewayFake().apply {
            enqueue(json.parseToJsonElement("""{"product":null}""").right())
        }

        val result = useCase(gateway).execute("store-1", "gid://shopify/Product/999").shouldBeRight()

        result.outcome shouldBe UnpublishResourceOutcome.NOT_FOUND
        result.resourceId shouldBe "gid://shopify/Product/999"
        gateway.calls.size shouldBe 1
    }

    @Test
    fun `should return a failed outcome when publishableUnpublish rejects the removal`() {
        val gateway = ShopifyAdminGatewayFake().apply {
            enqueue(json.parseToJsonElement("""{"product":{"id":"gid://shopify/Product/1","title":"Item","resourcePublicationsCount":{"count":1}}}""").right())
            enqueue(json.parseToJsonElement("""{"publications":{"edges":[{"node":{"id":"gid://shopify/Publication/1","name":"Online Store"}}]}}""").right())
            enqueue(json.parseToJsonElement("""{"publishableUnpublish":{"publishable":null,"userErrors":[{"field":[],"message":"cannot unpublish"}]}}""").right())
        }

        val result = useCase(gateway).execute("store-1", "gid://shopify/Product/1").shouldBeRight()

        result.outcome shouldBe UnpublishResourceOutcome.FAILED
        result.failureDetail shouldBe "cannot unpublish"
        gateway.calls.size shouldBe 3
    }
}
