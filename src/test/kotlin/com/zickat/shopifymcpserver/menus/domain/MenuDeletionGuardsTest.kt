package com.zickat.shopifymcpserver.menus.domain

import com.zickat.shopifymcpserver.menus.domain.models.MenuNode
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import org.junit.jupiter.api.Test

class MenuDeletionGuardsTest {

    private fun menu(isDefault: Boolean = false, handle: String = "main-menu", items: List<com.zickat.shopifymcpserver.menus.domain.models.MenuItemNode> = emptyList()) =
        MenuNode(
            id = "gid://shopify/Menu/199869530456",
            handle = handle,
            title = "Menu principal",
            isDefault = isDefault,
            items = items,
        )

    @Test
    fun `21 - a default menu should be refused in every case, confirmation never overrides it`() {
        // given / when / then
        MenuDeletionGuards.collectMenuDeletionRefusals(menu(isDefault = true), confirmNonEmptyDeletion = true)
            .size shouldBe 1
        MenuDeletionGuards.collectMenuDeletionRefusals(menu(isDefault = true), confirmNonEmptyDeletion = false)
            .size shouldBe 1
        MenuDeletionGuards.collectMenuDeletionRefusals(menu(isDefault = true), confirmNonEmptyDeletion = true)[0] shouldContain "PAR DÉFAUT"
    }

    @Test
    fun `21 - a non-empty ordinary menu should be refused unless confirmed, and only that guard should fall`() {
        // given
        val nonEmpty = menu(items = MenuTreeFixtures.tree())

        // when / then
        MenuDeletionGuards.collectMenuDeletionRefusals(nonEmpty, confirmNonEmptyDeletion = false).size shouldBe 1
        MenuDeletionGuards.collectMenuDeletionRefusals(nonEmpty, confirmNonEmptyDeletion = true).size shouldBe 0
    }

    @Test
    fun `21 - the two guards should be cumulative, and confirmation should only ever lift the second`() {
        // given
        val defaultAndNonEmpty = menu(isDefault = true, items = MenuTreeFixtures.tree())

        // when / then
        MenuDeletionGuards.collectMenuDeletionRefusals(defaultAndNonEmpty, confirmNonEmptyDeletion = false)
            .size shouldBe 2
        MenuDeletionGuards.collectMenuDeletionRefusals(defaultAndNonEmpty, confirmNonEmptyDeletion = true)
            .size shouldBe 1
    }

    @Test
    fun `21 - an ordinary empty menu should delete without confirmation`() {
        // given
        val ordinary = menu(handle = "zz-test")

        // when / then
        MenuDeletionGuards.collectMenuDeletionRefusals(ordinary, confirmNonEmptyDeletion = false).size shouldBe 0
    }
}
