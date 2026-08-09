package com.zickat.shopifymcpserver.pages.domain

import arrow.core.right
import com.zickat.shopifymcpserver.shopify.ShopifyAdminGatewayFake
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.jsonObject
import org.junit.jupiter.api.Test

class ListPagesUseCaseTest {

    private val json = Json

    @Test
    fun `execute without query should report no page found on an empty store`() {
        val gateway = ShopifyAdminGatewayFake().apply {
            enqueue(json.parseToJsonElement("""{"pages":{"pageInfo":{"hasNextPage":false,"endCursor":null},"edges":[]}}""").right())
        }
        val useCase = ListPagesUseCase(gateway)

        val result = useCase.execute("store-1", null).shouldBeRight()

        result.text shouldBe "Aucune page trouvée sur le store."
    }

    @Test
    fun `execute with a query should report no page found for that filter`() {
        val gateway = ShopifyAdminGatewayFake().apply {
            enqueue(json.parseToJsonElement("""{"pages":{"pageInfo":{"hasNextPage":false,"endCursor":null},"edges":[]}}""").right())
        }
        val useCase = ListPagesUseCase(gateway)

        val result = useCase.execute("store-1", "title:*nope*").shouldBeRight()

        result.text shouldBe "Aucune page trouvée pour ce filtre."
    }

    @Test
    fun `execute without query should list every page found`() {
        val gateway = ShopifyAdminGatewayFake().apply {
            enqueue(
                json.parseToJsonElement(
                    """{"pages":{"pageInfo":{"hasNextPage":false,"endCursor":null},"edges":[
                        {"node":{"id":"gid://shopify/Page/1","title":"Contact","handle":"contact"}}
                    ]}}""",
                ).right(),
            )
        }
        val useCase = ListPagesUseCase(gateway)

        val result = useCase.execute("store-1", null).shouldBeRight()

        result.text shouldBe "1 page(s) trouvée(s).\n\n- Contact (contact) — id: gid://shopify/Page/1"
    }

    @Test
    fun `execute should send a null query variable when the query is blank`() {
        val gateway = ShopifyAdminGatewayFake().apply {
            enqueue(json.parseToJsonElement("""{"pages":{"pageInfo":{"hasNextPage":false,"endCursor":null},"edges":[]}}""").right())
        }
        val useCase = ListPagesUseCase(gateway)

        useCase.execute("store-1", "   ").shouldBeRight()

        gateway.calls.single().variables.jsonObject["query"] shouldBe JsonNull
    }

    @Test
    fun `execute with a type should follow the cursor across pages until hasNextPage is false`() {
        val gateway = ShopifyAdminGatewayFake()
        gateway.nextResponseProvider = { call ->
            when {
                call.variables.jsonObject["cursor"] == JsonNull ->
                    json.parseToJsonElement(
                        """{"pages":{"pageInfo":{"hasNextPage":true,"endCursor":"cursor-1"},"edges":[
                            {"node":{"id":"gid://shopify/Page/1","title":"Contact","handle":"contact"}}
                        ]}}""",
                    ).right()
                else ->
                    json.parseToJsonElement(
                        """{"pages":{"pageInfo":{"hasNextPage":false,"endCursor":null},"edges":[
                            {"node":{"id":"gid://shopify/Page/2","title":"Guides","handle":"guides"}}
                        ]}}""",
                    ).right()
            }
        }
        val useCase = ListPagesUseCase(gateway)

        val result = useCase.execute("store-1", null).shouldBeRight()

        result.text shouldContain "2 page(s) trouvée(s)."
        result.text shouldContain "Contact"
        result.text shouldContain "Guides"
        result.text.contains("tronqués") shouldBe false
    }

    @Test
    fun `execute should stop at MAX_SEARCH_PAGES and report the scan as truncated`() {
        val gateway = ShopifyAdminGatewayFake()
        var pageCount = 0
        gateway.nextResponseProvider = { _ ->
            pageCount += 1
            json.parseToJsonElement(
                """{"pages":{"pageInfo":{"hasNextPage":true,"endCursor":"cursor-$pageCount"},"edges":[
                    {"node":{"id":"gid://shopify/Page/$pageCount","title":"Page $pageCount","handle":"page-$pageCount"}}
                ]}}""",
            ).right()
        }
        val useCase = ListPagesUseCase(gateway)

        val result = useCase.execute("store-1", null).shouldBeRight()

        result.text shouldContain "10 page(s) trouvée(s) (résultats tronqués à 500 — affiner la requête)."
    }
}
