package com.zickat.shopifymcpserver.menus.domain

import com.zickat.shopifymcpserver.menus.domain.MenuTreeFixtures.gid
import com.zickat.shopifymcpserver.menus.domain.models.MenuItemNode
import com.zickat.shopifymcpserver.menus.domain.models.MenuItemType
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import org.junit.jupiter.api.Test

class MenuTransformsTest {

    private fun fresh(title: String, type: MenuItemType = MenuItemType.PAGE) = MenuItemNode(
        id = "",
        title = title,
        type = type,
        url = null,
        resourceId = null,
        tags = emptyList(),
        items = emptyList(),
    )

    @Test
    fun `5 - insertion under an N2 should preserve the whole tree and land in the right N3 sibling list`() {
        // given
        val before = MenuTreeFixtures.tree()
        val newItem = fresh("Nouveau")

        // when
        val after = MenuTree.mapChildList(before, gid(502), MenuTransforms.makeInsertTransform(newItem, null))

        // then
        MenuTree.collectItemIds(after).sorted() shouldBe MenuTreeFixtures.ALL_IDS.sorted()
        val parent = MenuTree.findItemPath(after, gid(502))!!.last()
        parent.items.map { it.title } shouldBe listOf("Sac de cadre", "Sacoche de guidon", "Nouveau")
        MenuTree.maxDepth(after) shouldBe 4
        after[1].id shouldBe gid(507)
        before[0].items[0].items.size shouldBe 2
    }

    @Test
    fun `7 - insertion under an N3 should be refused (D2 rule A)`() {
        // given
        val before = MenuTreeFixtures.tree()
        val newItem = fresh("Trop profond")

        // when
        val error = shouldThrow<MenuItemValidationError> {
            MenuTree.mapChildList(before, gid(503), MenuTransforms.makeInsertTransform(newItem, null))
        }

        // then
        error.message shouldContain "Ajout refusé"
    }

    @Test
    fun `8 - reordering an N2 sibling list should apply the permutation and keep N3 subtrees intact`() {
        // given
        val before = MenuTreeFixtures.tree()

        // when
        val after = MenuTree.mapChildList(
            before,
            gid(501),
            MenuTransforms.makeReorderTransform(listOf(gid(506), gid(502)), "enfants de Guides"),
        )

        // then
        after[0].items.map { it.id } shouldBe listOf(gid(506), gid(502))
        val moved = after[0].items[1]
        moved.items.map { it.id } shouldBe listOf(gid(503), gid(505))
        moved.items[0].items.map { it.id } shouldBe listOf(gid(504))
        MenuTree.collectItemIds(after).sorted() shouldBe MenuTreeFixtures.ALL_IDS.sorted()
        MenuIntegrityGuards.assertNoSilentLoss(before, after, emptyList())
    }

    @Test
    fun `9 - removing a node with children without with_children should be refused, naming the child count`() {
        // given
        val before = MenuTreeFixtures.tree()

        // when
        val error = shouldThrow<MenuItemValidationError> {
            MenuTree.mapChildList(before, gid(501), MenuTransforms.makeRemoveTransform(gid(502), false))
        }

        // then
        error.message shouldContain "Retrait refusé"
        error.message shouldContain "2 sous-item(s)"
    }

    @Test
    fun `9 - removing a node with children opting into with_children should drop the whole declared subtree`() {
        // given
        val before = MenuTreeFixtures.tree()
        val declared = MenuTree.collectItemIds(listOf(MenuTree.findItemPath(before, gid(502))!!.last()))

        // when
        val after = MenuTree.mapChildList(before, gid(501), MenuTransforms.makeRemoveTransform(gid(502), true))

        // then
        declared.sorted() shouldBe listOf(502, 503, 504, 505).map(::gid).sorted()
        MenuIntegrityGuards.assertNoSilentLoss(before, after, declared)
    }

    @Test
    fun `16 - update_menu_item title only on an N2 with children should preserve item_id and subtree`() {
        // given
        val before = MenuTreeFixtures.tree()

        // when
        val after = MenuTree.mapChildList(
            before,
            gid(501),
            MenuTransforms.makeUpdateItemTransform(gid(502), MenuItemChanges(title = "Sacoches (renommé)")),
        )

        // then
        val node = MenuTree.findItemPath(after, gid(502))!!.last()
        node.title shouldBe "Sacoches (renommé)"
        node.id shouldBe gid(502)
        node.items.map { it.id } shouldBe listOf(gid(503), gid(505))
        node.items[0].items.map { it.id } shouldBe listOf(gid(504))
        MenuTree.collectItemIds(after).sorted() shouldBe MenuTreeFixtures.ALL_IDS.sorted()
        MenuIntegrityGuards.assertNoSilentLoss(before, after, emptyList())
        before[0].items[0].title shouldBe "Sacoches & rangement"
    }

    @Test
    fun `17 - update_menu_item title only on a FRONTPAGE with tags should stay deep-equal outside title`() {
        // given
        val accueil = MenuItemNode(
            id = gid(600),
            title = "Accueil",
            type = MenuItemType.FRONTPAGE,
            url = null,
            resourceId = null,
            tags = listOf("promo", "header"),
            items = emptyList(),
        )
        val before = listOf(accueil)

        // when
        val after = MenuTree.mapChildList(
            before,
            null,
            MenuTransforms.makeUpdateItemTransform(gid(600), MenuItemChanges(title = "Bienvenue")),
        )

        // then
        val updated = after[0]
        updated.title shouldBe "Bienvenue"
        updated shouldBe accueil.copy(title = "Bienvenue")
        updated.tags shouldBe listOf("promo", "header")
        updated.type shouldBe MenuItemType.FRONTPAGE
    }

    @Test
    fun `18 - update_menu_item without title, resource_id nor url should be refused`() {
        // given
        val before = MenuTreeFixtures.tree()

        // when
        val error = shouldThrow<MenuItemValidationError> {
            MenuTree.mapChildList(before, gid(501), MenuTransforms.makeUpdateItemTransform(gid(502), MenuItemChanges()))
        }

        // then
        error.message shouldContain "aucun champ à modifier"
    }
}
