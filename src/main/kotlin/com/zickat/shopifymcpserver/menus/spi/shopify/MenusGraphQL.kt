package com.zickat.shopifymcpserver.menus.spi.shopify

import com.zickat.shopifymcpserver.menus.domain.models.MenuItemNode
import com.zickat.shopifymcpserver.menus.domain.models.MenuItemType
import com.zickat.shopifymcpserver.menus.domain.models.MenuNode
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

private const val ITEM_FIELDS = "id title type url resourceId tags"

internal const val MENU_ITEMS_TREE =
    "$ITEM_FIELDS items { $ITEM_FIELDS items { $ITEM_FIELDS items { $ITEM_FIELDS items { id } } } }"

internal object MenusGraphQL {

    val LIST_MENUS_QUERY =
        "query ListMenus(\$query: String, \$cursor: String) {\n" +
            "            menus(first: 50, after: \$cursor, query: \$query) {\n" +
            "              pageInfo { hasNextPage endCursor }\n" +
            "              edges { node { id handle title isDefault items { $MENU_ITEMS_TREE } } }\n" +
            "            }\n" +
            "          }"

    val FETCH_MENU_QUERY =
        "query FetchMenu(\$id: ID!) {\n" +
            "      menu(id: \$id) {\n" +
            "        id handle title isDefault\n" +
            "        items { $MENU_ITEMS_TREE }\n" +
            "      }\n" +
            "    }"

    val UPDATE_MENU_MUTATION =
        "mutation UpdateMenu(\$id: ID!, \$title: String!, \$handle: String, \$items: [MenuItemUpdateInput!]!) {\n" +
            "      menuUpdate(id: \$id, title: \$title, handle: \$handle, items: \$items) {\n" +
            "        menu { id items { $MENU_ITEMS_TREE } }\n" +
            "        userErrors { field message }\n" +
            "      }\n" +
            "    }"

    val CREATE_MENU_MUTATION =
        "mutation CreateMenu(\$title: String!, \$handle: String!, \$items: [MenuItemCreateInput!]!) {\n" +
            "            menuCreate(title: \$title, handle: \$handle, items: \$items) {\n" +
            "              menu { id handle title isDefault items { $MENU_ITEMS_TREE } }\n" +
            "              userErrors { field message }\n" +
            "            }\n" +
            "          }"

    val DELETE_MENU_MUTATION =
        "mutation DeleteMenu(\$id: ID!) {\n" +
            "            menuDelete(id: \$id) {\n" +
            "              deletedMenuId\n" +
            "              userErrors { field message }\n" +
            "            }\n" +
            "          }"

    fun listMenusVariables(query: String?, cursor: String?): JsonObject = buildJsonObject {
        put("query", query?.let { JsonPrimitive(it) } ?: JsonNull)
        put("cursor", cursor?.let { JsonPrimitive(it) } ?: JsonNull)
    }

    fun fetchMenuVariables(menuId: String): JsonObject = buildJsonObject { put("id", JsonPrimitive(menuId)) }

    fun updateMenuVariables(menuId: String, title: String, handle: String, items: List<MenuItemNode>): JsonObject = buildJsonObject {
        put("id", JsonPrimitive(menuId))
        put("title", JsonPrimitive(title))
        put("handle", JsonPrimitive(handle))
        put("items", buildJsonArray { items.forEach { add(serializeItemForWrite(it)) } })
    }

    fun createMenuVariables(handle: String, title: String, items: List<MenuItemNode>): JsonObject = buildJsonObject {
        put("title", JsonPrimitive(title))
        put("handle", JsonPrimitive(handle))
        put("items", buildJsonArray { items.forEach { add(serializeItemForWrite(it)) } })
    }

    fun deleteMenuVariables(menuId: String): JsonObject = buildJsonObject { put("id", JsonPrimitive(menuId)) }

    fun menuFromFetchResponse(response: JsonElement): MenuNode? =
        ((response as? JsonObject)?.get("menu") as? JsonObject)?.let(::nodeToMenu)

    fun menuUpdatePayload(response: JsonElement): JsonObject? = (response as? JsonObject)?.get("menuUpdate") as? JsonObject

    fun updatedItems(payload: JsonObject): List<MenuItemNode> = normalizeItems((payload["menu"] as? JsonObject)?.get("items"))

    fun menuCreatePayload(response: JsonElement): JsonObject? = (response as? JsonObject)?.get("menuCreate") as? JsonObject

    fun createdMenu(payload: JsonObject): MenuNode? = (payload["menu"] as? JsonObject)?.let(::nodeToMenu)

    fun menuDeletePayload(response: JsonElement): JsonObject? = (response as? JsonObject)?.get("menuDelete") as? JsonObject

    fun deletedMenuId(payload: JsonObject): String? = payload["deletedMenuId"]?.jsonPrimitive?.contentOrNull

    fun serializeItemForWrite(item: MenuItemNode): JsonObject = buildJsonObject {
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
        if (item.items.isNotEmpty()) put("items", buildJsonArray { item.items.forEach { add(serializeItemForWrite(it)) } })
    }

    fun menusConnection(response: JsonElement): JsonObject? = (response as? JsonObject)?.get("menus") as? JsonObject

    fun hasNextPage(connection: JsonObject): Boolean =
        (connection["pageInfo"] as? JsonObject)?.get("hasNextPage")?.jsonPrimitive?.boolean ?: false

    fun endCursor(connection: JsonObject): String? =
        (connection["pageInfo"] as? JsonObject)?.get("endCursor")?.jsonPrimitive?.contentOrNull

    fun menuNodes(connection: JsonObject): List<MenuNode> {
        val edges = (connection["edges"] as? JsonArray).orEmpty()
        return edges.mapNotNull { edge -> (edge as? JsonObject)?.get("node") as? JsonObject }.map(::nodeToMenu)
    }

    private fun nodeToMenu(node: JsonObject): MenuNode = MenuNode(
        id = node["id"]?.jsonPrimitive?.contentOrNull.orEmpty(),
        handle = node["handle"]?.jsonPrimitive?.contentOrNull.orEmpty(),
        title = node["title"]?.jsonPrimitive?.contentOrNull.orEmpty(),
        isDefault = node["isDefault"]?.jsonPrimitive?.boolean ?: false,
        items = normalizeItems(node["items"]),
    )

    fun normalizeItems(raw: JsonElement?): List<MenuItemNode> {
        val array = raw as? JsonArray ?: return emptyList()
        return array.map(::normalizeItem)
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
