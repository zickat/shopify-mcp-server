package com.zickat.shopifymcpserver.pages.domain

import arrow.core.right
import com.zickat.shopifymcpserver.pages.exposed_interface.model.DeletePageOutcome
import com.zickat.shopifymcpserver.shopify.ShopifyAdminGatewayFake
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Test

class DeletePageUseCaseTest {

    private val json = Json

    @Test
    fun `execute should return NOT_FOUND before any deletion when the Page does not exist`() {
        val gateway = ShopifyAdminGatewayFake().apply {
            enqueue(json.parseToJsonElement("""{"page":null}""").right())
        }
        val useCase = DeletePageUseCase(gateway)

        val result = useCase.execute("store-1", "gid://shopify/Page/404").shouldBeRight()

        result.outcome shouldBe DeletePageOutcome.NOT_FOUND
        gateway.calls shouldHaveSize 1
    }
}
