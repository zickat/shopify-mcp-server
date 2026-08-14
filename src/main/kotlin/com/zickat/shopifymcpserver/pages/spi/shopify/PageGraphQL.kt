package com.zickat.shopifymcpserver.pages.spi.shopify

import com.zickat.shopifymcpserver.pages.domain.PageMetafield
import com.zickat.shopifymcpserver.pages.domain.PageMetafieldsSnapshot
import com.zickat.shopifymcpserver.pages.domain.PageNative
import com.zickat.shopifymcpserver.pages.domain.repositories.PageSummary
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

internal const val MAX_PAGE_METAFIELDS = 50

internal object PageGraphQL {

    val LIST_PAGES_QUERY =
        "query ListPages(\$query: String, \$cursor: String) {\n" +
            "            pages(first: 50, after: \$cursor, query: \$query) {\n" +
            "              pageInfo { hasNextPage endCursor }\n" +
            "              edges { node { id title handle } }\n" +
            "            }\n" +
            "          }"

    val CREATE_PAGE_MUTATION =
        "mutation CreatePage(\$page: PageCreateInput!) {\n" +
            "          pageCreate(page: \$page) {\n" +
            "            page { id title handle isPublished }\n" +
            "            userErrors { field message }\n" +
            "          }\n" +
            "        }"

    val UPDATE_PAGE_MUTATION =
        "mutation UpdatePage(\$id: ID!, \$page: PageUpdateInput!) {\n" +
            "          pageUpdate(id: \$id, page: \$page) {\n" +
            "            page { id title handle isPublished }\n" +
            "            userErrors { field message }\n" +
            "          }\n" +
            "        }"

    val DELETE_PAGE_MUTATION =
        "mutation DeletePage(\$id: ID!) {\n" +
            "          pageDelete(id: \$id) {\n" +
            "            deletedPageId\n" +
            "            userErrors { field message }\n" +
            "          }\n" +
            "        }"

    val TOGGLE_PAGE_PUBLISH_MUTATION =
        "mutation TogglePagePublish(\$id: ID!, \$page: PageUpdateInput!) {\n" +
            "            pageUpdate(id: \$id, page: \$page) {\n" +
            "              page { id title isPublished }\n" +
            "              userErrors { field message }\n" +
            "            }\n" +
            "          }"

    val FETCH_PAGE_NATIVE_QUERY =
        "query FetchPageNative(\$id: ID!) {\n" +
            "      page(id: \$id) { id title handle isPublished body }\n" +
            "    }"

    val FETCH_PAGE_METAFIELDS_QUERY =
        "query FetchPageMetafields(\$id: ID!) {\n" +
            "      page(id: \$id) {\n" +
            "        id\n" +
            "        title\n" +
            "        metafields(namespace: \"custom\", first: $MAX_PAGE_METAFIELDS) {\n" +
            "          edges { node { key type value } }\n" +
            "          pageInfo { hasNextPage }\n" +
            "        }\n" +
            "      }\n" +
            "    }"

    fun pagesConnection(response: JsonElement): JsonObject? = (response as? JsonObject)?.get("pages") as? JsonObject

    fun pageSummaries(connection: JsonObject): List<PageSummary> =
        (connection["edges"] as? JsonArray).orEmpty().mapNotNull { edge ->
            val node = (edge as? JsonObject)?.get("node") as? JsonObject ?: return@mapNotNull null
            PageSummary(
                id = node["id"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                title = node["title"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                handle = node["handle"]?.jsonPrimitive?.contentOrNull.orEmpty(),
            )
        }

    fun hasNextPage(connection: JsonObject): Boolean =
        (connection["pageInfo"] as? JsonObject)?.get("hasNextPage")?.jsonPrimitive?.boolean ?: false

    fun endCursor(connection: JsonObject): String? =
        (connection["pageInfo"] as? JsonObject)?.get("endCursor")?.jsonPrimitive?.contentOrNull

    fun parsePageNative(response: JsonElement): PageNative? {
        val page = (response as? JsonObject)?.get("page") as? JsonObject ?: return null
        return pageNativeFrom(page)
    }

    fun pageNativeFrom(node: JsonObject, fallbackIsPublished: Boolean = false): PageNative =
        PageNative(
            id = node["id"]?.jsonPrimitive?.contentOrNull.orEmpty(),
            title = node["title"]?.jsonPrimitive?.contentOrNull.orEmpty(),
            handle = node["handle"]?.jsonPrimitive?.contentOrNull.orEmpty(),
            isPublished = node["isPublished"]?.jsonPrimitive?.boolean ?: fallbackIsPublished,
            body = node["body"]?.jsonPrimitive?.contentOrNull.orEmpty(),
        )

    fun mutationPayload(response: JsonElement, mutationField: String): JsonObject? =
        (response as? JsonObject)?.get(mutationField) as? JsonObject

    fun mutationPage(payload: JsonObject?): JsonObject? = payload?.get("page") as? JsonObject

    fun deletedPageId(payload: JsonObject?): String? = payload?.get("deletedPageId")?.jsonPrimitive?.contentOrNull

    fun pageNodeForMetafields(response: JsonElement): JsonObject? = (response as? JsonObject)?.get("page") as? JsonObject

    fun metafieldsConnection(pageNode: JsonObject): JsonObject? = pageNode["metafields"] as? JsonObject

    fun metafieldsSnapshotFrom(pageNode: JsonObject, connection: JsonObject): PageMetafieldsSnapshot {
        val title = pageNode["title"]?.jsonPrimitive?.contentOrNull.orEmpty()
        val metafields = (connection["edges"] as? JsonArray).orEmpty().mapNotNull { edge ->
            val node = (edge as? JsonObject)?.get("node") as? JsonObject ?: return@mapNotNull null
            PageMetafield(
                key = node["key"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                type = node["type"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                value = node["value"]?.jsonPrimitive?.contentOrNull.orEmpty(),
            )
        }
        val truncated = (connection["pageInfo"] as? JsonObject)?.get("hasNextPage")?.jsonPrimitive?.boolean ?: false

        return PageMetafieldsSnapshot(title, metafields, truncated)
    }
}
