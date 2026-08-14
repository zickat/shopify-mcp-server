package com.zickat.shopifymcpserver.menus.spi.shopify

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

object MenusGraphQLFixtures {

    fun rawItem(id: String, title: String = "Item", items: JsonElement? = null): JsonElement = buildJsonObject {
        put("id", id)
        put("title", title)
        put("type", "COLLECTION")
        put("url", JsonNull)
        put("resourceId", "gid://shopify/Collection/1")
        put("tags", buildJsonArray {})
        if (items != null) put("items", items)
    }

    fun rawItemWithoutItemsKey(id: String, title: String = "Item"): JsonElement = buildJsonObject {
        put("id", id)
        put("title", title)
        put("type", "COLLECTION")
        put("url", JsonNull)
        put("resourceId", "gid://shopify/Collection/1")
        put("tags", buildJsonArray {})
    }

    fun menuNode(
        id: String = "gid://shopify/Menu/1",
        handle: String = "main-menu",
        title: String = "Main menu",
        isDefault: Boolean = false,
        items: JsonElement = buildJsonArray {},
    ): JsonElement = buildJsonObject {
        put("id", id)
        put("handle", handle)
        put("title", title)
        put("isDefault", isDefault)
        put("items", items)
    }

    fun edge(node: JsonElement): JsonElement = buildJsonObject { put("node", node) }

    fun page(edges: List<JsonElement>, hasNextPage: Boolean = false, endCursor: String? = null): JsonElement =
        buildJsonObject {
            put(
                "menus",
                buildJsonObject {
                    put(
                        "pageInfo",
                        buildJsonObject {
                            put("hasNextPage", hasNextPage)
                            put("endCursor", endCursor?.let { JsonPrimitive(it) } ?: JsonNull)
                        },
                    )
                    put("edges", buildJsonArray { edges.forEach { add(it) } })
                },
            )
        }
}
