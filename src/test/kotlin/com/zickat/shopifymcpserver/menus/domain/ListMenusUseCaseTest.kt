package com.zickat.shopifymcpserver.menus.domain

import arrow.core.right
import com.zickat.shopifymcpserver.menus.domain.ListMenusUseCaseSyntheticFixtures.edge
import com.zickat.shopifymcpserver.menus.domain.ListMenusUseCaseSyntheticFixtures.menuNode
import com.zickat.shopifymcpserver.menus.domain.ListMenusUseCaseSyntheticFixtures.page
import com.zickat.shopifymcpserver.menus.domain.ListMenusUseCaseSyntheticFixtures.rawItem
import com.zickat.shopifymcpserver.menus.domain.ListMenusUseCaseSyntheticFixtures.rawItemWithoutItemsKey
import com.zickat.shopifymcpserver.shopify.ShopifyAdminGatewayFake
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Test

class ListMenusUseCaseTest {

    @Test
    fun `execute should return one block per menu when the response is a single page`() {
        val gateway = ShopifyAdminGatewayFake()
        gateway.default = page(
            listOf(
                edge(menuNode(id = "gid://shopify/Menu/1", handle = "main-menu", title = "Main menu")),
                edge(menuNode(id = "gid://shopify/Menu/2", handle = "footer", title = "Footer", isDefault = true)),
            ),
        ).right()
        val useCase = ListMenusUseCase(gateway)

        val result = useCase.execute("store-1", null, null).shouldBeRight()

        result.blocks.size shouldBe 2
        result.blocks[0] shouldContainMenuId "gid://shopify/Menu/1"
        result.blocks[1] shouldContainMenuId "gid://shopify/Menu/2"
        result.truncated shouldBe false
        result.hadQuery shouldBe false
    }

    @Test
    fun `execute should follow the cursor across pages until hasNextPage is false`() {
        val gateway = ShopifyAdminGatewayFake()
        gateway.nextResponseProvider = { call ->
            val cursor = call.variables.jsonObject["cursor"]
            if (cursor == JsonNull) {
                page(listOf(edge(menuNode(id = "gid://shopify/Menu/1"))), hasNextPage = true, endCursor = "cursor-1").right()
            } else {
                page(listOf(edge(menuNode(id = "gid://shopify/Menu/2"))), hasNextPage = false).right()
            }
        }
        val useCase = ListMenusUseCase(gateway)

        val result = useCase.execute("store-1", null, null).shouldBeRight()

        gateway.calls.size shouldBe 2
        gateway.calls[1].variables.jsonObject["cursor"]?.jsonPrimitive?.content shouldBe "cursor-1"
        result.blocks.size shouldBe 2
        result.truncated shouldBe false
    }

    @Test
    fun `execute should stop at MAX_SEARCH_PAGES and mark the result truncated when menus never run out of pages`() {
        val gateway = ShopifyAdminGatewayFake()
        var pageCount = 0
        gateway.nextResponseProvider = {
            pageCount += 1
            page(listOf(edge(menuNode(id = "gid://shopify/Menu/$pageCount"))), hasNextPage = true, endCursor = "cursor-$pageCount").right()
        }
        val useCase = ListMenusUseCase(gateway)

        val result = useCase.execute("store-1", null, null).shouldBeRight()

        gateway.calls.size shouldBe 10
        result.blocks.size shouldBe 10
        result.truncated shouldBe true
    }

    @Test
    fun `execute should normalize an N4 item that carries no items key at all, as an empty child list`() {
        val gateway = ShopifyAdminGatewayFake()
        val n4WithoutItemsKey = rawItemWithoutItemsKey(id = "gid://shopify/MenuItem/4", title = "Leaf")
        val n3 = rawItem(id = "gid://shopify/MenuItem/3", title = "N3", items = buildJsonArray { add(n4WithoutItemsKey) })
        val n2 = rawItem(id = "gid://shopify/MenuItem/2", title = "N2", items = buildJsonArray { add(n3) })
        val n1 = rawItem(id = "gid://shopify/MenuItem/1", title = "N1", items = buildJsonArray { add(n2) })
        gateway.default = page(
            listOf(edge(menuNode(items = buildJsonArray { add(n1) }))),
        ).right()
        val useCase = ListMenusUseCase(gateway)

        val result = useCase.execute("store-1", null, 4).shouldBeRight()

        val block = result.blocks.single()
        block shouldContainMenuId "gid://shopify/MenuItem/4"
        block.contains("⚠") shouldBe false
    }

    @Test
    fun `execute should trim a blank query down to null in the sent variables and report hadQuery false`() {
        val gateway = ShopifyAdminGatewayFake()
        gateway.default = page(emptyList()).right()
        val useCase = ListMenusUseCase(gateway)

        val result = useCase.execute("store-1", "   ", null).shouldBeRight()

        gateway.calls.single().variables.jsonObject["query"] shouldBe JsonNull
        result.hadQuery shouldBe false
        result.blocks shouldBe emptyList()
    }

    @Test
    fun `execute should pass a non-blank query through and report hadQuery true`() {
        val gateway = ShopifyAdminGatewayFake()
        gateway.default = page(emptyList()).right()
        val useCase = ListMenusUseCase(gateway)

        val result = useCase.execute("store-1", "title:*footer*", null).shouldBeRight()

        gateway.calls.single().variables.jsonObject["query"]?.jsonPrimitive?.content shouldBe "title:*footer*"
        result.hadQuery shouldBe true
    }

    @Test
    fun `execute should clamp an out-of-range depth into the 1 to 4 window instead of failing`() {
        val gateway = ShopifyAdminGatewayFake()
        val deepItem = rawItem(id = "gid://shopify/MenuItem/1", title = "Deep")
        gateway.default = page(
            listOf(edge(menuNode(items = buildJsonArray { add(deepItem) }))),
        ).right()
        val useCase = ListMenusUseCase(gateway)

        val result = useCase.execute("store-1", null, 99).shouldBeRight()

        result.blocks.single() shouldContainMenuId "gid://shopify/MenuItem/1"
    }

    private infix fun String.shouldContainMenuId(id: String) {
        contains(id) shouldBe true
    }
}
