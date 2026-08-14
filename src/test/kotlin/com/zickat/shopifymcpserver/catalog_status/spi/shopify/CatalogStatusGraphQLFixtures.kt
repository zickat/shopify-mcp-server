package com.zickat.shopifymcpserver.catalog_status.spi.shopify

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

object CatalogStatusGraphQLFixtures {

    private fun metafield(value: String?): JsonElement = if (value == null) {
        JsonNull
    } else {
        buildJsonObject { put("value", value) }
    }

    fun resourceNode(
        id: String,
        title: String = "Title",
        handle: String = "handle",
        contentStatus: String? = null,
        summary: String? = null,
        secondarySignal: String? = null,
    ): JsonElement = buildJsonObject {
        put("id", id)
        put("title", title)
        put("handle", handle)
        put("contentStatus", metafield(contentStatus))
        put("summary", metafield(summary))
        put("secondarySignal", metafield(secondarySignal))
    }

    fun edge(node: JsonElement): JsonElement = buildJsonObject { put("node", node) }

    fun page(pluralField: String, edges: List<JsonElement>, hasNextPage: Boolean = false, endCursor: String? = null): JsonElement =
        buildJsonObject {
            put(
                pluralField,
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
