package com.zickat.shopifymcpserver.catalog_status.spi.shopify

import arrow.core.right
import com.zickat.shopifymcpserver.catalog_status.domain.models.SearchResourceType
import com.zickat.shopifymcpserver.catalog_status.spi.shopify.CatalogStatusGraphQLFixtures.edge
import com.zickat.shopifymcpserver.catalog_status.spi.shopify.CatalogStatusGraphQLFixtures.page
import com.zickat.shopifymcpserver.catalog_status.spi.shopify.CatalogStatusGraphQLFixtures.resourceNode
import com.zickat.shopifymcpserver.shared_kernel.TechnicalError
import com.zickat.shopifymcpserver.shopify.ShopifyAdminGatewayFake
import io.kotest.assertions.arrow.core.shouldBeLeft
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Test

class CatalogStatusShopifyRepositoryTest {

    @Test
    fun `search should return one resource per edge when the response is a single page`() {
        val gateway = ShopifyAdminGatewayFake()
        gateway.default = page(
            "collections",
            listOf(
                edge(resourceNode(id = "gid://1", title = "First", handle = "first", contentStatus = "to_review")),
                edge(resourceNode(id = "gid://2", title = "Second", handle = "second")),
            ),
        ).right()
        val repository = CatalogStatusShopifyRepository(gateway)

        val listing = repository.search("store-1", SearchResourceType.COLLECTION, null).shouldBeRight()

        listing.resources.map { it.id } shouldBe listOf("gid://1", "gid://2")
        listing.resources[0].contentStatus shouldBe "to_review"
        listing.truncated shouldBe false
    }

    @Test
    fun `search should query the collections field and the intro_text secondary metafield for a COLLECTION`() {
        val gateway = ShopifyAdminGatewayFake()
        gateway.default = page("collections", emptyList()).right()
        val repository = CatalogStatusShopifyRepository(gateway)

        repository.search("store-1", SearchResourceType.COLLECTION, null).shouldBeRight()

        val sentQuery = gateway.calls.single().query
        sentQuery shouldContain "collections(first: 50"
        sentQuery shouldContain "intro_text"
    }

    @Test
    fun `search should query the articles field and the sections secondary metafield for an ARTICLE`() {
        val gateway = ShopifyAdminGatewayFake()
        gateway.default = page("articles", emptyList()).right()
        val repository = CatalogStatusShopifyRepository(gateway)

        repository.search("store-1", SearchResourceType.ARTICLE, null).shouldBeRight()

        val sentQuery = gateway.calls.single().query
        sentQuery shouldContain "articles(first: 50"
        sentQuery shouldContain "sections"
    }

    @Test
    fun `search should follow the cursor across pages until hasNextPage is false`() {
        val gateway = ShopifyAdminGatewayFake()
        gateway.nextResponseProvider = { call ->
            val cursor = call.variables.jsonObject["cursor"]
            if (cursor == JsonNull) {
                page("articles", listOf(edge(resourceNode(id = "gid://1"))), hasNextPage = true, endCursor = "cursor-1").right()
            } else {
                page("articles", listOf(edge(resourceNode(id = "gid://2"))), hasNextPage = false).right()
            }
        }
        val repository = CatalogStatusShopifyRepository(gateway)

        val listing = repository.search("store-1", SearchResourceType.ARTICLE, null).shouldBeRight()

        gateway.calls.size shouldBe 2
        gateway.calls[1].variables.jsonObject["cursor"]?.jsonPrimitive?.content shouldBe "cursor-1"
        listing.resources.map { it.id } shouldBe listOf("gid://1", "gid://2")
        listing.truncated shouldBe false
    }

    @Test
    fun `search should stop at MAX_SEARCH_PAGES and mark the listing truncated when the resource never runs out of pages`() {
        val gateway = ShopifyAdminGatewayFake()
        var pageCount = 0
        gateway.nextResponseProvider = {
            pageCount += 1
            page("collections", listOf(edge(resourceNode(id = "gid://$pageCount"))), hasNextPage = true, endCursor = "cursor-$pageCount").right()
        }
        val repository = CatalogStatusShopifyRepository(gateway)

        val listing = repository.search("store-1", SearchResourceType.COLLECTION, null).shouldBeRight()

        gateway.calls.size shouldBe 10
        listing.truncated shouldBe true
        listing.resources.size shouldBe 10
    }

    @Test
    fun `search should send the given query untouched in the sent variables`() {
        val gateway = ShopifyAdminGatewayFake()
        gateway.default = page("collections", emptyList()).right()
        val repository = CatalogStatusShopifyRepository(gateway)

        repository.search("store-1", SearchResourceType.COLLECTION, "title:*word*").shouldBeRight()

        gateway.calls.single().variables.jsonObject["query"]?.jsonPrimitive?.content shouldBe "title:*word*"
    }

    @Test
    fun `search should send a null query variable when the query is null`() {
        val gateway = ShopifyAdminGatewayFake()
        gateway.default = page("collections", emptyList()).right()
        val repository = CatalogStatusShopifyRepository(gateway)

        repository.search("store-1", SearchResourceType.COLLECTION, null).shouldBeRight()

        gateway.calls.single().variables.jsonObject["query"] shouldBe JsonNull
    }

    @Test
    fun `search should fail technically when the expected resource field is missing from the response`() {
        val gateway = ShopifyAdminGatewayFake()
        gateway.default = page("articles", emptyList()).right()
        val repository = CatalogStatusShopifyRepository(gateway)

        repository.search("store-1", SearchResourceType.COLLECTION, null).shouldBeLeft()
            .shouldBeInstanceOf<TechnicalError>().messageKey shouldBe "shopify.graphql.response.malformed"
    }
}
