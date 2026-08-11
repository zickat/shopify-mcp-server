package com.zickat.shopifymcpserver.products.domain

import arrow.core.right
import com.zickat.shopifymcpserver.shopify.ShopifyAdminGatewayFake
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Test

class ListOrphanProductsUseCaseTest {

    private val json = Json

    private val emptyCollectionsScan =
        """{"collections":{"pageInfo":{"hasNextPage":false,"endCursor":null},"edges":[]}}"""

    @Test
    fun `should report no orphan when every product belongs to at least one collection`() {
        val gateway = ShopifyAdminGatewayFake()
        gateway.nextResponseProvider = { call ->
            when {
                call.query.contains("OrphanScanCollections") ->
                    json.parseToJsonElement(
                        """{"collections":{"pageInfo":{"hasNextPage":false,"endCursor":null},"edges":[
                            {"node":{"id":"gid://shopify/Collection/1","title":"C","products":{"pageInfo":{"hasNextPage":false},"nodes":[{"id":"gid://shopify/Product/1"}]}}}
                        ]}}""",
                    ).right()
                else ->
                    json.parseToJsonElement(
                        """{"products":{"pageInfo":{"hasNextPage":false,"endCursor":null},"edges":[
                            {"node":{"id":"gid://shopify/Product/1","title":"P","handle":"p","status":"ACTIVE","contentStatus":null,"summaryPoints":null}}
                        ]}}""",
                    ).right()
            }
        }
        val useCase = ListOrphanProductsUseCase(gateway)

        val result = useCase.execute("store-1").shouldBeRight()

        result.text shouldBe "Aucun produit orphelin actuellement (tout produit ACTIVE/DRAFT appartient à au moins " +
            "une collection)."
    }

    @Test
    fun `should list a product absent from every collection, excluding ARCHIVED`() {
        val gateway = ShopifyAdminGatewayFake()
        gateway.nextResponseProvider = { call ->
            when {
                call.query.contains("OrphanScanCollections") -> json.parseToJsonElement(emptyCollectionsScan).right()
                else ->
                    json.parseToJsonElement(
                        """{"products":{"pageInfo":{"hasNextPage":false,"endCursor":null},"edges":[
                            {"node":{"id":"gid://shopify/Product/1","title":"Orphan","handle":"orphan","status":"DRAFT","contentStatus":null,"summaryPoints":null}},
                            {"node":{"id":"gid://shopify/Product/2","title":"Archived","handle":"archived","status":"ARCHIVED","contentStatus":null,"summaryPoints":null}}
                        ]}}""",
                    ).right()
            }
        }
        val useCase = ListOrphanProductsUseCase(gateway)

        val result = useCase.execute("store-1").shouldBeRight()

        result.text shouldContain "1 produit(s) orphelin(s)"
        result.text shouldContain "gid://shopify/Product/1"
        result.text.contains("gid://shopify/Product/2") shouldBe false
    }

    @Test
    fun `should signal a collection whose product scan is itself truncated at 250, distinctly from the collection-page truncation`() {
        val gateway = ShopifyAdminGatewayFake()
        gateway.nextResponseProvider = { call ->
            when {
                call.query.contains("OrphanScanCollections") ->
                    json.parseToJsonElement(
                        """{"collections":{"pageInfo":{"hasNextPage":false,"endCursor":null},"edges":[
                            {"node":{"id":"gid://shopify/Collection/1","title":"Big collection","products":{"pageInfo":{"hasNextPage":true},"nodes":[]}}}
                        ]}}""",
                    ).right()
                else -> json.parseToJsonElement(
                    """{"products":{"pageInfo":{"hasNextPage":false,"endCursor":null},"edges":[
                        {"node":{"id":"gid://shopify/Product/1","title":"Orphan","handle":"orphan","status":"ACTIVE","contentStatus":null,"summaryPoints":null}}
                    ]}}""",
                ).right()
            }
        }
        val useCase = ListOrphanProductsUseCase(gateway)

        val result = useCase.execute("store-1").shouldBeRight()

        result.text shouldContain "dépassent 250 produits"
        result.text shouldContain "Big collection"
        result.text.contains("Balayage des collections tronqué") shouldBe false
    }
}
