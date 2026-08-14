package com.zickat.shopifymcpserver.menus.domain

import com.zickat.shopifymcpserver.menus.domain.models.MenuItemNode
import com.zickat.shopifymcpserver.menus.spi.shopify.MenusGraphQL
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

object MenuTreeFixtures {

    fun gid(n: Int): String = "gid://shopify/MenuItem/$n"

    fun rawItem(n: Int, title: String, items: JsonArray? = null): JsonObject = buildJsonObject {
        put("id", gid(n))
        put("title", title)
        put("type", "COLLECTION")
        put("url", JsonNull)
        put("resourceId", "gid://shopify/Collection/$n")
        put("tags", buildJsonArray {})
        if (items != null) put("items", items)
    }

    fun rawTree(): JsonArray = buildJsonArray {
        add(
            rawItem(
                501,
                "Guides",
                items = buildJsonArray {
                    add(
                        rawItem(
                            502,
                            "Sacoches & rangement",
                            items = buildJsonArray {
                                add(
                                    rawItem(
                                        503,
                                        "Sac de cadre",
                                        items = buildJsonArray { add(rawItem(504, "Détail")) },
                                    ),
                                )
                                add(rawItem(505, "Sacoche de guidon", items = buildJsonArray {}))
                            },
                        ),
                    )
                    add(rawItem(506, "Bivouac et autonomie", items = buildJsonArray {}))
                },
            ),
        )
        add(rawItem(507, "Boutique", items = buildJsonArray {}))
    }

    fun rawTreeWithSentinel(): JsonArray {
        val sentinel = buildJsonArray { add(buildJsonObject { put("id", gid(508)) }) }
        return buildJsonArray {
            add(
                rawItem(
                    501,
                    "Guides",
                    items = buildJsonArray {
                        add(
                            rawItem(
                                502,
                                "Sacoches & rangement",
                                items = buildJsonArray {
                                    add(
                                        rawItem(
                                            503,
                                            "Sac de cadre",
                                            items = buildJsonArray { add(rawItem(504, "Détail", items = sentinel)) },
                                        ),
                                    )
                                    add(rawItem(505, "Sacoche de guidon", items = buildJsonArray {}))
                                },
                            ),
                        )
                        add(rawItem(506, "Bivouac et autonomie", items = buildJsonArray {}))
                    },
                ),
            )
            add(rawItem(507, "Boutique", items = buildJsonArray {}))
        }
    }

    fun tree(): List<MenuItemNode> = MenusGraphQL.normalizeItems(rawTree())

    val ALL_IDS: List<String> = listOf(501, 502, 503, 504, 505, 506, 507).map(::gid)
}
