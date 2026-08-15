package com.zickat.shopifymcpserver.menus.domain

import arrow.core.right
import com.zickat.shopifymcpserver.menus.MenusFakeRepository
import com.zickat.shopifymcpserver.menus.domain.models.MenuItemNode
import com.zickat.shopifymcpserver.menus.domain.models.MenuItemType
import com.zickat.shopifymcpserver.menus.domain.models.MenuNode
import com.zickat.shopifymcpserver.menus.domain.repositories.MenuUpdateOutcome
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class AddMenuItemUseCaseTest {

    private fun useCase(repository: MenusFakeRepository) = AddMenuItemUseCase(MenuRewriteEngine(repository))

    private fun emptyMenu() = MenuNode(id = "gid://shopify/Menu/1", handle = "main-menu", title = "Main menu", isDefault = false, items = emptyList())

    @Test
    fun `execute should refuse before any read when neither resource_id nor url is supplied`() {
        val repository = MenusFakeRepository()

        val result = useCase(repository).execute("store-1", "gid://shopify/Menu/1", "Rien", null, null, null, null).shouldBeRight()

        result.outcome shouldBe AddMenuItemOutcome.INVALID_ITEM
        repository.fetchCalls.shouldBeEmpty()
    }

    @Test
    fun `execute should add the item and reuse the pre-write menu title and handle for the report`() {
        val repository = MenusFakeRepository().apply {
            fetchResponse = emptyMenu().right()
            rewriteResponse = MenuUpdateOutcome.Success(
                listOf(
                    MenuItemNode(
                        id = "gid://shopify/MenuItem/1",
                        title = "Guides",
                        type = MenuItemType.COLLECTION,
                        url = null,
                        resourceId = "gid://shopify/Collection/1",
                        tags = emptyList(),
                        items = emptyList(),
                    ),
                ),
            ).right()
        }

        val result = useCase(repository).execute("store-1", "gid://shopify/Menu/1", "Guides", "gid://shopify/Collection/1", null, null, null).shouldBeRight()

        result.outcome shouldBe AddMenuItemOutcome.ADDED
        result.menuTitle shouldBe "Main menu"
        result.menuHandle shouldBe "main-menu"
        result.position shouldBe 1
    }
}
