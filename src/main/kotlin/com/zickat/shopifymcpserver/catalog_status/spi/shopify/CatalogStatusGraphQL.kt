package com.zickat.shopifymcpserver.catalog_status.spi.shopify

import com.zickat.shopifymcpserver.catalog_status.domain.models.CatalogStatusResourceNode
import com.zickat.shopifymcpserver.catalog_status.domain.models.SearchResourceType
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

internal object CatalogStatusGraphQL {

    fun searchQueryFor(resourceType: SearchResourceType): String {
        val info = resourceInfoFor(resourceType)
        return "query SearchResources(\$query: String, \$cursor: String) {\n" +
            "            ${info.pluralField}(first: 50, after: \$cursor, query: \$query) {\n" +
            "              pageInfo { hasNextPage endCursor }\n" +
            "              edges {\n" +
            "                node {\n" +
            "                  id\n" +
            "                  title\n" +
            "                  handle\n" +
            "                  contentStatus: metafield(namespace: \"custom\", key: \"content_status\") { value }\n" +
            "                  summary: metafield(namespace: \"custom\", key: \"summary\") { value }\n" +
            "                  secondarySignal: metafield(namespace: \"custom\", key: \"${info.secondaryKey}\") { value }\n" +
            "                }\n" +
            "              }\n" +
            "            }\n" +
            "          }"
    }

    fun searchVariables(query: String?, cursor: String?): JsonObject = buildJsonObject {
        put("query", query?.let { JsonPrimitive(it) } ?: JsonNull)
        put("cursor", cursor?.let { JsonPrimitive(it) } ?: JsonNull)
    }

    fun resourcesConnection(resourceType: SearchResourceType, response: JsonElement): JsonObject? =
        (response as? JsonObject)?.get(resourceInfoFor(resourceType).pluralField) as? JsonObject

    fun hasNextPage(connection: JsonObject): Boolean =
        (connection["pageInfo"] as? JsonObject)?.get("hasNextPage")?.jsonPrimitive?.boolean ?: false

    fun endCursor(connection: JsonObject): String? =
        (connection["pageInfo"] as? JsonObject)?.get("endCursor")?.jsonPrimitive?.contentOrNull

    fun resourceNodes(connection: JsonObject): List<CatalogStatusResourceNode> {
        val edges = (connection["edges"] as? JsonArray).orEmpty()
        return edges.mapNotNull { edge -> (edge as? JsonObject)?.get("node") as? JsonObject }.map(::nodeToRaw)
    }

    private fun nodeToRaw(node: JsonObject): CatalogStatusResourceNode = CatalogStatusResourceNode(
        id = node["id"]?.jsonPrimitive?.contentOrNull.orEmpty(),
        title = node["title"]?.jsonPrimitive?.contentOrNull.orEmpty(),
        handle = node["handle"]?.jsonPrimitive?.contentOrNull.orEmpty(),
        contentStatus = node.metafieldValue("contentStatus"),
        summary = node.metafieldValue("summary"),
        secondarySignal = node.metafieldValue("secondarySignal"),
    )

    private fun JsonObject.metafieldValue(key: String): String? =
        ((this[key] as? JsonObject)?.get("value"))?.jsonPrimitive?.contentOrNull

    private fun resourceInfoFor(resourceType: SearchResourceType): ResourceTypeInfo = when (resourceType) {
        SearchResourceType.COLLECTION -> ResourceTypeInfo("collections", "intro_text")
        SearchResourceType.ARTICLE -> ResourceTypeInfo("articles", "sections")
    }

    private data class ResourceTypeInfo(val pluralField: String, val secondaryKey: String)
}
