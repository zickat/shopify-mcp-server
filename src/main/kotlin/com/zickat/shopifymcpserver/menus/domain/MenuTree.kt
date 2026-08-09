package com.zickat.shopifymcpserver.menus.domain

import com.zickat.shopifymcpserver.menus.domain.models.MenuItemNode
import com.zickat.shopifymcpserver.menus.domain.models.MenuItemType
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

private const val ITEM_FIELDS = "id title type url resourceId tags"

const val MENU_ITEMS_TREE =
    "$ITEM_FIELDS items { $ITEM_FIELDS items { $ITEM_FIELDS items { $ITEM_FIELDS items { id } } } }"

object MenuTree {

    fun normalizeItems(raw: JsonElement?): List<MenuItemNode> {
        val array = raw as? JsonArray ?: return emptyList()
        return array.map(::normalizeItem)
    }

    fun findItemPath(items: List<MenuItemNode>, itemId: String): List<MenuItemNode>? {
        for (item in items) {
            if (item.id == itemId) return listOf(item)
            val deeper = findItemPath(item.items, itemId)
            if (deeper != null) return listOf(item) + deeper
        }
        return null
    }

    fun mapChildList(
        items: List<MenuItemNode>,
        parentId: String?,
        transform: (siblings: List<MenuItemNode>, siblingDepth: Int) -> List<MenuItemNode>,
    ): List<MenuItemNode> {
        if (parentId == null) return transform(items, 1)

        var found = false

        fun walk(nodes: List<MenuItemNode>, depth: Int): List<MenuItemNode> =
            nodes.map { node ->
                if (node.id == parentId) {
                    found = true
                    node.copy(items = transform(node.items, depth + 1))
                } else {
                    node.copy(items = walk(node.items, depth + 1))
                }
            }

        val rebuilt = walk(items, 1)
        if (!found) {
            throw MenuItemValidationError("Item parent introuvable dans ce menu : $parentId")
        }
        return rebuilt
    }

    fun collectItemIds(items: List<MenuItemNode>): List<String> {
        val ids = mutableListOf<String>()
        for (item in items) {
            if (item.id.isNotEmpty()) ids.add(item.id)
            ids.addAll(collectItemIds(item.items))
        }
        return ids
    }

    fun maxDepth(items: List<MenuItemNode>): Int {
        var deepest = 0
        for (item in items) {
            val depth = 1 + maxDepth(item.items)
            if (depth > deepest) deepest = depth
        }
        return deepest
    }

    private fun normalizeItem(raw: JsonElement): MenuItemNode {
        val obj = raw as? JsonObject ?: JsonObject(emptyMap())
        return MenuItemNode(
            id = obj["id"]?.jsonPrimitive?.contentOrNull.orEmpty(),
            title = obj["title"]?.jsonPrimitive?.contentOrNull.orEmpty(),
            type = obj["type"]?.jsonPrimitive?.contentOrNull
                ?.let { typeName -> runCatching { MenuItemType.valueOf(typeName) }.getOrNull() }
                ?: MenuItemType.HTTP,
            url = obj["url"]?.jsonPrimitive?.contentOrNull,
            resourceId = obj["resourceId"]?.jsonPrimitive?.contentOrNull,
            tags = (obj["tags"] as? JsonArray)?.mapNotNull { it.jsonPrimitive.contentOrNull }.orEmpty(),
            items = normalizeItems(obj["items"]),
        )
    }
}
