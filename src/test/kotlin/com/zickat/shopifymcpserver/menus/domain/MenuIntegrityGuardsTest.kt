package com.zickat.shopifymcpserver.menus.domain

import com.zickat.shopifymcpserver.menus.domain.MenuTreeFixtures.gid
import com.zickat.shopifymcpserver.menus.domain.models.MenuItemNode
import com.zickat.shopifymcpserver.menus.domain.models.MenuItemType
import com.zickat.shopifymcpserver.menus.spi.shopify.MenusGraphQL
import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import org.junit.jupiter.api.Test

class MenuIntegrityGuardsTest {

    @Test
    fun `assertNoSilentLoss should throw when a real reconstruction drops a subtree`() {
        // given
        val before = MenuTreeFixtures.tree()
        val after = MenuTree.mapChildList(before, null) { siblings, _ ->
            siblings.map { node -> if (node.id == gid(501)) node.copy(items = emptyList()) else node }
        }

        // when
        val error = shouldThrow<MenuItemValidationError> {
            MenuIntegrityGuards.assertNoSilentLoss(before, after, emptyList())
        }

        // then
        error.message shouldContain "supprimerait"
    }

    @Test
    fun `assertNoSilentLoss should throw on a duplicated item_id even though invariants (1) and (2) stay satisfied`() {
        // given
        val before = MenuTreeFixtures.tree()
        val after = before + before[1]
        val beforeSet = MenuTree.collectItemIds(before).toSet()
        val afterSet = MenuTree.collectItemIds(after).toSet()

        // then (invariants (1) and (2) alone cannot see this case)
        (afterSet - beforeSet).shouldBeEmpty()
        (beforeSet - afterSet).shouldBeEmpty()

        // when
        val error = shouldThrow<MenuItemValidationError> {
            MenuIntegrityGuards.assertNoSilentLoss(before, after, emptyList())
        }

        // then
        error.message shouldContain "duplique"
    }

    @Test
    fun `assertNoUnreadableDepth should refuse a tree carrying an N5 sentinel but accept a 4-level tree`() {
        // given
        val withSentinel = MenusGraphQL.normalizeItems(MenuTreeFixtures.rawTreeWithSentinel())
        MenuTree.maxDepth(withSentinel) shouldBe 5

        // when
        val error = shouldThrow<MenuItemValidationError> {
            MenuIntegrityGuards.assertNoUnreadableDepth(withSentinel)
        }

        // then
        error.message shouldContain "Modification refusée"
        shouldNotThrowAny { MenuIntegrityGuards.assertNoUnreadableDepth(MenuTreeFixtures.tree()) }
    }

    @Test
    fun `collectItemIds should ignore empty ids so invariant (0) does not fire on two brand-new items`() {
        // given
        val before = MenuTreeFixtures.tree()
        fun fresh(title: String) = MenuItemNode(
            id = "",
            title = title,
            type = MenuItemType.PAGE,
            url = null,
            resourceId = null,
            tags = emptyList(),
            items = emptyList(),
        )
        val after = MenuTree.mapChildList(before, null) { siblings, _ -> siblings + fresh("A") + fresh("B") }

        // when / then
        MenuTree.collectItemIds(after).sorted() shouldBe MenuTreeFixtures.ALL_IDS.sorted()
        shouldNotThrowAny { MenuIntegrityGuards.assertNoSilentLoss(before, after, emptyList()) }
        shouldNotThrowAny { MenuIntegrityGuards.assertDepthNotWorsened(before, after) }
    }

    @Test
    fun `identity transform should satisfy invariants (0)(1)(2), and a faulty identity should still be caught`() {
        // given
        val before = MenuTreeFixtures.tree()
        val after = MenuTree.mapChildList(before, null) { items, _ -> items }

        // then
        MenuTree.collectItemIds(after) shouldBe MenuTree.collectItemIds(before)
        after.map { MenuWriteDiff.serializeItem(it) } shouldBe before.map { MenuWriteDiff.serializeItem(it) }
        shouldNotThrowAny { MenuIntegrityGuards.assertNoSilentLoss(before, after, emptyList()) }
        shouldNotThrowAny { MenuIntegrityGuards.assertDepthNotWorsened(before, after) }

        val error = shouldThrow<MenuItemValidationError> {
            MenuIntegrityGuards.assertNoSilentLoss(before, before.drop(1), emptyList())
        }
        error.message shouldContain "supprimerait"
    }

    @Test
    fun `assertOnlyDeclaredFieldsChanged should catch a lost tags field that the set invariants let through`() {
        // given
        val original = MenuItemNode(
            id = gid(600),
            title = "Accueil",
            type = MenuItemType.FRONTPAGE,
            url = null,
            resourceId = null,
            tags = listOf("promo"),
            items = emptyList(),
        )
        val rebuilt = MenuItemNode(
            id = original.id,
            title = "Bienvenue",
            type = original.type,
            url = original.url,
            resourceId = original.resourceId,
            tags = emptyList(),
            items = original.items,
        )

        // then (D5's set invariants stay green on this case)
        shouldNotThrowAny { MenuIntegrityGuards.assertNoSilentLoss(listOf(original), listOf(rebuilt), emptyList()) }
        MenuTree.collectItemIds(listOf(rebuilt)) shouldBe MenuTree.collectItemIds(listOf(original))

        // when
        val error = shouldThrow<MenuItemValidationError> {
            MenuIntegrityGuards.assertOnlyDeclaredFieldsChanged(original, rebuilt, listOf("title"))
        }

        // then
        error.message shouldContain "champ(s) non déclaré(s)"
        error.message shouldContain "tags"
        shouldNotThrowAny {
            MenuIntegrityGuards.assertOnlyDeclaredFieldsChanged(original, original.copy(title = "Bienvenue"), listOf("title"))
        }
    }
}
