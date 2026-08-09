package com.zickat.shopifymcpserver.metaobjects.domain

import arrow.core.right
import com.zickat.shopifymcpserver.shopify.ShopifyAdminGatewayFake
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Test

class ListMetaobjectsUseCaseTest {

    private val json = Json

    @Test
    fun `execute without a type should list the definitions with their native instance count`() {
        val gateway = ShopifyAdminGatewayFake().apply {
            enqueue(
                json.parseToJsonElement(
                    """{"metaobjectDefinitions":{"pageInfo":{"hasNextPage":false},"edges":[
                        {"node":{"type":"faq_item","name":"FAQ Item","metaobjectsCount":3}},
                        {"node":{"type":"guide_theme","name":"Guide Theme","metaobjectsCount":1}}
                    ]}}""",
                ).right(),
            )
        }
        val useCase = ListMetaobjectsUseCase(gateway)

        val result = useCase.execute("store-1", null).shouldBeRight()

        result.text shouldContain "2 type(s) de metaobject trouvé(s) sur le store :"
        result.text shouldContain "- faq_item (FAQ Item) : 3 instance(s)"
        result.text shouldContain "- guide_theme (Guide Theme) : 1 instance(s)"
        result.text shouldContain "Appeler list_metaobjects(type: \"...\") pour lister les instances d'un type."
    }

    @Test
    fun `execute without a type should report no definition found when the store has none`() {
        val gateway = ShopifyAdminGatewayFake().apply {
            enqueue(json.parseToJsonElement("""{"metaobjectDefinitions":{"pageInfo":{"hasNextPage":false},"edges":[]}}""").right())
        }
        val useCase = ListMetaobjectsUseCase(gateway)

        val result = useCase.execute("store-1", null).shouldBeRight()

        result.text shouldBe "Aucune définition de metaobject trouvée sur le store."
    }

    @Test
    fun `execute without a type should flag a truncated definitions page explicitly`() {
        val gateway = ShopifyAdminGatewayFake().apply {
            enqueue(
                json.parseToJsonElement(
                    """{"metaobjectDefinitions":{"pageInfo":{"hasNextPage":true},"edges":[
                        {"node":{"type":"faq_item","name":"FAQ Item","metaobjectsCount":3}}
                    ]}}""",
                ).right(),
            )
        }
        val useCase = ListMetaobjectsUseCase(gateway)

        val result = useCase.execute("store-1", null).shouldBeRight()

        result.text shouldContain "⚠️ Balayage des définitions tronqué à 50"
    }

    @Test
    fun `execute with a type should report no instance found when the type has none`() {
        val gateway = ShopifyAdminGatewayFake().apply {
            enqueue(
                json.parseToJsonElement(
                    """{"metaobjects":{"pageInfo":{"hasNextPage":false,"endCursor":null},"edges":[]}}""",
                ).right(),
            )
        }
        val useCase = ListMetaobjectsUseCase(gateway)

        val result = useCase.execute("store-1", "faq_item").shouldBeRight()

        result.text shouldBe "Aucun metaobject de type \"faq_item\" trouvé."
    }

    @Test
    fun `execute with a type should list each instance with its fields and fetch its reference status`() {
        val gateway = ShopifyAdminGatewayFake().apply {
            enqueue(
                json.parseToJsonElement(
                    """{"metaobjects":{"pageInfo":{"hasNextPage":false,"endCursor":null},"edges":[
                        {"node":{"id":"gid://shopify/Metaobject/1","fields":[{"key":"title","value":"T"}]}}
                    ]}}""",
                ).right(),
            )
            enqueue(json.parseToJsonElement("""{"metaobject":{"referencedBy":{"edges":[],"pageInfo":{"hasNextPage":false}}}}""").right())
        }
        val useCase = ListMetaobjectsUseCase(gateway)

        val result = useCase.execute("store-1", "faq_item").shouldBeRight()

        result.text shouldContain "1 instance(s) de type \"faq_item\" :"
        result.text shouldContain "gid://shopify/Metaobject/1 — orphelin (aucune référence entrante trouvée)"
        result.text shouldContain "  - title: T"
        gateway.calls.last().variables.jsonObject["id"]?.jsonPrimitive?.content shouldBe "gid://shopify/Metaobject/1"
    }

