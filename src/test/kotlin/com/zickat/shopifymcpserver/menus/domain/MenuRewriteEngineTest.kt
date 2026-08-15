package com.zickat.shopifymcpserver.menus.domain

import arrow.core.right
import com.zickat.shopifymcpserver.menus.MenusFakeRepository
import com.zickat.shopifymcpserver.menus.domain.models.MenuNode
import com.zickat.shopifymcpserver.menus.spi.shopify.MenusGraphQL
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.shouldBeInstanceOf
import org.junit.jupiter.api.Test

class MenuRewriteEngineTest {

    private fun engine(repository: MenusFakeRepository) = MenuRewriteEngine(repository)

    private fun menu(items: List<com.zickat.shopifymcpserver.menus.domain.models.MenuItemNode>) = MenuNode(
        id = "gid://shopify/Menu/1",
        handle = "main-menu",
        title = "Main menu",
        isDefault = false,
        items = items,
    )

    @Test
    fun `rewrite should refuse before any network write when the fetched menu carries an N5 sentinel`() {
        // given
        val repository = MenusFakeRepository().apply {
            fetchResponse = menu(MenusGraphQL.normalizeItems(MenuTreeFixtures.rawTreeWithSentinel())).right()
        }

        // when
        val outcome = engine(repository).rewrite(
            "store-1",
            "gid://shopify/Menu/1",
            parentSelector = { null },
            transform = { items, _ -> items },
        ).shouldBeRight()

        // then
        val failed = outcome.shouldBeInstanceOf<MenuRewriteOutcome.Failed>()
        failed.detail shouldContain "profondeur lue"
        repository.rewriteCalls.shouldBeEmpty()
    }

    @Test
    fun `rewrite should refuse before any network write when the transform silently drops an existing item`() {
        // given
        val repository = MenusFakeRepository().apply {
            fetchResponse = menu(MenuTreeFixtures.tree()).right()
        }

        // when
        val outcome = engine(repository).rewrite(
            "store-1",
            "gid://shopify/Menu/1",
            parentSelector = { null },
            transform = { items, _ -> items.drop(1) },
        ).shouldBeRight()

        // then
        val failed = outcome.shouldBeInstanceOf<MenuRewriteOutcome.Failed>()
        failed.detail shouldContain "supprimerait"
        repository.rewriteCalls.shouldBeEmpty()
    }
}
