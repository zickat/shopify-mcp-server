package com.zickat.shopifymcpserver.metaobjects.domain

import arrow.core.right
import com.zickat.shopifymcpserver.metaobjects.exposed_interface.model.CreateMetaobjectOutcome
import com.zickat.shopifymcpserver.metaobjects.exposed_interface.model.MetaobjectFieldInput
import com.zickat.shopifymcpserver.shopify.ShopifyAdminGatewayFake
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Test

class CreateMetaobjectUseCaseTest {

    private val json = Json

    @Test
    fun `execute should report failure and never a created outcome when Shopify returns userErrors`() {
        val gateway = ShopifyAdminGatewayFake().apply {
            enqueue(json.parseToJsonElement("""{"metaobjectDefinitionByType":null}""").right())
            enqueue(
                json.parseToJsonElement(
                    """{"metaobjectCreate":{"metaobject":null,"userErrors":[{"field":["type"],"message":"Type does not exist"}]}}""",
                ).right(),
            )
        }
        val useCase = CreateMetaobjectUseCase(gateway)

        val result = useCase.execute("store-1", "unknown_type", listOf(MetaobjectFieldInput("title", "x"))).shouldBeRight()

        result.outcome shouldBe CreateMetaobjectOutcome.FAILED
        requireNotNull(result.failureDetail) shouldContain "Type does not exist"
    }

    @Test
    fun `execute should report the created metaobject id and type on success`() {
        val gateway = ShopifyAdminGatewayFake().apply {
            enqueue(json.parseToJsonElement("""{"metaobjectDefinitionByType":null}""").right())
            enqueue(
                json.parseToJsonElement(
                    """{"metaobjectCreate":{"metaobject":{"id":"gid://shopify/Metaobject/1"},"userErrors":[]}}""",
                ).right(),
            )
        }
        val useCase = CreateMetaobjectUseCase(gateway)

        val result = useCase.execute("store-1", "faq_item", listOf(MetaobjectFieldInput("question", "?"))).shouldBeRight()

        result.outcome shouldBe CreateMetaobjectOutcome.CREATED
        requireNotNull(result.text) shouldContain "gid://shopify/Metaobject/1"
        requireNotNull(result.text) shouldContain "faq_item"
    }
}
