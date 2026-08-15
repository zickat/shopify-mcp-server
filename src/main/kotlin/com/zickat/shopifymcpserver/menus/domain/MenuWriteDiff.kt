package com.zickat.shopifymcpserver.menus.domain

import com.zickat.shopifymcpserver.menus.domain.models.MenuItemNode

typealias ParentSelector = (List<MenuItemNode>) -> String?

object MenuWriteDiff {

    fun diffAfterWrite(
        before: List<MenuItemNode>,
        persisted: List<MenuItemNode>,
        expectedRemovals: List<String>,
    ): List<String> {
        val declared = expectedRemovals.toSet()
        val persistedIds = MenuTree.collectItemIds(persisted).toSet()
        val missing = MenuTree.collectItemIds(before).filter { it !in declared && it !in persistedIds }
        if (missing.isEmpty()) return emptyList()

        val snapshot = MenuTreeRenderer.renderMenuTree(before, MAX_REEMITTABLE_DEPTH).lines.joinToString("\n")
        return listOf(
            "⚠ ÉCART APRÈS ÉCRITURE — ${missing.size} item(s) présent(s) avant l'opération et NON déclaré(s) " +
                "comme retirés sont absents de l'arbre renvoyé par Shopify : ${missing.joinToString(", ")}. La mutation a " +
                "déjà eu lieu et ne peut plus être annulée automatiquement. Plan de restauration manuel — arbre " +
                "complet AVANT écriture :\n$snapshot",
        )
    }

    fun locateItem(tree: List<MenuItemNode>, itemId: String): List<MenuItemNode> =
        MenuTree.findItemPath(tree, itemId)
            ?: throw MenuItemValidationError("Item introuvable dans ce menu : $itemId")

    fun parentOf(itemId: String): ParentSelector = { tree ->
        val path = locateItem(tree, itemId)
        if (path.size >= 2) path[path.size - 2].id else null
    }
}
