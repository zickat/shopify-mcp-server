package com.zickat.shopifymcpserver.products.spi.shopify

import arrow.core.right
import com.zickat.shopifymcpserver.products.domain.models.ToReviewResourceType
import com.zickat.shopifymcpserver.products.domain.repositories.ProductMetafieldWriteOutcome
import com.zickat.shopifymcpserver.products.domain.repositories.ProductPublishOutcome
import com.zickat.shopifymcpserver.products.domain.repositories.ProductUnpublishOutcome
import com.zickat.shopifymcpserver.shopify.ShopifyAdminGatewayFake
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class ProductShopifyRepositoryTest {

    private val json = Json

    private fun productEdge(id: String, contentStatus: String? = null, summaryPointsJson: String? = null): String =
        """{"node":{"id":"$id","title":"T $id","handle":"h-$id","status":"ACTIVE",""" +
            """"contentStatus":${contentStatus?.let { "{\"value\":\"$it\"}" } ?: "null"},""" +
            """"summaryPoints":${summaryPointsJson?.let { "{\"value\":\"${it.replace("\"", "\\\"")}\"}" } ?: "null"}}}"""

    @Nested
    inner class SearchTest {

        @Test
        fun `should return an empty scan when the store has no product`() {
            val gateway = ShopifyAdminGatewayFake().apply {
                enqueue(json.parseToJsonElement("""{"products":{"pageInfo":{"hasNextPage":false,"endCursor":null},"edges":[]}}""").right())
            }

            val scan = ProductShopifyRepository(gateway).search("store-1", null).shouldBeRight()

            scan.entries shouldBe emptyList()
            scan.truncated shouldBe false
        }

        @Test
        fun `should return every raw fact of every fetched page, undecided about status filtering`() {
            val gateway = ShopifyAdminGatewayFake().apply {
                enqueue(
                    json.parseToJsonElement(
                        """{"products":{"pageInfo":{"hasNextPage":false,"endCursor":null},"edges":[
                            ${productEdge("gid://shopify/Product/1")},
                            ${productEdge("gid://shopify/Product/2", contentStatus = "to_review")}
                        ]}}""",
                    ).right(),
                )
            }

            val scan = ProductShopifyRepository(gateway).search("store-1", null).shouldBeRight()

            scan.entries.map { it.id } shouldBe listOf("gid://shopify/Product/1", "gid://shopify/Product/2")
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

            val scan = ProductShopifyRepository(gateway).search("store-1", null).shouldBeRight()

            scan.entries shouldHaveSize 10
            scan.truncated shouldBe true
        }

        @Test
        fun `should send a null query variable when the query is blank`() {
            val gateway = ShopifyAdminGatewayFake().apply {
                enqueue(json.parseToJsonElement("""{"products":{"pageInfo":{"hasNextPage":false,"endCursor":null},"edges":[]}}""").right())
            }

            ProductShopifyRepository(gateway).search("store-1", "   ").shouldBeRight()

            gateway.calls.single().variables.jsonObject["query"] shouldBe JsonNull
        }
    }

    @Nested
    inner class RawContentTest {

        @Test
        fun `should return null and emit no second call when the product does not exist`() {
            val gateway = ShopifyAdminGatewayFake().apply {
                enqueue(json.parseToJsonElement("""{"product":null}""").right())
            }

            ProductShopifyRepository(gateway).rawContent("store-1", "gid://shopify/Product/999").shouldBeRight() shouldBe null
            gateway.calls shouldHaveSize 1
        }

        @Test
        fun `should combine the product query and the media query, grouping variant combos by media id`() {
            val gateway = ShopifyAdminGatewayFake()
            gateway.nextResponseProvider = { call ->
                when {
                    call.query.contains("GetRawContent") ->
                        json.parseToJsonElement(
                            """{"product":{
                                "id":"gid://shopify/Product/1","title":"Maillot","handle":"maillot","status":"ACTIVE",
                                "descriptionHtml":"<p>Un <strong>maillot</strong> léger</p>",
                                "priceRangeV2":{"minVariantPrice":{"amount":"29.99","currencyCode":"EUR"},"maxVariantPrice":{"amount":"29.99","currencyCode":"EUR"}},
                                "variants":{"edges":[
                                    {"node":{"id":"gid://shopify/ProductVariant/1","selectedOptions":[{"name":"Couleur","value":"Olive"}],"media":{"edges":[{"node":{"id":"gid://shopify/MediaImage/1"}}]}}},
                                    {"node":{"id":"gid://shopify/ProductVariant/2","selectedOptions":[{"name":"Couleur","value":"Noir"}],"media":{"edges":[]}}}
                                ]},
                                "options":[{"id":"gid://shopify/ProductOption/1","name":"Couleur","optionValues":[{"id":"o1","name":"Olive"},{"id":"o2","name":"Noir"}]}]
                            }}""",
                        ).right()
                    else ->
                        json.parseToJsonElement(
                            """{"product":{"status":"ACTIVE","media":{"edges":[
                                {"node":{"id":"gid://shopify/MediaImage/1","alt":"Alt du produit","image":{"url":"https://cdn/1.jpg","altText":null}}}
                            ]}}}""",
                        ).right()
                }
            }

            val snapshot = requireNotNull(ProductShopifyRepository(gateway).rawContent("store-1", "gid://shopify/Product/1").shouldBeRight())

            snapshot.title shouldBe "Maillot"
            snapshot.handle shouldBe "maillot"
            snapshot.minPrice shouldBe "29.99"
            snapshot.maxPrice shouldBe "29.99"
            snapshot.currency shouldBe "EUR"
            snapshot.media shouldHaveSize 1
            snapshot.media.single().altText shouldBe "Alt du produit"
            snapshot.media.single().variantCombos shouldBe listOf("Couleur: Olive")
            snapshot.options.single().values shouldBe listOf("Olive", "Noir")
            snapshot.variants.map { it.combo } shouldBe listOf("Couleur: Olive", "Couleur: Noir")
            gateway.calls shouldHaveSize 2
        }
    }

    @Nested
    inner class EnrichedContentTest {

        @Test
        fun `should return null when the product does not exist`() {
            val gateway = ShopifyAdminGatewayFake().apply {
                enqueue(json.parseToJsonElement("""{"product":null}""").right())
            }

            ProductShopifyRepository(gateway).enrichedContent("store-1", "gid://shopify/Product/999").shouldBeRight() shouldBe null
        }

        @Test
        fun `should parse specs, faq, complementary products and related guides into typed entries`() {
            val gateway = ShopifyAdminGatewayFake().apply {
                enqueue(
                    json.parseToJsonElement(
                        """{"product":{
                            "id":"gid://shopify/Product/1","title":"T","handle":"t","status":"ACTIVE",
                            "productType":"Sacoche","tags":["a","b"],"descriptionHtml":"<p>Desc</p>",
                            "contentStatus":{"value":"to_review"},
                            "summaryPoints":{"value":"[\"Point 1\"]"},
                            "whyRecommend":null,"howToUse":null,
                            "specs":{"references":{"edges":[{"node":{"label":{"value":"Poids"},"value":{"value":"100g"}}}]}},
                            "faq":{"references":{"edges":[{"node":{"question":{"value":"Q?"},"answer":{"value":"A."}}}]}},
                            "complementaryProducts":{"references":{"edges":[{"node":{"id":"gid://shopify/Product/2","title":"Autre"}}]}},
                            "originalTitle":null,"originalDescriptionHtml":null,
                            "relatedGuides":{"references":{"edges":[{"node":{"id":"gid://shopify/Article/1","handle":"guide","title":"Guide"}}]}},
                            "relatedGuidesSource":{"value":"manual"},
                            "idealFor":{"value":"[\"bikepacking\"]"}
                        }}""",
                    ).right(),
                )
            }

            val fetch = requireNotNull(ProductShopifyRepository(gateway).enrichedContent("store-1", "gid://shopify/Product/1").shouldBeRight())

            fetch.specs.single().label shouldBe "Poids"
            fetch.specs.single().value shouldBe "100g"
            fetch.faq.single().question shouldBe "Q?"
            fetch.faq.single().answer shouldBe "A."
            fetch.complementaryProducts.single().title shouldBe "Autre"
            fetch.relatedGuides.single().title shouldBe "Guide"
            fetch.relatedGuidesSourceRaw shouldBe "manual"
            fetch.summaryPoints shouldBe listOf("Point 1")
            fetch.idealFor shouldBe listOf("bikepacking")
            fetch.contentStatusValue shouldBe "to_review"
        }
    }

    @Nested
    inner class ListToReviewTest {

        @Test
        fun `should return an empty list when nothing matches`() {
            val gateway = ShopifyAdminGatewayFake().apply {
                enqueue(json.parseToJsonElement("""{"products":{"pageInfo":{"hasNextPage":false,"endCursor":null},"edges":[]}}""").right())
            }

            ProductShopifyRepository(gateway).listToReview("store-1", ToReviewResourceType.PRODUCT).shouldBeRight() shouldBe emptyList()
        }

        @Test
        fun `should keep only nodes whose content_status is exactly to_review`() {
            val gateway = ShopifyAdminGatewayFake().apply {
                enqueue(
                    json.parseToJsonElement(
                        """{"collections":{"pageInfo":{"hasNextPage":false,"endCursor":null},"edges":[
                            {"node":{"id":"gid://shopify/Collection/1","title":"To review","handle":"to-review","contentStatus":{"value":"to_review"}}},
                            {"node":{"id":"gid://shopify/Collection/2","title":"Blocked","handle":"blocked","contentStatus":{"value":"blocked"}}},
                            {"node":{"id":"gid://shopify/Collection/3","title":"Untreated","handle":"untreated","contentStatus":null}}
                        ]}}""",
                    ).right(),
                )
            }

            val entries = ProductShopifyRepository(gateway).listToReview("store-1", ToReviewResourceType.COLLECTION).shouldBeRight()

            entries.map { it.id } shouldBe listOf("gid://shopify/Collection/1")
        }

        @Test
        fun `should follow the cursor across pages of the article scan until hasNextPage is false`() {
            val gateway = ShopifyAdminGatewayFake()
            gateway.nextResponseProvider = { call ->
                if (call.variables.toString().contains("null")) {
                    json.parseToJsonElement(
                        """{"articles":{"pageInfo":{"hasNextPage":true,"endCursor":"cursor-1"},"edges":[
                            {"node":{"id":"gid://shopify/Article/1","title":"A","handle":"a","contentStatus":{"value":"to_review"}}}
                        ]}}""",
                    ).right()
                } else {
                    json.parseToJsonElement(
                        """{"articles":{"pageInfo":{"hasNextPage":false,"endCursor":null},"edges":[
                            {"node":{"id":"gid://shopify/Article/2","title":"B","handle":"b","contentStatus":{"value":"to_review"}}}
                        ]}}""",
                    ).right()
                }
            }

            val entries = ProductShopifyRepository(gateway).listToReview("store-1", ToReviewResourceType.ARTICLE).shouldBeRight()

            entries.map { it.id } shouldBe listOf("gid://shopify/Article/1", "gid://shopify/Article/2")
            gateway.calls shouldHaveSize 2
        }
    }

    @Nested
    inner class ListOrphansTest {

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

            val scan = ProductShopifyRepository(gateway).listOrphans("store-1").shouldBeRight()

            scan.orphans shouldBe emptyList()
        }

        @Test
        fun `should list a product absent from every collection, excluding ARCHIVED`() {
            val gateway = ShopifyAdminGatewayFake()
            gateway.nextResponseProvider = { call ->
                when {
                    call.query.contains("OrphanScanCollections") ->
                        json.parseToJsonElement("""{"collections":{"pageInfo":{"hasNextPage":false,"endCursor":null},"edges":[]}}""").right()
                    else ->
                        json.parseToJsonElement(
                            """{"products":{"pageInfo":{"hasNextPage":false,"endCursor":null},"edges":[
                                {"node":{"id":"gid://shopify/Product/1","title":"Orphan","handle":"orphan","status":"DRAFT","contentStatus":null,"summaryPoints":null}},
                                {"node":{"id":"gid://shopify/Product/2","title":"Archived","handle":"archived","status":"ARCHIVED","contentStatus":null,"summaryPoints":null}}
                            ]}}""",
                        ).right()
                }
            }

            val scan = ProductShopifyRepository(gateway).listOrphans("store-1").shouldBeRight()

            scan.orphans.map { it.id } shouldBe listOf("gid://shopify/Product/1")
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
                        """{"products":{"pageInfo":{"hasNextPage":false,"endCursor":null},"edges":[]}}""",
                    ).right()
                }
            }

            val scan = ProductShopifyRepository(gateway).listOrphans("store-1").shouldBeRight()

            scan.collectionsWithTruncatedProducts.single().title shouldBe "Big collection"
            scan.collectionsTruncated shouldBe false
        }
    }

    @Nested
    inner class MarkBlockedTest {

        @Test
        fun `should mark the resource blocked with a single setMetafields call carrying the literal 'blocked' value`() {
            val gateway = ShopifyAdminGatewayFake().apply {
                enqueue(
                    json.parseToJsonElement(
                        """{"metafieldsSet":{"metafields":[{"id":"gid://shopify/Metafield/1","key":"content_status"}],"userErrors":[]}}""",
                    ).right(),
                )
            }

            val outcome = ProductShopifyRepository(gateway).markBlocked("store-1", "gid://shopify/Product/1").shouldBeRight()

            outcome shouldBe ProductMetafieldWriteOutcome.Marked
            val metafield = gateway.calls.single().variables.jsonObject.getValue("metafields").jsonArray.single().jsonObject
            metafield.getValue("ownerId").jsonPrimitive.content shouldBe "gid://shopify/Product/1"
            metafield.getValue("value").jsonPrimitive.content shouldBe "blocked"
        }

        @Test
        fun `should return a failed outcome carrying the formatted Shopify user error when metafieldsSet rejects the write`() {
            val gateway = ShopifyAdminGatewayFake().apply {
                enqueue(
                    json.parseToJsonElement(
                        """{"metafieldsSet":{"metafields":[],"userErrors":[{"field":["metafields","0","value"],"message":"is invalid"}]}}""",
                    ).right(),
                )
            }

            val outcome = ProductShopifyRepository(gateway).markBlocked("store-1", "gid://shopify/Product/1").shouldBeRight()

            (outcome as ProductMetafieldWriteOutcome.Failed).detail shouldBe "metafields.0.value : is invalid"
        }
    }

    @Nested
    inner class PublishTest {

        @Test
        fun `should return NotFound and emit no further call when the product does not exist`() {
            val gateway = ShopifyAdminGatewayFake().apply {
                enqueue(json.parseToJsonElement("""{"product":null}""").right())
            }

            val outcome = ProductShopifyRepository(gateway).publish("store-1", "gid://shopify/Product/999").shouldBeRight()

            outcome shouldBe ProductPublishOutcome.NotFound
            gateway.calls shouldHaveSize 1
        }

        @Test
        fun `should return a failed outcome and emit no further call when productUpdate rejects the activation`() {
            val gateway = ShopifyAdminGatewayFake().apply {
                enqueue(json.parseToJsonElement("""{"product":{"id":"gid://shopify/Product/1","title":"Item","status":"DRAFT","resourcePublicationsCount":{"count":0}}}""").right())
                enqueue(json.parseToJsonElement("""{"productUpdate":{"product":null,"userErrors":[{"field":["status"],"message":"cannot activate"}]}}""").right())
            }

            val outcome = ProductShopifyRepository(gateway).publish("store-1", "gid://shopify/Product/1").shouldBeRight()

            (outcome as ProductPublishOutcome.Failed).detail shouldBe "status : cannot activate"
            gateway.calls shouldHaveSize 2
        }

        @Test
        fun `should skip the publications lookup and publishablePublish call when the product is already published on a channel`() {
            val gateway = ShopifyAdminGatewayFake().apply {
                enqueue(json.parseToJsonElement("""{"product":{"id":"gid://shopify/Product/1","title":"Item","status":"ACTIVE","resourcePublicationsCount":{"count":2}}}""").right())
                enqueue(json.parseToJsonElement("""{"productUpdate":{"product":{"id":"gid://shopify/Product/1"},"userErrors":[]}}""").right())
                enqueue(json.parseToJsonElement("""{"metafieldsDelete":{"deletedMetafields":[null],"userErrors":[]}}""").right())
            }

            val outcome = ProductShopifyRepository(gateway).publish("store-1", "gid://shopify/Product/1").shouldBeRight()

            (outcome as ProductPublishOutcome.Published).wasNeverPublished shouldBe false
            gateway.calls shouldHaveSize 3
        }

        @Test
        fun `should resolve the online store publication and publish when the product was never published`() {
            val gateway = ShopifyAdminGatewayFake().apply {
                enqueue(json.parseToJsonElement("""{"product":{"id":"gid://shopify/Product/1","title":"Item","status":"DRAFT","resourcePublicationsCount":{"count":0}}}""").right())
                enqueue(json.parseToJsonElement("""{"productUpdate":{"product":{"id":"gid://shopify/Product/1"},"userErrors":[]}}""").right())
                enqueue(json.parseToJsonElement("""{"metafieldsDelete":{"deletedMetafields":[null],"userErrors":[]}}""").right())
                enqueue(json.parseToJsonElement("""{"publications":{"edges":[{"node":{"id":"gid://shopify/Publication/1","name":"Online Store"}}]}}""").right())
                enqueue(json.parseToJsonElement("""{"publishablePublish":{"publishable":{"id":"gid://shopify/Product/1"},"userErrors":[]}}""").right())
            }

            val outcome = ProductShopifyRepository(gateway).publish("store-1", "gid://shopify/Product/1").shouldBeRight()

            outcome shouldBe ProductPublishOutcome.Published("Item", wasNeverPublished = true)
            gateway.calls shouldHaveSize 5
        }
    }

    @Nested
    inner class UnpublishTest {

        @Test
        fun `should return NotFound and emit no further call when the product does not exist`() {
            val gateway = ShopifyAdminGatewayFake().apply {
                enqueue(json.parseToJsonElement("""{"product":null}""").right())
            }

            val outcome = ProductShopifyRepository(gateway).unpublish("store-1", "gid://shopify/Product/999").shouldBeRight()

            outcome shouldBe ProductUnpublishOutcome.NotFound
            gateway.calls shouldHaveSize 1
        }

        @Test
        fun `should return Noop and emit no further call when the product has no active publication`() {
            val gateway = ShopifyAdminGatewayFake().apply {
                enqueue(json.parseToJsonElement("""{"product":{"id":"gid://shopify/Product/1","title":"Item","resourcePublicationsCount":{"count":0}}}""").right())
            }

            val outcome = ProductShopifyRepository(gateway).unpublish("store-1", "gid://shopify/Product/1").shouldBeRight()

            outcome shouldBe ProductUnpublishOutcome.Noop("Item")
            gateway.calls shouldHaveSize 1
        }

        @Test
        fun `should return a failed outcome when publishableUnpublish rejects the removal`() {
            val gateway = ShopifyAdminGatewayFake().apply {
                enqueue(json.parseToJsonElement("""{"product":{"id":"gid://shopify/Product/1","title":"Item","resourcePublicationsCount":{"count":1}}}""").right())
                enqueue(json.parseToJsonElement("""{"publications":{"edges":[{"node":{"id":"gid://shopify/Publication/1","name":"Online Store"}}]}}""").right())
                enqueue(json.parseToJsonElement("""{"publishableUnpublish":{"publishable":null,"userErrors":[{"field":[],"message":"cannot unpublish"}]}}""").right())
            }

            val outcome = ProductShopifyRepository(gateway).unpublish("store-1", "gid://shopify/Product/1").shouldBeRight()

            (outcome as ProductUnpublishOutcome.Failed).detail shouldBe "cannot unpublish"
            gateway.calls shouldHaveSize 3
        }

        @Test
        fun `should return Unpublished carrying the pre-write publications count`() {
            val gateway = ShopifyAdminGatewayFake().apply {
                enqueue(json.parseToJsonElement("""{"product":{"id":"gid://shopify/Product/1","title":"Item","resourcePublicationsCount":{"count":1}}}""").right())
                enqueue(json.parseToJsonElement("""{"publications":{"edges":[{"node":{"id":"gid://shopify/Publication/1","name":"Online Store"}}]}}""").right())
                enqueue(json.parseToJsonElement("""{"publishableUnpublish":{"publishable":{"id":"gid://shopify/Product/1"},"userErrors":[]}}""").right())
            }

            val outcome = ProductShopifyRepository(gateway).unpublish("store-1", "gid://shopify/Product/1").shouldBeRight()

            outcome shouldBe ProductUnpublishOutcome.Unpublished("Item", countBefore = 1)
        }
    }
}
