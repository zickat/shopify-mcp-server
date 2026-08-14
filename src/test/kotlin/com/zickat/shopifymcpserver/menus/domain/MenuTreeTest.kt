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
    fun `maxDepth should return the deepest normalized branch, N4 included`() {
        // given
        val t = MenuTreeFixtures.tree()

        // when
        val n4Path = MenuTree.findItemPath(t, gid(504))

        // then
        n4Path.shouldNotBeNull()
        n4Path.last().items shouldBe emptyList()
        MenuTree.maxDepth(t) shouldBe 4
    }
}
