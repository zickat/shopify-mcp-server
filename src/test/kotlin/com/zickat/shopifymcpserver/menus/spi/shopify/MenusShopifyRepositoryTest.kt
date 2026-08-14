package com.zickat.shopifymcpserver.menus.spi.shopify

import arrow.core.right
import com.zickat.shopifymcpserver.menus.domain.models.MenuListing
import com.zickat.shopifymcpserver.menus.spi.shopify.MenusGraphQLFixtures.edge
import com.zickat.shopifymcpserver.menus.spi.shopify.MenusGraphQLFixtures.menuNode
import com.zickat.shopifymcpserver.menus.spi.shopify.MenusGraphQLFixtures.page
import com.zickat.shopifymcpserver.menus.spi.shopify.MenusGraphQLFixtures.rawItem
import com.zickat.shopifymcpserver.menus.spi.shopify.MenusGraphQLFixtures.rawItemWithoutItemsKey
import com.zickat.shopifymcpserver.shopify.ShopifyAdminGatewayFake
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Test

class MenusShopifyRepositoryTest {

    @Test
    fun `list should return one menu per edge when the response is a single page`() {
        val gateway = ShopifyAdminGatewayFake()
        gateway.default = page(
            listOf(
                edge(menuNode(id = "gid://shopify/Menu/1", handle = "main-menu", title = "Main menu")),
                edge(menuNode(id = "gid://shopify/Menu/2", handle = "footer", title = "Footer", isDefault = true)),
            ),
        ).right()
        val repository = MenusShopifyRepository(gateway)

        val listing = repository.list("store-1", null).shouldBeRight()

        listing.menus.map { it.id } shouldBe listOf("gid://shopify/Menu/1", "gid://shopify/Menu/2")
        listing.menus[1].isDefault shouldBe true
        listing.truncated shouldBe false
    }

    @Test
    fun `list should follow the cursor across pages until hasNextPage is false`() {
        val gateway = ShopifyAdminGatewayFake()
        gateway.nextResponseProvider = { call ->
            val cursor = call.variables.jsonObject["cursor"]
            if (cursor == JsonNull) {
                page(listOf(edge(menuNode(id = "gid://shopify/Menu/1"))), hasNextPage = true, endCursor = "cursor-1").right()
            } else {
                page(listOf(edge(menuNode(id = "gid://shopify/Menu/2"))), hasNextPage = false).right()
            }
        }
        val repository = MenusShopifyRepository(gateway)

        val listing = repository.list("store-1", null).shouldBeRight()

        gateway.calls.size shouldBe 2
        gateway.calls[1].variables.jsonObject["cursor"]?.jsonPrimitive?.content shouldBe "cursor-1"
        listing.menus.size shouldBe 2
        listing.truncated shouldBe false
    }

    @Test
    fun `list should stop at MAX_SEARCH_PAGES and mark the listing truncated when menus never run out of pages`() {
        val gateway = ShopifyAdminGatewayFake()
        var pageCount = 0
        gateway.nextResponseProvider = {
            pageCount += 1
            page(listOf(edge(menuNode(id = "gid://shopify/Menu/$pageCount"))), hasNextPage = true, endCursor = "cursor-$pageCount").right()
        }
        val repository = MenusShopifyRepository(gateway)

        val listing = repository.list("store-1", null).shouldBeRight()

        gateway.calls.size shouldBe 10
        listing.menus.size shouldBe 10
        listing.truncated shouldBe true
    }

    @Test
    fun `list should normalize an N4 item that carries no items key at all, as an empty child list`() {
        val gateway = ShopifyAdminGatewayFake()
        val n4WithoutItemsKey = rawItemWithoutItemsKey(id = "gid://shopify/MenuItem/4", title = "Leaf")
        val n3 = rawItem(id = "gid://shopify/MenuItem/3", title = "N3", items = buildJsonArray { add(n4WithoutItemsKey) })
        val n2 = rawItem(id = "gid://shopify/MenuItem/2", title = "N2", items = buildJsonArray { add(n3) })
        val n1 = rawItem(id = "gid://shopify/MenuItem/1", title = "N1", items = buildJsonArray { add(n2) })
        gateway.default = page(listOf(edge(menuNode(items = buildJsonArray { add(n1) })))).right()
        val repository = MenusShopifyRepository(gateway)

        val listing = repository.list("store-1", null).shouldBeRight()

        val n1Node = listing.menus.single().items.single()
        n1Node.items.single().items.single().items.single().let { leaf ->
            leaf.id shouldBe "gid://shopify/MenuItem/4"
            leaf.items shouldBe emptyList()
        }
    }

    @Test
    fun `list should send the given query untouched in the sent variables`() {
        val gateway = ShopifyAdminGatewayFake()
        gateway.default = page(emptyList()).right()
        val repository = MenusShopifyRepository(gateway)

        repository.list("store-1", "title:*footer*").shouldBeRight()

        gateway.calls.single().variables.jsonObject["query"]?.jsonPrimitive?.content shouldBe "title:*footer*"
    }

    @Test
    fun `list should send a null query variable when the query is null`() {
        val gateway = ShopifyAdminGatewayFake()
        gateway.default = page(emptyList()).right()
        val repository = MenusShopifyRepository(gateway)

        val listing = repository.list("store-1", null).shouldBeRight()

        gateway.calls.single().variables.jsonObject["query"] shouldBe JsonNull
        listing shouldBe MenuListing(emptyList(), truncated = false)
    }
}
