package com.zickat.shopifymcpserver.pages.spi.shopify

import arrow.core.right
import com.zickat.shopifymcpserver.pages.domain.PageMetafieldInput
import com.zickat.shopifymcpserver.pages.domain.PageMetafieldsSnapshot
import com.zickat.shopifymcpserver.pages.domain.PageNative
import com.zickat.shopifymcpserver.pages.domain.repositories.PageDeleteOutcome
import com.zickat.shopifymcpserver.pages.domain.repositories.PageListing
import com.zickat.shopifymcpserver.pages.domain.repositories.PageMetafieldsWriteOutcome
import com.zickat.shopifymcpserver.pages.domain.repositories.PagePatch
import com.zickat.shopifymcpserver.pages.domain.repositories.PageSummary
import com.zickat.shopifymcpserver.pages.domain.repositories.PageWriteOutcome
import com.zickat.shopifymcpserver.shared_kernel.TechnicalError
import com.zickat.shopifymcpserver.shopify.ShopifyAdminGatewayFake
import io.kotest.assertions.arrow.core.shouldBeLeft
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.jsonObject
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class PageShopifyRepositoryTest {

    private val json = Json

    @Nested
    inner class ListTest {

        @Test
        fun `list should return an empty listing when the store has no page`() {
            val gateway = ShopifyAdminGatewayFake().apply {
                enqueue(json.parseToJsonElement("""{"pages":{"pageInfo":{"hasNextPage":false,"endCursor":null},"edges":[]}}""").right())
            }
            val repository = PageShopifyRepository(gateway)

            val listing = repository.list("store-1", null).shouldBeRight()

            listing shouldBe PageListing(emptyList(), truncated = false)
        }

        @Test
        fun `list should map every edge to a PageSummary`() {
            val gateway = ShopifyAdminGatewayFake().apply {
                enqueue(
                    json.parseToJsonElement(
                        """{"pages":{"pageInfo":{"hasNextPage":false,"endCursor":null},"edges":[
                            {"node":{"id":"gid://shopify/Page/1","title":"Contact","handle":"contact"}}
                        ]}}""",
                    ).right(),
                )
            }
            val repository = PageShopifyRepository(gateway)

            val listing = repository.list("store-1", null).shouldBeRight()

            listing shouldBe PageListing(listOf(PageSummary("gid://shopify/Page/1", "Contact", "contact")), truncated = false)
        }

        @Test
        fun `list should send a null query variable when the query is blank`() {
            val gateway = ShopifyAdminGatewayFake().apply {
                enqueue(json.parseToJsonElement("""{"pages":{"pageInfo":{"hasNextPage":false,"endCursor":null},"edges":[]}}""").right())
            }
            val repository = PageShopifyRepository(gateway)

            repository.list("store-1", null).shouldBeRight()

            gateway.calls.single().variables.jsonObject["query"] shouldBe JsonNull
        }

        @Test
        fun `list should follow the cursor across pages until hasNextPage is false`() {
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
            val repository = PageShopifyRepository(gateway)

            val listing = repository.list("store-1", null).shouldBeRight()

            listing.pages.map { it.title } shouldBe listOf("Contact", "Guides")
            listing.truncated shouldBe false
            gateway.calls.size shouldBe 2
        }

        @Test
        fun `list should stop at MAX_SEARCH_PAGES and report the scan as truncated`() {
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
            val repository = PageShopifyRepository(gateway)

            val listing = repository.list("store-1", null).shouldBeRight()

            listing.pages.size shouldBe 10
            listing.truncated shouldBe true
        }

        @Test
        fun `list should raise a technical error when the response is malformed`() {
            val gateway = ShopifyAdminGatewayFake().apply {
                enqueue(json.parseToJsonElement("""{"unexpected":true}""").right())
            }
            val repository = PageShopifyRepository(gateway)

            repository.list("store-1", null).shouldBeLeft().shouldBeInstanceOf<TechnicalError>().messageKey shouldBe "shopify.graphql.response.malformed"
        }
    }

    @Nested
    inner class GetTest {

        @Test
        fun `get should return null when the page does not exist`() {
            val gateway = ShopifyAdminGatewayFake().apply {
                enqueue(json.parseToJsonElement("""{"page":null}""").right())
            }
            val repository = PageShopifyRepository(gateway)

            repository.get("store-1", "gid://shopify/Page/404").shouldBeRight() shouldBe null
        }

        @Test
        fun `get should map the page fields`() {
            val gateway = ShopifyAdminGatewayFake().apply {
                enqueue(
                    json.parseToJsonElement(
                        """{"page":{"id":"gid://shopify/Page/1","title":"T","handle":"t","isPublished":true,"body":"<p></p>"}}""",
                    ).right(),
                )
            }
            val repository = PageShopifyRepository(gateway)

            val page = repository.get("store-1", "gid://shopify/Page/1").shouldBeRight()

            page shouldBe PageNative("gid://shopify/Page/1", "T", "t", isPublished = true, body = "<p></p>")
        }
    }

    @Nested
    inner class CreateTest {

        @Test
        fun `create should report failure when Shopify returns userErrors`() {
            val gateway = ShopifyAdminGatewayFake().apply {
                enqueue(
                    json.parseToJsonElement(
                        """{"pageCreate":{"page":null,"userErrors":[{"field":["title"],"message":"can't be blank"}]}}""",
                    ).right(),
                )
            }
            val repository = PageShopifyRepository(gateway)

            val outcome = repository.create("store-1", "", "<p>x</p>", null, null).shouldBeRight()

            (outcome as PageWriteOutcome.Failed).detail shouldBe "title : can't be blank"
        }

        @Test
        fun `create should default isPublished to false when publish is omitted and Shopify echoes it back`() {
            val gateway = ShopifyAdminGatewayFake().apply {
                enqueue(
                    json.parseToJsonElement(
                        """{"pageCreate":{"page":{"id":"gid://shopify/Page/1","title":"T","handle":"t","isPublished":false},"userErrors":[]}}""",
                    ).right(),
                )
            }
            val repository = PageShopifyRepository(gateway)

            val outcome = repository.create("store-1", "T", "<p>x</p>", null, null).shouldBeRight()

            (outcome as PageWriteOutcome.Success).page.isPublished shouldBe false
        }
    }

    @Nested
    inner class UpdateTest {

        @Test
        fun `update should map the updated page`() {
            val gateway = ShopifyAdminGatewayFake().apply {
                enqueue(
                    json.parseToJsonElement(
                        """{"pageUpdate":{"page":{"id":"gid://shopify/Page/1","title":"New","handle":"new","isPublished":true},"userErrors":[]}}""",
                    ).right(),
                )
            }
            val repository = PageShopifyRepository(gateway)

            val outcome = repository.update("store-1", "gid://shopify/Page/1", PagePatch("New", null, "new")).shouldBeRight()

            (outcome as PageWriteOutcome.Success).page.title shouldBe "New"
            outcome.page.handle shouldBe "new"
        }
    }

    @Nested
    inner class SetPublishedTest {

        @Test
        fun `setPublished should default isPublished to the requested target when Shopify omits it`() {
            val gateway = ShopifyAdminGatewayFake().apply {
                enqueue(
                    json.parseToJsonElement(
                        """{"pageUpdate":{"page":{"id":"gid://shopify/Page/1","title":"T"},"userErrors":[]}}""",
                    ).right(),
                )
            }
            val repository = PageShopifyRepository(gateway)

            val outcome = repository.setPublished("store-1", "gid://shopify/Page/1", true).shouldBeRight()

            (outcome as PageWriteOutcome.Success).page.isPublished shouldBe true
        }
    }

    @Nested
    inner class DeleteTest {

        @Test
        fun `delete should report Deleted when Shopify returns a deletedPageId`() {
            val gateway = ShopifyAdminGatewayFake().apply {
                enqueue(json.parseToJsonElement("""{"pageDelete":{"deletedPageId":"gid://shopify/Page/1","userErrors":[]}}""").right())
            }
            val repository = PageShopifyRepository(gateway)

            repository.delete("store-1", "gid://shopify/Page/1").shouldBeRight() shouldBe PageDeleteOutcome.Deleted
        }

        @Test
        fun `delete should report failure when Shopify returns userErrors`() {
            val gateway = ShopifyAdminGatewayFake().apply {
                enqueue(
                    json.parseToJsonElement(
                        """{"pageDelete":{"deletedPageId":null,"userErrors":[{"field":[],"message":"forbidden"}]}}""",
                    ).right(),
                )
            }
            val repository = PageShopifyRepository(gateway)

            val outcome = repository.delete("store-1", "gid://shopify/Page/1").shouldBeRight()

            (outcome as PageDeleteOutcome.Failed).detail shouldBe "forbidden"
        }
    }

    @Nested
    inner class MetafieldsTest {

        @Test
        fun `metafields should return null when the page does not exist`() {
            val gateway = ShopifyAdminGatewayFake().apply {
                enqueue(json.parseToJsonElement("""{"page":null}""").right())
            }
            val repository = PageShopifyRepository(gateway)

            repository.metafields("store-1", "gid://shopify/Page/999").shouldBeRight() shouldBe null
        }

        @Test
        fun `metafields should map every custom metafield edge`() {
            val gateway = ShopifyAdminGatewayFake().apply {
                enqueue(
                    json.parseToJsonElement(
                        """{"page":{"id":"gid://shopify/Page/1","title":"Guides","metafields":{"edges":[
                            {"node":{"key":"summary","type":"single_line_text_field","value":"Résumé"}}
                        ],"pageInfo":{"hasNextPage":true}}}}""",
                    ).right(),
                )
            }
            val repository = PageShopifyRepository(gateway)

            val snapshot = repository.metafields("store-1", "gid://shopify/Page/1").shouldBeRight()

            snapshot shouldBe PageMetafieldsSnapshot(
                "Guides",
                listOf(com.zickat.shopifymcpserver.pages.domain.PageMetafield("summary", "single_line_text_field", "Résumé")),
                truncated = true,
            )
        }

        @Test
        fun `metafields should raise a technical error when the response is malformed`() {
            val gateway = ShopifyAdminGatewayFake().apply {
                enqueue(json.parseToJsonElement("""{"page":{"id":"gid://shopify/Page/1","title":"Guides"}}""").right())
            }
            val repository = PageShopifyRepository(gateway)

            repository.metafields("store-1", "gid://shopify/Page/1").shouldBeLeft().shouldBeInstanceOf<TechnicalError>().messageKey shouldBe
                "shopify.graphql.response.malformed"
        }
    }

    @Nested
    inner class SetMetafieldsTest {

        @Test
        fun `setMetafields should report Updated when Shopify returns no userErrors`() {
            val gateway = ShopifyAdminGatewayFake().apply {
                enqueue(json.parseToJsonElement("""{"metafieldsSet":{"metafields":[{"id":"1","key":"themes"}],"userErrors":[]}}""").right())
            }
            val repository = PageShopifyRepository(gateway)

            val outcome = repository.setMetafields(
                "store-1",
                "gid://shopify/Page/1",
                listOf(PageMetafieldInput("themes", "single_line_text_field", "x")),
            ).shouldBeRight()

            outcome shouldBe PageMetafieldsWriteOutcome.Updated
        }

        @Test
        fun `setMetafields should report failure when Shopify returns userErrors`() {
            val gateway = ShopifyAdminGatewayFake().apply {
                enqueue(
                    json.parseToJsonElement(
                        """{"metafieldsSet":{"metafields":null,"userErrors":[{"field":["value"],"message":"invalid"}]}}""",
                    ).right(),
                )
            }
            val repository = PageShopifyRepository(gateway)

            val outcome = repository.setMetafields(
                "store-1",
                "gid://shopify/Page/1",
                listOf(PageMetafieldInput("themes", "single_line_text_field", "x")),
            ).shouldBeRight()

            (outcome as PageMetafieldsWriteOutcome.Failed).detail shouldBe "value : invalid"
        }
    }
}
