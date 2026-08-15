package com.zickat.shopifymcpserver.menus.domain

import com.zickat.shopifymcpserver.menus.domain.MenuTreeFixtures.gid
import com.zickat.shopifymcpserver.menus.spi.shopify.MenusGraphQL
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import org.junit.jupiter.api.Test

class MenuWriteDiffTest {

    @Test
    fun `diffAfterWrite should normalize the persisted payload, ignore declared removals and name the lost id with a restoration snapshot`() {
        // given
        val before = MenuTreeFixtures.tree()

        // when / then — faithful round trip: no discrepancy
        val persisted = MenusGraphQL.normalizeItems(MenuTreeFixtures.rawTree())
        MenuWriteDiff.diffAfterWrite(before, persisted, emptyList()).shouldBeEmpty()

        // when / then — declared removal: no warning
        val declared = MenuTree.collectItemIds(listOf(MenuTree.findItemPath(before, gid(502))!!.last()))
        val afterRemoval = MenusGraphQL.normalizeItems(MenuTreeFixtures.rawTree()).filterNot { it.id == gid(501) }
        MenuWriteDiff.diffAfterWrite(before, afterRemoval, declared + listOf(gid(501), gid(506))).shouldBeEmpty()

        // when / then — undeclared loss: one warning naming the id, carrying the pre-write snapshot
        val lossy = MenusGraphQL.normalizeItems(MenuTreeFixtures.rawTree()).filterNot { it.id == gid(507) }
        val warnings = MenuWriteDiff.diffAfterWrite(before, lossy, emptyList())
        warnings shouldHaveSize 1
        warnings[0] shouldContain gid(507)
        warnings[0] shouldContain "Plan de restauration manuel"
        warnings[0] shouldContain gid(504)
    }

    @Test
    fun `locateItem should find an item at any depth or refuse naming it`() {
        // given
        val tree = MenuTreeFixtures.tree()

        // when
        val path = MenuWriteDiff.locateItem(tree, gid(504))

        // then
        path.map { it.id } shouldBe listOf(gid(501), gid(502), gid(503), gid(504))
    }

    @Test
    fun `parentOf should resolve the parent id of a nested item, and null at the root`() {
        // given
        val tree = MenuTreeFixtures.tree()

        // when / then
        MenuWriteDiff.parentOf(gid(504))(tree) shouldBe gid(503)
        MenuWriteDiff.parentOf(gid(501))(tree) shouldBe null
    }
}
