package com.zickat.shopifymcpserver.seo.domain

import arrow.core.right
import com.zickat.shopifymcpserver.seo.exposed_interface.model.GetSeoResult
import com.zickat.shopifymcpserver.shared_kernel.TechnicalError
import com.zickat.shopifymcpserver.shopify.ShopifyAdminGatewayFake
import io.kotest.assertions.arrow.core.shouldBeLeft
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Test

class SeoUseCaseTest {

    @Test
    fun `getSeo should delegate to GetSeoUseCase once the resource_type string is parsed`() {
        val gateway = ShopifyAdminGatewayFake().apply {
            enqueue(Json.parseToJsonElement("""{"node":{"__typename":"Collection","title":"T","seo":{"title":null,"description":null}}}""").right())
        }
        val service = SeoExposedServiceImpl(GetSeoUseCase(gateway))

        val result = service.getSeo("store-1", "collection", "gid://shopify/Collection/1").shouldBeRight()

        result shouldBe GetSeoResult.found("T", null, null)
    }

    @Test
    fun `getSeo should reject an unknown resource_type before reaching Shopify`() {
        val gateway = ShopifyAdminGatewayFake()
        val service = SeoExposedServiceImpl(GetSeoUseCase(gateway))

        val error = service.getSeo("store-1", "not-a-real-type", "gid://shopify/Product/1").shouldBeLeft()

        error.shouldBeInstanceOf<TechnicalError>()
        error.messageKey shouldBe "seo.resource_type.unexpected"
        gateway.calls shouldBe emptyList()
    }
}
