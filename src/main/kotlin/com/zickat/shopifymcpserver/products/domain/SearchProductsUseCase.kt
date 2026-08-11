package com.zickat.shopifymcpserver.products.domain

import arrow.core.Either
import arrow.core.raise.either
import com.zickat.shopifymcpserver.products.exposed_interface.model.ProductStatusFilter
import com.zickat.shopifymcpserver.products.exposed_interface.model.SearchProductsResult
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

private val SEARCH_PRODUCTS_QUERY =
    "query SearchProducts(\$query: String, \$cursor: String) {\n" +
        "            products(first: 50, after: \$cursor, query: \$query) {\n" +
        "              pageInfo { hasNextPage endCursor }\n" +
        "              edges {\n" +
        "                node {\n" +
        "                  id\n" +
        "                  title\n" +
        "                  handle\n" +
        "                  status\n" +
        "                  contentStatus: metafield(namespace: \"custom\", key: \"content_status\") { value }\n" +
        "                  summaryPoints: metafield(namespace: \"custom\", key: \"summary_points\") { value }\n" +
        "                }\n" +
        "              }\n" +
        "            }\n" +
        "          }"

private data class ProductSearchSummary(val id: String, val title: String, val handle: String, val status: String, val contentStatus: String)

@Component
class SearchProductsUseCase(
    private val shopifyAdminGateway: ShopifyAdminGateway,
) {

    fun execute(storeId: String, query: String?, statusFilter: ProductStatusFilter): Either<UseCaseError, SearchProductsResult> = either {
        val searchQuery = query?.trim()?.takeIf { it.isNotEmpty() }
        val results = mutableListOf<ProductSearchSummary>()
        var cursor: String? = null
        var page = 0
        var truncated = false

        do {
            val variables = buildJsonObject {
                put("query", searchQuery?.let { JsonPrimitive(it) } ?: JsonNull)
                put("cursor", cursor?.let { JsonPrimitive(it) } ?: JsonNull)
            }
            val response = shopifyAdminGateway.executeGraphQL(storeId, SEARCH_PRODUCTS_QUERY, variables).bind()
            val connection = (response as? JsonObject)?.get("products") as? JsonObject
                ?: raise(TechnicalError("shopify.graphql.response.malformed"))

            (connection["edges"] as? JsonArray).orEmpty().forEach { edge ->
                val node = (edge as? JsonObject)?.get("node") as? JsonObject
                if (node != null) {
                    nodeToSummaryOrNull(node, statusFilter)?.let { results += it }
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

        val text = if (results.isEmpty()) {
            "Aucun produit trouvé pour ce filtre."
        } else {
            val truncationNote = if (truncated) " (résultats tronqués à ${MAX_SEARCH_PAGES * 50} — affiner la requête)" else ""
            val lines = results.joinToString("\n") {
                "- ${it.title} (${it.handle}) — statut Shopify: ${it.status}, pipeline: ${it.contentStatus} — id: ${it.id}"
            }
            "${results.size} produit(s) trouvé(s)$truncationNote.\n\n$lines"
        }
        SearchProductsResult(text)
    }

    private fun nodeToSummaryOrNull(node: JsonObject, statusFilter: ProductStatusFilter): ProductSearchSummary? {
        val id = node["id"]?.jsonPrimitive?.contentOrNull ?: return null
        val title = node["title"]?.jsonPrimitive?.contentOrNull.orEmpty()
        val handle = node["handle"]?.jsonPrimitive?.contentOrNull.orEmpty()
        val status = node["status"]?.jsonPrimitive?.contentOrNull.orEmpty()
        val contentStatusValue = node.metafieldValue("contentStatus")
        val hasSummaryPoints = RichText.parseStringArray(node.metafieldValue("summaryPoints")).isNotEmpty()

        val included = when (statusFilter) {
            ProductStatusFilter.UNTREATED -> contentStatusValue == null && !hasSummaryPoints
            ProductStatusFilter.TO_REVIEW -> contentStatusValue == "to_review"
            ProductStatusFilter.BLOCKED -> contentStatusValue == "blocked"
            ProductStatusFilter.ALL -> true
        }
        if (!included) return null

        return ProductSearchSummary(id, title, handle, status, deriveContentStatus(contentStatusValue, hasSummaryPoints))
    }

    private fun JsonObject.metafieldValue(key: String): String? =
        ((this[key] as? JsonObject)?.get("value"))?.jsonPrimitive?.contentOrNull
}
