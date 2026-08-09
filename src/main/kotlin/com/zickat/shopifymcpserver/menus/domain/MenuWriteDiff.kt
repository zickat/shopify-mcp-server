package com.zickat.shopifymcpserver.menus.domain

import com.zickat.shopifymcpserver.menus.domain.models.MenuItemNode
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject

typealias ParentSelector = (List<MenuItemNode>) -> String?

private val prettyJson = Json { prettyPrint = true }

object MenuWriteDiff {

    fun serializeItem(item: MenuItemNode): JsonObject = buildJsonObject {
        put("title", JsonPrimitive(item.title))
        put("type", JsonPrimitive(item.type.name))
        put("tags", buildJsonArray { item.tags.forEach { add(JsonPrimitive(it)) } })
        if (item.id.isNotEmpty()) put("id", JsonPrimitive(item.id))
        val resourceId = item.resourceId
        val url = item.url
        if (resourceId != null) {
            put("resourceId", JsonPrimitive(resourceId))
        } else if (url != null) {
            put("url", JsonPrimitive(url))
        }
        if (item.items.isNotEmpty()) {
            put("items", buildJsonArray { item.items.forEach { add(serializeItem(it)) } })
        }
    }

    fun diffAfterWrite(
        before: List<MenuItemNode>,
        persisted: List<MenuItemNode>,
        expectedRemovals: List<String>,
    ): List<String> {
        val declared = expectedRemovals.toSet()
        val persistedIds = MenuTree.collectItemIds(persisted).toSet()
        val missing = MenuTree.collectItemIds(before).filter { it !in declared && it !in persistedIds }
        if (missing.isEmpty()) return emptyList()

        val snapshot: JsonArray = buildJsonArray { before.forEach { add(serializeItem(it)) } }
        val snapshotText = prettyJson.encodeToString(JsonArray.serializer(), snapshot)
        return listOf(
            "⚠ ÉCART APRÈS ÉCRITURE — ${missing.size} item(s) présent(s) avant l'opération et NON déclaré(s) " +
                "comme retirés sont absents de l'arbre renvoyé par Shopify : ${missing.joinToString(", ")}. La mutation a " +
                "déjà eu lieu et ne peut plus être annulée automatiquement. Plan de restauration manuel — arbre " +
                "complet AVANT écriture :\n$snapshotText",
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
