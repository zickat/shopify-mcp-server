package com.zickat.shopifymcpserver.catalog_status.domain

import arrow.core.Either
import arrow.core.raise.either
import com.zickat.shopifymcpserver.catalog_status.exposed_interface.model.ResourceSummary
import com.zickat.shopifymcpserver.catalog_status.exposed_interface.model.SearchResourceType
import com.zickat.shopifymcpserver.catalog_status.exposed_interface.model.SearchResourcesResult
import com.zickat.shopifymcpserver.catalog_status.exposed_interface.model.SearchStatusFilter
import com.zickat.shopifymcpserver.shared_kernel.MAX_SEARCH_PAGES
import com.zickat.shopifymcpserver.shared_kernel.TechnicalError
import com.zickat.shopifymcpserver.shared_kernel.UseCaseError
import com.zickat.shopifymcpserver.shopify.exposed_interface.RichText
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

@Component
class SearchResourcesUseCase(
    private val shopifyAdminGateway: ShopifyAdminGateway,
) {

    fun execute(
        storeId: String,
        resourceType: SearchResourceType,
        query: String?,
        statusFilter: SearchStatusFilter,
    ): Either<UseCaseError, SearchResourcesResult> = either {
        val info = resourceInfoFor(resourceType)
        val graphQLQuery = searchQueryFor(info.pluralField, info.secondaryKey)
        val searchQuery = query?.trim()?.takeIf { it.isNotEmpty() }

        val results = mutableListOf<ResourceSummary>()
        var cursor: String? = null
        var page = 0
        var truncated = false

        do {
            val variables = buildJsonObject {
                put("query", searchQuery?.let { JsonPrimitive(it) } ?: JsonNull)
                put("cursor", cursor?.let { JsonPrimitive(it) } ?: JsonNull)
            }
            val response = shopifyAdminGateway.executeGraphQL(storeId, graphQLQuery, variables).bind()
            val connection = (response as? JsonObject)?.get(info.pluralField) as? JsonObject
                ?: raise(TechnicalError("shopify.graphql.response.malformed"))

            val edges = (connection["edges"] as? JsonArray).orEmpty()
            edges.forEach { edge ->
                val node = (edge as? JsonObject)?.get("node") as? JsonObject
                if (node != null) {
                    nodeToSummaryOrNull(resourceType, node, statusFilter)?.let { results += it }
                }
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

        SearchResourcesResult(resourceType, results, truncated)
    }

    private fun nodeToSummaryOrNull(
        resourceType: SearchResourceType,
        node: JsonObject,
        statusFilter: SearchStatusFilter,
    ): ResourceSummary? {
        val id = node["id"]?.jsonPrimitive?.contentOrNull ?: return null
        val title = node["title"]?.jsonPrimitive?.contentOrNull.orEmpty()
        val handle = node["handle"]?.jsonPrimitive?.contentOrNull.orEmpty()
        val contentStatusValue = node.metafieldValue("contentStatus")
        val summaryValue = node.metafieldValue("summary")
        val secondaryValue = node.metafieldValue("secondarySignal")

        val hasSummary = summaryValue.orEmpty().trim().isNotEmpty()
        val hasSecondarySignal = when (resourceType) {
            SearchResourceType.COLLECTION -> RichText.toPlainText(secondaryValue).trim().isNotEmpty()
            SearchResourceType.ARTICLE -> RichText.parseStringArray(secondaryValue).isNotEmpty()
        }
        val isEnriched = hasSummary || hasSecondarySignal

        val included = when (statusFilter) {
            SearchStatusFilter.UNTREATED -> contentStatusValue == null && !isEnriched
            SearchStatusFilter.TO_REVIEW -> contentStatusValue == "to_review"
            SearchStatusFilter.BLOCKED -> contentStatusValue == "blocked"
            SearchStatusFilter.ALL -> true
        }
        if (!included) return null

        val displayedStatus = contentStatusValue ?: if (isEnriched) "published" else "untreated"
        return ResourceSummary(id, title, handle, displayedStatus)
    }

    private fun JsonObject.metafieldValue(key: String): String? =
        ((this[key] as? JsonObject)?.get("value"))?.jsonPrimitive?.contentOrNull

    private fun resourceInfoFor(resourceType: SearchResourceType): ResourceTypeInfo = when (resourceType) {
        SearchResourceType.COLLECTION -> ResourceTypeInfo("collections", "intro_text")
        SearchResourceType.ARTICLE -> ResourceTypeInfo("articles", "sections")
    }

    private data class ResourceTypeInfo(val pluralField: String, val secondaryKey: String)

    private fun searchQueryFor(pluralField: String, secondaryKey: String): String =
        "query SearchResources(\$query: String, \$cursor: String) {\n" +
            "            $pluralField(first: 50, after: \$cursor, query: \$query) {\n" +
            "              pageInfo { hasNextPage endCursor }\n" +
            "              edges {\n" +
            "                node {\n" +
            "                  id\n" +
            "                  title\n" +
            "                  handle\n" +
            "                  contentStatus: metafield(namespace: \"custom\", key: \"content_status\") { value }\n" +
            "                  summary: metafield(namespace: \"custom\", key: \"summary\") { value }\n" +
            "                  secondarySignal: metafield(namespace: \"custom\", key: \"$secondaryKey\") { value }\n" +
            "                }\n" +
            "              }\n" +
            "            }\n" +
            "          }"
}
