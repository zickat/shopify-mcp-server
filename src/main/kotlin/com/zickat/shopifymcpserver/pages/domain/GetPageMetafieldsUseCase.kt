package com.zickat.shopifymcpserver.pages.domain

import arrow.core.Either
import arrow.core.raise.either
import com.zickat.shopifymcpserver.pages.exposed_interface.model.GetPageMetafieldsResult
import com.zickat.shopifymcpserver.shared_kernel.TechnicalError
import com.zickat.shopifymcpserver.shared_kernel.UseCaseError
import com.zickat.shopifymcpserver.shopify.exposed_interface.ShopifyAdminGateway
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import org.springframework.stereotype.Component

private const val MAX_PAGE_METAFIELDS = 50

private val FETCH_PAGE_METAFIELDS_QUERY =
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

private data class PageMetafield(val key: String, val type: String, val value: String)

@Component
class GetPageMetafieldsUseCase(
    private val shopifyAdminGateway: ShopifyAdminGateway,
) {

    fun execute(storeId: String, pageId: String, keys: List<String>?): Either<UseCaseError, GetPageMetafieldsResult> = either {
        val variables = buildJsonObject { put("id", JsonPrimitive(pageId)) }
        val response = shopifyAdminGateway.executeGraphQL(storeId, FETCH_PAGE_METAFIELDS_QUERY, variables).bind()
        val page = (response as? JsonObject)?.get("page") as? JsonObject

        if (page == null) {
            GetPageMetafieldsResult.notFound(pageId)
        } else {
            val title = page["title"]?.jsonPrimitive?.contentOrNull.orEmpty()
            val connection = page["metafields"] as? JsonObject
                ?: raise(TechnicalError("shopify.graphql.response.malformed"))

            val metafields = (connection["edges"] as? JsonArray).orEmpty().mapNotNull { edge ->
                val node = (edge as? JsonObject)?.get("node") as? JsonObject ?: return@mapNotNull null
                PageMetafield(
                    key = node["key"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                    type = node["type"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                    value = node["value"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                )
            }
            val truncated = (connection["pageInfo"] as? JsonObject)?.get("hasNextPage")?.jsonPrimitive?.boolean ?: false
            val truncationNote = if (truncated) {
                "\n\n⚠️ Lecture des metafields tronquée — d'autres metafields custom.* peuvent exister au-delà de la borne."
            } else {
                ""
            }

            val text = if (keys != null && keys.isNotEmpty()) {
                val found = metafields.filter { it.key in keys }
                val missing = keys.filter { key -> metafields.none { it.key == key } }
                val foundLines = if (found.isNotEmpty()) {
                    found.joinToString("\n") { "- ${it.key}: ${it.value} (${it.type})" }
                } else {
                    "  (aucune des clés demandées n'est définie)"
                }
                val missingNote = if (missing.isNotEmpty()) "\nNon défini : ${missing.joinToString(", ")}" else ""
                "Page \"$title\" — ${found.size} metafield(s) custom.* parmi les clés demandées :\n$foundLines$missingNote$truncationNote"
            } else if (metafields.isEmpty()) {
                "Page \"$title\" — aucun metafield custom.* défini.$truncationNote"
            } else {
                val lines = metafields.joinToString("\n") { "- ${it.key}: ${it.value} (${it.type})" }
                "Page \"$title\" — ${metafields.size} metafield(s) custom.* :\n$lines$truncationNote"
            }

            GetPageMetafieldsResult.found(text)
        }
    }
}
