package com.zickat.shopifymcpserver.metaobjects.domain

import arrow.core.right
import com.zickat.shopifymcpserver.metaobjects.exposed_interface.model.MetaobjectFieldInput
import com.zickat.shopifymcpserver.metaobjects.exposed_interface.model.UpdateMetaobjectOutcome
import com.zickat.shopifymcpserver.shopify.ShopifyAdminGatewayFake
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Test

class UpdateMetaobjectUseCaseTest {

    private val json = Json

    @Test
    fun `execute should return NOT_FOUND before any write when the metaobject does not exist`() {
        val gateway = ShopifyAdminGatewayFake().apply {
            enqueue(json.parseToJsonElement("""{"metaobject":null}""").right())
        }
        val useCase = UpdateMetaobjectUseCase(gateway)

        val result = useCase.execute("store-1", "gid://shopify/Metaobject/404", listOf(MetaobjectFieldInput("title", "x"))).shouldBeRight()

        result.outcome shouldBe UpdateMetaobjectOutcome.NOT_FOUND
        result.metaobjectId shouldBe "gid://shopify/Metaobject/404"
        gateway.calls shouldHaveSize 1
    }

    @Test
    fun `execute should update and format the changed fields on success`() {
        val gateway = ShopifyAdminGatewayFake().apply {
            enqueue(json.parseToJsonElement("""{"metaobject":{"id":"gid://shopify/Metaobject/1","type":"faq_item","fields":[]}}""").right())
            enqueue(json.parseToJsonElement("""{"metaobjectDefinitionByType":null}""").right())
            enqueue(
                json.parseToJsonElement(
                    """{"metaobjectUpdate":{"metaobject":{"id":"gid://shopify/Metaobject/1"},"userErrors":[]}}""",
                ).right(),
            )
        }
        val useCase = UpdateMetaobjectUseCase(gateway)

        val result = useCase.execute("store-1", "gid://shopify/Metaobject/1", listOf(MetaobjectFieldInput("question", "updated?"))).shouldBeRight()

        result.outcome shouldBe UpdateMetaobjectOutcome.UPDATED
        requireNotNull(result.text) shouldContain "faq_item"
        requireNotNull(result.text) shouldContain "updated?"
    }
}
