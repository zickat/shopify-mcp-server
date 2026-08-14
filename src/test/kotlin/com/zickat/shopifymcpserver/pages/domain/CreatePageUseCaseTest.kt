package com.zickat.shopifymcpserver.pages.domain

import arrow.core.right
import com.zickat.shopifymcpserver.pages.exposed_interface.model.CreatePageOutcome
import com.zickat.shopifymcpserver.shopify.ShopifyAdminGatewayFake
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Test

class CreatePageUseCaseTest {

    private val json = Json

    @Test
    fun `execute should report failure when Shopify returns userErrors`() {
        val gateway = ShopifyAdminGatewayFake().apply {
            enqueue(
                json.parseToJsonElement(
                    """{"pageCreate":{"page":null,"userErrors":[{"field":["title"],"message":"can't be blank"}]}}""",
                ).right(),
            )
        }
        val useCase = CreatePageUseCase(gateway)

        val result = useCase.execute("store-1", "", "<p>x</p>", null, null).shouldBeRight()

        result.outcome shouldBe CreatePageOutcome.FAILED
        requireNotNull(result.failureDetail) shouldContain "can't be blank"
    }

    @Test
    fun `execute should default to a draft when publish is omitted, and report the effective handle`() {
        val gateway = ShopifyAdminGatewayFake().apply {
            enqueue(
                json.parseToJsonElement(
                    """{"pageCreate":{"page":{"id":"gid://shopify/Page/1","title":"T","handle":"t","isPublished":false},"userErrors":[]}}""",
                ).right(),
            )
        }
        val useCase = CreatePageUseCase(gateway)

        val result = useCase.execute("store-1", "T", "<p>x</p>", null, null).shouldBeRight()

        result.outcome shouldBe CreatePageOutcome.CREATED
        requireNotNull(result.text) shouldContain "isPublished: false"
        requireNotNull(result.text) shouldContain "brouillon"
    }
}
