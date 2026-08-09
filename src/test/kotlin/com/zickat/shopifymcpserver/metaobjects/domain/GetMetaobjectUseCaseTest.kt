package com.zickat.shopifymcpserver.metaobjects.domain

import arrow.core.right
import com.zickat.shopifymcpserver.metaobjects.exposed_interface.model.GetMetaobjectOutcome
import com.zickat.shopifymcpserver.shopify.ShopifyAdminGatewayFake
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Test

class GetMetaobjectUseCaseTest {

    private val json = Json

    private fun useCase(gateway: ShopifyAdminGatewayFake = ShopifyAdminGatewayFake()) = GetMetaobjectUseCase(gateway)

    @Test
    fun `should return NotFound with the requested id when the metaobject does not exist`() {
        val gateway = ShopifyAdminGatewayFake().apply {
            enqueue(json.parseToJsonElement("""{"metaobject":null}""").right())
        }

        val result = useCase(gateway).execute("store-1", "gid://shopify/Metaobject/999").shouldBeRight()

        result.outcome shouldBe GetMetaobjectOutcome.NOT_FOUND
        result.metaobjectId shouldBe "gid://shopify/Metaobject/999"
        gateway.calls shouldHaveSize 1
    }

    @Test
    fun `should return Found rendering the type, reference status and fields when the metaobject exists`() {
        val gateway = ShopifyAdminGatewayFake().apply {
            enqueue(
                json.parseToJsonElement(
                    """{"metaobject":{"id":"gid://shopify/Metaobject/1","type":"guide_theme",
                        "fields":[{"key":"title","value":"Leurres"}]}}""",
                ).right(),
            )
            enqueue(
                json.parseToJsonElement(
                    """{"metaobject":{"referencedBy":{"edges":[
                        {"node":{"key":"guide_theme","namespace":"custom","referencer":
                            {"__typename":"Article","id":"gid://shopify/Article/1","title":"Guide"}}}
                    ],"pageInfo":{"hasNextPage":false}}}}""",
                ).right(),
            )
        }

        val result = useCase(gateway).execute("store-1", "gid://shopify/Metaobject/1").shouldBeRight()

        result.outcome shouldBe GetMetaobjectOutcome.FOUND
        result.text shouldContain "gid://shopify/Metaobject/1 — type: guide_theme"
        result.text shouldContain "référencé par : Article \"Guide\" (gid://shopify/Article/1) via custom.guide_theme"
        result.text shouldContain "  - title: Leurres"
    }

    @Test
    fun `should report the metaobject as orphan when referencedBy carries no edge`() {
        val gateway = ShopifyAdminGatewayFake().apply {
            enqueue(
                json.parseToJsonElement(
                    """{"metaobject":{"id":"gid://shopify/Metaobject/1","type":"guide_theme","fields":[]}}""",
                ).right(),
            )
            enqueue(json.parseToJsonElement("""{"metaobject":{"referencedBy":{"edges":[],"pageInfo":{"hasNextPage":false}}}}""").right())
        }

        val result = useCase(gateway).execute("store-1", "gid://shopify/Metaobject/1").shouldBeRight()

        result.text shouldContain "orphelin (aucune référence entrante trouvée)"
    }

    @Test
    fun `should emit the id variable to both the metaobject read and the references read`() {
        val gateway = ShopifyAdminGatewayFake().apply {
            enqueue(
                json.parseToJsonElement(
                    """{"metaobject":{"id":"gid://shopify/Metaobject/42","type":"faq_item","fields":[]}}""",
                ).right(),
            )
            enqueue(json.parseToJsonElement("""{"metaobject":{"referencedBy":{"edges":[],"pageInfo":{"hasNextPage":false}}}}""").right())
        }

        useCase(gateway).execute("store-1", "gid://shopify/Metaobject/42").shouldBeRight()

        gateway.calls shouldHaveSize 2
        gateway.calls[0].variables.jsonObject["id"]?.jsonPrimitive?.content shouldBe "gid://shopify/Metaobject/42"
        gateway.calls[1].variables.jsonObject["id"]?.jsonPrimitive?.content shouldBe "gid://shopify/Metaobject/42"
    }
}
