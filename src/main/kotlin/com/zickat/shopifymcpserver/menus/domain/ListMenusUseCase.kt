package com.zickat.shopifymcpserver.menus.domain

import arrow.core.Either
import arrow.core.raise.either
import com.zickat.shopifymcpserver.menus.domain.models.MenuNode
import com.zickat.shopifymcpserver.menus.exposed_interface.model.ListMenusResult
import com.zickat.shopifymcpserver.shared_kernel.MAX_SEARCH_PAGES
import com.zickat.shopifymcpserver.shared_kernel.TechnicalError
import com.zickat.shopifymcpserver.shared_kernel.UseCaseError
import com.zickat.shopifymcpserver.shopify.exposed_interface.ShopifyAdminGateway
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import org.springframework.stereotype.Component

private const val DEFAULT_DISPLAY_DEPTH = 3

private val LIST_MENUS_QUERY =
    "query ListMenus(\$query: String, \$cursor: String) {\n" +
        "            menus(first: 50, after: \$cursor, query: \$query) {\n" +
        "              pageInfo { hasNextPage endCursor }\n" +
        "              edges {\n" +
        "                node {\n" +
        "                  id\n" +
        "                  handle\n" +
        "                  title\n" +
        "                  isDefault\n" +
        "                  items { $MENU_ITEMS_TREE }\n" +
        "                }\n" +
        "              }\n" +
        "            }\n" +
        "          }"

@Component
class ListMenusUseCase(
    private val shopifyAdminGateway: ShopifyAdminGateway,
) {

    fun execute(storeId: String, query: String?, depth: Int?): Either<UseCaseError, ListMenusResult> = either {
        val searchQuery = query?.trim()?.takeIf { it.isNotEmpty() }
        val displayDepth = (depth ?: DEFAULT_DISPLAY_DEPTH).coerceIn(1, 4)

        val menus = mutableListOf<MenuNode>()
        var cursor: String? = null
        var page = 0
        var truncated = false

        do {
            val variables = buildJsonObject {
                put("query", searchQuery?.let { JsonPrimitive(it) } ?: JsonNull)
                put("cursor", cursor?.let { JsonPrimitive(it) } ?: JsonNull)
            }
            val response = shopifyAdminGateway.executeGraphQL(storeId, LIST_MENUS_QUERY, variables).bind()
            val connection = (response as? JsonObject)?.get("menus") as? JsonObject
                ?: raise(TechnicalError("shopify.graphql.response.malformed"))

            val edges = (connection["edges"] as? JsonArray).orEmpty()
            edges.forEach { edge ->
                val node = (edge as? JsonObject)?.get("node") as? JsonObject
                if (node != null) menus += nodeToMenu(node)
            }

            val pageInfo = connection["pageInfo"] as? JsonObject
            val hasNextPage = pageInfo?.get("hasNextPage")?.jsonPrimitive?.boolean ?: false
            cursor = if (hasNextPage) pageInfo?.get("endCursor")?.jsonPrimitive?.contentOrNull else null
            page += 1
            if (cursor != null && page >= MAX_SEARCH_PAGES) {
                truncated = true
                cursor = null
            }
        } while (cursor != null)

        ListMenusResult(
            hadQuery = searchQuery != null,
            blocks = menus.map { MenuTreeRenderer.formatMenuBlock(it, displayDepth) },
            truncated = truncated,
        )
    }

    private fun nodeToMenu(node: JsonObject): MenuNode = MenuNode(
        id = node["id"]?.jsonPrimitive?.contentOrNull.orEmpty(),
        handle = node["handle"]?.jsonPrimitive?.contentOrNull.orEmpty(),
        title = node["title"]?.jsonPrimitive?.contentOrNull.orEmpty(),
        isDefault = node["isDefault"]?.jsonPrimitive?.boolean ?: false,
        items = MenuTree.normalizeItems(node["items"]),
    )
}