    @Test
    fun `execute with a type should follow the cursor across pages until hasNextPage is false`() {
        val gateway = ShopifyAdminGatewayFake()
        gateway.nextResponseProvider = { call ->
            when {
                call.query.contains("ListMetaobjectsByType") && call.variables.jsonObject["cursor"] == JsonNull ->
                    json.parseToJsonElement(
                        """{"metaobjects":{"pageInfo":{"hasNextPage":true,"endCursor":"cursor-1"},"edges":[
                            {"node":{"id":"gid://shopify/Metaobject/1","fields":[]}}
                        ]}}""",
                    ).right()
                call.query.contains("ListMetaobjectsByType") ->
                    json.parseToJsonElement(
                        """{"metaobjects":{"pageInfo":{"hasNextPage":false,"endCursor":null},"edges":[
                            {"node":{"id":"gid://shopify/Metaobject/2","fields":[]}}
                        ]}}""",
                    ).right()
                else ->
                    json.parseToJsonElement("""{"metaobject":{"referencedBy":{"edges":[],"pageInfo":{"hasNextPage":false}}}}""").right()
            }
        }
        val useCase = ListMetaobjectsUseCase(gateway)

        val result = useCase.execute("store-1", "faq_item").shouldBeRight()

        result.text shouldContain "2 instance(s) de type \"faq_item\" :"
        result.text shouldContain "gid://shopify/Metaobject/1"
        result.text shouldContain "gid://shopify/Metaobject/2"
        result.text.contains("⚠️ Balayage tronqué") shouldBe false
    }

    @Test
    fun `execute with a type should stop at MAX_SEARCH_PAGES and report the scan as truncated`() {
        val gateway = ShopifyAdminGatewayFake()
        var pageCount = 0
        gateway.nextResponseProvider = { call ->
            if (call.query.contains("ListMetaobjectsByType")) {
                pageCount += 1
                json.parseToJsonElement(
                    """{"metaobjects":{"pageInfo":{"hasNextPage":true,"endCursor":"cursor-$pageCount"},"edges":[
                        {"node":{"id":"gid://shopify/Metaobject/$pageCount","fields":[]}}
                    ]}}""",
                ).right()
            } else {
                json.parseToJsonElement("""{"metaobject":{"referencedBy":{"edges":[],"pageInfo":{"hasNextPage":false}}}}""").right()
            }
        }
        val useCase = ListMetaobjectsUseCase(gateway)

        val result = useCase.execute("store-1", "faq_item").shouldBeRight()

        result.text shouldContain "10 instance(s) de type \"faq_item\" :"
        result.text shouldContain "⚠️ Balayage tronqué à 500 instances"
    }

    @Test
    fun `execute with a type should render a null field value as vide`() {
        val gateway = ShopifyAdminGatewayFake().apply {
            enqueue(
                json.parseToJsonElement(
                    """{"metaobjects":{"pageInfo":{"hasNextPage":false,"endCursor":null},"edges":[
                        {"node":{"id":"gid://shopify/Metaobject/1","fields":[{"key":"articles","value":null}]}}
                    ]}}""",
                ).right(),
            )
            enqueue(json.parseToJsonElement("""{"metaobject":{"referencedBy":{"edges":[],"pageInfo":{"hasNextPage":false}}}}""").right())
        }
        val useCase = ListMetaobjectsUseCase(gateway)

        val result = useCase.execute("store-1", "faq_item").shouldBeRight()

        result.text shouldContain "  - articles: (vide)"
    }
}
