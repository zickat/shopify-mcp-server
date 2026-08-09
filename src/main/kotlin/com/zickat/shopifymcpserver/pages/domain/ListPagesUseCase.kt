package com.zickat.shopifymcpserver.pages.domain

import arrow.core.Either
import arrow.core.raise.either
import com.zickat.shopifymcpserver.pages.exposed_interface.model.ListPagesResult
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

private val LIST_PAGES_QUERY =
    "query ListPages(\$query: String, \$cursor: String) {\n" +
        "            pages(first: 50, after: \$cursor, query: \$query) {\n" +
        "              pageInfo { hasNextPage endCursor }\n" +
        "              edges { node { id title handle } }\n" +
        "            }\n" +
        "          }"

private data class PageSummary(val id: String, val title: String, val handle: String)

@Component
class ListPagesUseCase(
    private val shopifyAdminGateway: ShopifyAdminGateway,
) {

    fun execute(storeId: String, query: String?): Either<UseCaseError, ListPagesResult> = either {
        val searchQuery = query?.trim()?.takeIf { it.isNotEmpty() }
        val results = mutableListOf<PageSummary>()
        var cursor: String? = null
        var page = 0
        var truncated = false

        do {
            val variables = buildJsonObject {
                put("query", searchQuery?.let { JsonPrimitive(it) } ?: JsonNull)
                put("cursor", cursor?.let { JsonPrimitive(it) } ?: JsonNull)
            }
            val response = shopifyAdminGateway.executeGraphQL(storeId, LIST_PAGES_QUERY, variables).bind()
            val connection = (response as? JsonObject)?.get("pages") as? JsonObject
                ?: raise(TechnicalError("shopify.graphql.response.malformed"))

            (connection["edges"] as? JsonArray).orEmpty().forEach { edge ->
                val node = (edge as? JsonObject)?.get("node") as? JsonObject
                if (node != null) {
                    results += PageSummary(
                        id = node["id"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                        title = node["title"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                        handle = node["handle"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                    )
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
            if (searchQuery != null) "Aucune page trouvée pour ce filtre." else "Aucune page trouvée sur le store."
        } else {
            val truncationNote = if (truncated) " (résultats tronqués à ${MAX_SEARCH_PAGES * 50} — affiner la requête)" else ""
            val lines = results.joinToString("\n") { "- ${it.title} (${it.handle}) — id: ${it.id}" }
            "${results.size} page(s) trouvée(s)$truncationNote.\n\n$lines"
        }
        ListPagesResult(text)
    }
}
