package com.zickat.shopifymcpserver.pages.domain

import arrow.core.right
import com.zickat.shopifymcpserver.pages.exposed_interface.model.PageMetafieldInput
import com.zickat.shopifymcpserver.pages.exposed_interface.model.UpdatePageMetafieldsOutcome
import com.zickat.shopifymcpserver.shopify.ShopifyAdminGatewayFake
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Test

class UpdatePageMetafieldsUseCaseTest {

    private val json = Json

    @Test
    fun `execute should return NOT_FOUND before any write when the Page does not exist`() {
        val gateway = ShopifyAdminGatewayFake().apply {
            enqueue(json.parseToJsonElement("""{"page":null}""").right())
        }
        val useCase = UpdatePageMetafieldsUseCase(gateway)

        val result = useCase.execute(
            "store-1",
            "gid://shopify/Page/404",
            listOf(PageMetafieldInput("themes", "single_line_text_field", "x")),
        ).shouldBeRight()

        result.outcome shouldBe UpdatePageMetafieldsOutcome.NOT_FOUND
        gateway.calls shouldHaveSize 1
    }

    @Test
    fun `execute should report failure when Shopify returns userErrors`() {
        val gateway = ShopifyAdminGatewayFake().apply {
            enqueue(
                json.parseToJsonElement(
                    """{"page":{"id":"gid://shopify/Page/1","title":"T","metafields":{"edges":[],"pageInfo":{"hasNextPage":false}}}}""",
                ).right(),
            )
            enqueue(
                json.parseToJsonElement(
                    """{"metafieldsSet":{"metafields":null,"userErrors":[{"field":["value"],"message":"invalid"}]}}""",
                ).right(),
            )
        }
        val useCase = UpdatePageMetafieldsUseCase(gateway)

        val result = useCase.execute(
            "store-1",
            "gid://shopify/Page/1",
            listOf(PageMetafieldInput("themes", "single_line_text_field", "x")),
        ).shouldBeRight()

        result.outcome shouldBe UpdatePageMetafieldsOutcome.FAILED
    }
}
