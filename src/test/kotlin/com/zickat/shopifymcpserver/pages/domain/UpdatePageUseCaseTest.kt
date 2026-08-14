package com.zickat.shopifymcpserver.pages.domain

import arrow.core.right
import com.zickat.shopifymcpserver.pages.exposed_interface.model.UpdatePageOutcome
import com.zickat.shopifymcpserver.shopify.ShopifyAdminGatewayFake
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Test

class UpdatePageUseCaseTest {

    private val json = Json

    @Test
    fun `execute should report NO_OP and never call Shopify when every field is omitted`() {
        val gateway = ShopifyAdminGatewayFake()
        val useCase = UpdatePageUseCase(gateway)

        val result = useCase.execute("store-1", "gid://shopify/Page/1", null, null, null).shouldBeRight()

        result.outcome shouldBe UpdatePageOutcome.NO_OP
        gateway.calls.shouldHaveSize(0)
    }

    @Test
    fun `execute should return NOT_FOUND before any write when the Page does not exist`() {
        val gateway = ShopifyAdminGatewayFake().apply {
            enqueue(json.parseToJsonElement("""{"page":null}""").right())
        }
        val useCase = UpdatePageUseCase(gateway)

        val result = useCase.execute("store-1", "gid://shopify/Page/404", "New title", null, null).shouldBeRight()

        result.outcome shouldBe UpdatePageOutcome.NOT_FOUND
        gateway.calls shouldHaveSize 1
    }
}
