package com.zickat.shopifymcpserver.menus.domain

import com.zickat.shopifymcpserver.menus.domain.models.MenuNode
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldStartWith
import org.junit.jupiter.api.Test

class MenuTreeRendererTest {

    @Test
    fun `R1 - render should carry an invariant item_id suffix a single regex extracts for every id`() {
        // given
        val render = MenuTreeRenderer.renderMenuTree(MenuTreeFixtures.tree(), 4)
        val text = render.lines.joinToString("\n")

        // when
        val extracted = Regex("— item_id: (gid://shopify/MenuItem/\\d+)$", RegexOption.MULTILINE)
            .findAll(text)
            .map { it.groupValues[1] }
            .toList()

        // then
        extracted.sorted() shouldBe MenuTreeFixtures.ALL_IDS.sorted()
        render.hiddenByDepth shouldBe 0
        render.hiddenByCap shouldBe 0
        render.hasUnreadableDepth shouldBe false
        render.lines[0] shouldStartWith "    1. Guides"
        render.lines[1] shouldStartWith "        1.1. Sacoches"
        render.lines[2] shouldStartWith "            1.1.1. Sac de cadre"
    }

    @Test
    fun `R2 - depth=1 should hide with an exact count, and an N5 should be flagged instead of counted`() {
        // given
        val render = MenuTreeRenderer.renderMenuTree(MenuTreeFixtures.tree(), 1)

        // then
        render.lines.size shouldBe 2
        render.hiddenByDepth shouldBe 5

        // and
        val sentinel = MenuTreeRenderer.renderMenuTree(MenuTree.normalizeItems(MenuTreeFixtures.rawTreeWithSentinel()), 4)
        sentinel.hasUnreadableDepth shouldBe true
        sentinel.hiddenByDepth shouldBe 0
    }

    @Test
    fun `formatMenuBlock should render the empty-menu placeholder when a menu has no items`() {
        // given
        val menu = MenuNode(id = "gid://shopify/Menu/1", handle = "empty-menu", title = "Vide", isDefault = false, items = emptyList())

        // when
        val block = MenuTreeRenderer.formatMenuBlock(menu, 4)

        // then
        block shouldContain "(aucun item)"
    }

    @Test
    fun `formatMenuBlock should flag a default menu in its header`() {
        // given
        val menu = MenuNode(
            id = "gid://shopify/Menu/2",
            handle = "main-menu",
            title = "Menu principal",
            isDefault = true,
            items = MenuTreeFixtures.tree(),
        )

        // when
        val block = MenuTreeRenderer.formatMenuBlock(menu, 4)

        // then
        block shouldContain ", par défaut"
    }

    @Test
    fun `withWarnings should leave the text untouched when there are no warnings`() {
        MenuTreeRenderer.withWarnings("ok", emptyList()) shouldBe "ok"
    }

    @Test
    fun `withWarnings should append every warning after a blank line`() {
        MenuTreeRenderer.withWarnings("ok", listOf("warning A", "warning B")) shouldBe "ok\n\nwarning A\n\nwarning B"
    }

    @Test
    fun `mutationErrorMessage should prefix the underlying error message`() {
        MenuTreeRenderer.mutationErrorMessage("Ajout", MenuItemValidationError("refusé")) shouldBe "Ajout : refusé"
    }
}
