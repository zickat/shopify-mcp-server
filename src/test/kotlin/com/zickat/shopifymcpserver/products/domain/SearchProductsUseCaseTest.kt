package com.zickat.shopifymcpserver.products.domain

import arrow.core.right
import com.zickat.shopifymcpserver.products.exposed_interface.model.ProductStatusFilter
import com.zickat.shopifymcpserver.shopify.ShopifyAdminGatewayFake
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.jsonObject
import org.junit.jupiter.api.Test

class SearchProductsUseCaseTest {

    private val json = Json

    private fun productEdge(id: String, contentStatus: String? = null, summaryPointsJson: String? = null): String =
        """{"node":{"id":"$id","title":"T $id","handle":"h-$id","status":"ACTIVE",""" +
            """"contentStatus":${contentStatus?.let { "{\"value\":\"$it\"}" } ?: "null"},""" +
            """"summaryPoints":${summaryPointsJson?.let { "{\"value\":\"${it.replace("\"", "\\\"")}\"}" } ?: "null"}}}"""

    @Test
    fun `should report no product found when the store is empty`() {
        val gateway = ShopifyAdminGatewayFake().apply {
            enqueue(json.parseToJsonElement("""{"products":{"pageInfo":{"hasNextPage":false,"endCursor":null},"edges":[]}}""").right())
        }
        val useCase = SearchProductsUseCase(gateway)

        val result = useCase.execute("store-1", null, ProductStatusFilter.UNTREATED).shouldBeRight()

        result.text shouldBe "Aucun produit trouvé pour ce filtre."
    }

    @Test
    fun `untreated filter should keep only products with neither content_status nor summary_points — the double guard`() {
        val gateway = ShopifyAdminGatewayFake().apply {
            enqueue(
                json.parseToJsonElement(
                    """{"products":{"pageInfo":{"hasNextPage":false,"endCursor":null},"edges":[
                        ${productEdge("gid://shopify/Product/1")},
                        ${productEdge("gid://shopify/Product/2", contentStatus = "to_review")},
                        ${productEdge("gid://shopify/Product/3", summaryPointsJson = """["already published"]""")}
                    ]}}""",
                ).right(),
            )
        }
        val useCase = SearchProductsUseCase(gateway)

        val result = useCase.execute("store-1", null, ProductStatusFilter.UNTREATED).shouldBeRight()

        result.text shouldContain "1 produit(s) trouvé(s)"
        result.text shouldContain "gid://shopify/Product/1"
        result.text.contains("gid://shopify/Product/2") shouldBe false
        result.text.contains("gid://shopify/Product/3") shouldBe false
    }

    @Test
    fun `all filter should keep every product regardless of status`() {
        val gateway = ShopifyAdminGatewayFake().apply {
            enqueue(
                json.parseToJsonElement(
                    """{"products":{"pageInfo":{"hasNextPage":false,"endCursor":null},"edges":[
                        ${productEdge("gid://shopify/Product/1")},
                        ${productEdge("gid://shopify/Product/2", contentStatus = "blocked")}
                    ]}}""",
                ).right(),
            )
        }
        val useCase = SearchProductsUseCase(gateway)

        val result = useCase.execute("store-1", null, ProductStatusFilter.ALL).shouldBeRight()

        result.text shouldContain "2 produit(s) trouvé(s)"
    }

    @Test
    fun `a product with summary_points but no content_status should display as published, not untreated — BUG-01`() {
        val gateway = ShopifyAdminGatewayFake().apply {
            enqueue(
                json.parseToJsonElement(
                    """{"products":{"pageInfo":{"hasNextPage":false,"endCursor":null},"edges":[
                        ${productEdge("gid://shopify/Product/1", summaryPointsJson = """["a"]""")}
                    ]}}""",
                ).right(),
            )
        }
        val useCase = SearchProductsUseCase(gateway)

        val result = useCase.execute("store-1", null, ProductStatusFilter.ALL).shouldBeRight()

        result.text shouldContain "pipeline: published"
    }

    @Test
    fun `should stop at MAX_SEARCH_PAGES and report the scan as truncated`() {
        val gateway = ShopifyAdminGatewayFake()
        var pageCount = 0
        gateway.nextResponseProvider = { _ ->
            pageCount += 1
            json.parseToJsonElement(
                """{"products":{"pageInfo":{"hasNextPage":true,"endCursor":"cursor-$pageCount"},"edges":[
                    ${productEdge("gid://shopify/Product/$pageCount")}
                ]}}""",
            ).right()
        }
        val useCase = SearchProductsUseCase(gateway)

        val result = useCase.execute("store-1", null, ProductStatusFilter.UNTREATED).shouldBeRight()

        result.text shouldContain "10 produit(s) trouvé(s) (résultats tronqués à 500 — affiner la requête)."
    }

    @Test
    fun `should send a null query variable when the query is blank`() {
        val gateway = ShopifyAdminGatewayFake().apply {
            enqueue(json.parseToJsonElement("""{"products":{"pageInfo":{"hasNextPage":false,"endCursor":null},"edges":[]}}""").right())
        }
        val useCase = SearchProductsUseCase(gateway)

        useCase.execute("store-1", "   ", ProductStatusFilter.ALL).shouldBeRight()

        gateway.calls.single().variables.jsonObject["query"] shouldBe JsonNull
    }
}
