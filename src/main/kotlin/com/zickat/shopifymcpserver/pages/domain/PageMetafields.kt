package com.zickat.shopifymcpserver.pages.domain

import arrow.core.Either
import arrow.core.raise.either
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

const val MAX_PAGE_METAFIELDS = 50

data class PageMetafield(val key: String, val type: String, val value: String)

data class PageMetafieldsSnapshot(
    val title: String,
    val metafields: List<PageMetafield>,
    val truncated: Boolean,
)

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

/**
 * Reads every `custom.*` metafield already written on a Page — shared read helper (ported from
 * `fetchPageMetafields` in `shared.ts`), consumed by [GetPageMetafieldsUseCase] (pure display) and
 * [UpdatePageMetafieldsUseCase] (existence check + title source before writing, same guard as
 * `update_metaobject`).
 */
fun fetchPageMetafields(shopifyAdminGateway: ShopifyAdminGateway, storeId: String, pageId: String): Either<UseCaseError, PageMetafieldsSnapshot?> =
    either {
        val variables = buildJsonObject { put("id", JsonPrimitive(pageId)) }
        val response = shopifyAdminGateway.executeGraphQL(storeId, FETCH_PAGE_METAFIELDS_QUERY, variables).bind()
        val page = (response as? JsonObject)?.get("page") as? JsonObject

        page?.let {
            val title = it["title"]?.jsonPrimitive?.contentOrNull.orEmpty()
            val connection = it["metafields"] as? JsonObject ?: raise(TechnicalError("shopify.graphql.response.malformed"))

            val metafields = (connection["edges"] as? JsonArray).orEmpty().mapNotNull { edge ->
                val node = (edge as? JsonObject)?.get("node") as? JsonObject ?: return@mapNotNull null
                PageMetafield(
                    key = node["key"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                    type = node["type"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                    value = node["value"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                )
            }
            val truncated = (connection["pageInfo"] as? JsonObject)?.get("hasNextPage")?.jsonPrimitive?.boolean ?: false

            PageMetafieldsSnapshot(title, metafields, truncated)
        }
    }
