package com.zickat.shopifymcpserver.products.domain

import arrow.core.Either
import arrow.core.raise.either
import com.zickat.shopifymcpserver.products.exposed_interface.model.ListToReviewResult
import com.zickat.shopifymcpserver.products.exposed_interface.model.ToReviewResourceType
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

private data class ToReviewMatch(val id: String, val title: String, val handle: String)

@Component
class ListToReviewUseCase(
    private val shopifyAdminGateway: ShopifyAdminGateway,
) {

    fun execute(storeId: String, resourceType: ToReviewResourceType): Either<UseCaseError, ListToReviewResult> = either {
        val pluralField = resourceType.pluralField
        val query = listToReviewQueryFor(pluralField)

        val matches = mutableListOf<ToReviewMatch>()
        var cursor: String? = null
        var page = 0

        do {
            val variables = buildJsonObject { put("cursor", cursor?.let { JsonPrimitive(it) } ?: JsonNull) }
            val response = shopifyAdminGateway.executeGraphQL(storeId, query, variables).bind()
            val connection = (response as? JsonObject)?.get(pluralField) as? JsonObject
                ?: raise(TechnicalError("shopify.graphql.response.malformed"))

            (connection["edges"] as? JsonArray).orEmpty().forEach { edge ->
                val node = (edge as? JsonObject)?.get("node") as? JsonObject
                val contentStatus = ((node?.get("contentStatus") as? JsonObject)?.get("value"))?.jsonPrimitive?.contentOrNull
                if (node != null && contentStatus == "to_review") {
                    matches += ToReviewMatch(
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
            if (cursor != null && page >= MAX_SEARCH_PAGES) cursor = null
        } while (cursor != null)

        val text = if (matches.isEmpty()) {
            "Aucun(e) ${resourceType.toolValue} à review actuellement."
        } else {
            val lines = matches.joinToString("\n") { "- ${it.title} (${it.handle}) — id: ${it.id}" }
            "${matches.size} ${resourceType.toolValue}(s) à review :\n$lines"
        }
        ListToReviewResult(text)
    }

    private fun listToReviewQueryFor(pluralField: String): String =
        "query ListToReview(\$cursor: String) {\n" +
            "            $pluralField(first: 50, after: \$cursor) {\n" +
            "              pageInfo { hasNextPage endCursor }\n" +
            "              edges {\n" +
            "                node {\n" +
            "                  id\n" +
            "                  title\n" +
            "                  handle\n" +
            "                  contentStatus: metafield(namespace: \"custom\", key: \"content_status\") { value }\n" +
            "                }\n" +
            "              }\n" +
            "            }\n" +
            "          }"
}
