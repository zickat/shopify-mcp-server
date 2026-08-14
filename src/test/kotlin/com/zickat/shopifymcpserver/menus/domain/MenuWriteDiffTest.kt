package com.zickat.shopifymcpserver.menus.domain

import com.zickat.shopifymcpserver.menus.domain.MenuTreeFixtures.gid
import com.zickat.shopifymcpserver.menus.spi.shopify.MenusGraphQL
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Test

class MenuWriteDiffTest {

    @Test
    fun `serializeItem should re-emit every id of the normalized tree without error`() {
        // given
        val serialized = MenuTreeFixtures.tree().map { MenuWriteDiff.serializeItem(it) }

        // when
        val ids = idsOfSerialized(serialized)

        // then
        ids.sorted() shouldBe MenuTreeFixtures.ALL_IDS.sorted()
    }

    @Test
    fun `diffAfterWrite should normalize the persisted payload, ignore declared removals and name the lost id with a full N4 snapshot`() {
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

    private fun idsOfSerialized(nodes: List<JsonObject>): List<String> {
        val ids = mutableListOf<String>()
        for (node in nodes) {
            node["id"]?.jsonPrimitive?.contentOrNull?.let { ids.add(it) }
            (node["items"] as? JsonArray)?.let { items ->
                ids.addAll(idsOfSerialized(items.map { child -> child as JsonObject }))
            }
        }
        return ids
    }
}
