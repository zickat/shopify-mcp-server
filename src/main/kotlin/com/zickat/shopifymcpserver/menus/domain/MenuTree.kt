package com.zickat.shopifymcpserver.menus.domain

import com.zickat.shopifymcpserver.menus.domain.models.MenuItemNode

object MenuTree {

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
}
