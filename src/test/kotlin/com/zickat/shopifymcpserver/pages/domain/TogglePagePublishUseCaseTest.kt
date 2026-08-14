package com.zickat.shopifymcpserver.pages.domain

import arrow.core.right
import com.zickat.shopifymcpserver.pages.exposed_interface.model.TogglePagePublishOutcome
import com.zickat.shopifymcpserver.shopify.ShopifyAdminGatewayFake
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Test

class TogglePagePublishUseCaseTest {

    private val json = Json

    @Test
    fun `execute should return NOT_FOUND before any write when the Page does not exist`() {
        val gateway = ShopifyAdminGatewayFake().apply {
            enqueue(json.parseToJsonElement("""{"page":null}""").right())
        }
        val useCase = TogglePagePublishUseCase(gateway)

        val result = useCase.execute("store-1", "gid://shopify/Page/404", target = true).shouldBeRight()

        result.outcome shouldBe TogglePagePublishOutcome.NOT_FOUND
        gateway.calls shouldHaveSize 1
    }

    @Test
    fun `execute should be a no-op and emit no mutation when the Page is already in the target state`() {
        val gateway = ShopifyAdminGatewayFake().apply {
            enqueue(
                json.parseToJsonElement(
                    """{"page":{"id":"gid://shopify/Page/1","title":"T","handle":"t","isPublished":true,"body":"<p></p>"}}""",
                ).right(),
            )
        }
        val useCase = TogglePagePublishUseCase(gateway)

        val result = useCase.execute("store-1", "gid://shopify/Page/1", target = true).shouldBeRight()

        result.outcome shouldBe TogglePagePublishOutcome.NO_OP
        gateway.calls shouldHaveSize 1
    }
}
