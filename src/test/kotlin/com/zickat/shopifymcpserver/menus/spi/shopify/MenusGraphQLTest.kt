package com.zickat.shopifymcpserver.menus.spi.shopify

import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import org.junit.jupiter.api.Test

class MenusGraphQLTest {

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
