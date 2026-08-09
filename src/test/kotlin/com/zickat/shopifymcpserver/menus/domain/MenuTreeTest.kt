package com.zickat.shopifymcpserver.menus.domain

import com.zickat.shopifymcpserver.menus.domain.MenuTreeFixtures.gid
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import org.junit.jupiter.api.Test

class MenuTreeTest {

    @Test
    fun `normalizeItems should turn a raw N4 node without an items key into an empty list without throwing`() {
        // given
        val normalized = MenuTree.normalizeItems(MenuTreeFixtures.rawTree())

        // when
        val n4Path = MenuTree.findItemPath(normalized, gid(504))

        // then
        n4Path.shouldNotBeNull()
        n4Path.last().items shouldBe emptyList()
        MenuTree.maxDepth(normalized) shouldBe 4
    }

    @Test
    fun `findItemPath should return the root-to-node chain and depth at N1, N2 and N3`() {
        // given
        val t = MenuTreeFixtures.tree()

        // when / then
        MenuTree.findItemPath(t, gid(501))!!.map { it.id } shouldBe listOf(gid(501))
        MenuTree.findItemPath(t, gid(502))!!.map { it.id } shouldBe listOf(gid(501), gid(502))
        val n3 = MenuTree.findItemPath(t, gid(503))!!
        n3.map { it.id } shouldBe listOf(gid(501), gid(502), gid(503))
        n3.size shouldBe 3
    }

    @Test
    fun `findItemPath should return null when the id is absent`() {
        // given
        val t = MenuTreeFixtures.tree()

        // when
        val path = MenuTree.findItemPath(t, gid(999))

        // then
        path.shouldBeNull()
    }

    @Test
    fun `mapChildList should throw MenuItemValidationError when parentId is unknown`() {
        // given
        val t = MenuTreeFixtures.tree()

        // when
        val error = shouldThrow<MenuItemValidationError> {
            MenuTree.mapChildList(t, gid(999)) { siblings, _ -> siblings }
        }

        // then
        error.message shouldContain "Item parent introuvable"
    }

    @Test
    fun `MENU_ITEMS_TREE fragment should re-emit exactly 4 levels and carry the N5 sentinel`() {
        // given
        val fields = "id title type url resourceId tags"

        // when
        val fieldOccurrences = MENU_ITEMS_TREE.split(fields).size - 1
        val nestings = MENU_ITEMS_TREE.split("items {").size - 1

        // then
        fieldOccurrences shouldBe 4
        nestings shouldBe 4
        MENU_ITEMS_TREE shouldContain "items { id }"
    }
}
