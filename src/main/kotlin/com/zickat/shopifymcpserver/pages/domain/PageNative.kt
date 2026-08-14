package com.zickat.shopifymcpserver.pages.domain

import arrow.core.Either
import arrow.core.raise.either
import com.zickat.shopifymcpserver.shared_kernel.UseCaseError
import com.zickat.shopifymcpserver.shopify.exposed_interface.ShopifyAdminGateway
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

/**
 * Native state of a Page (title/HTML body/handle/published status) — distinct from
 * [fetchPageMetafields], which only reads `custom.*`. Shared read helper — used by every mutant
 * that must reread the Page before writing (`update_page`, `publish_page`/`unpublish_page`,
 * `delete_page`), ported from the local `fetchPageNative` in `pages.ts`.
 */
data class PageNative(
    val id: String,
    val title: String,
    val handle: String,
    val isPublished: Boolean,
    val body: String,
)

private const val FETCH_PAGE_NATIVE_QUERY =
    "query FetchPageNative(\$id: ID!) {\n" +
        "      page(id: \$id) { id title handle isPublished body }\n" +
        "    }"

fun fetchPageNative(shopifyAdminGateway: ShopifyAdminGateway, storeId: String, pageId: String): Either<UseCaseError, PageNative?> = either {
    val variables = buildJsonObject { put("id", JsonPrimitive(pageId)) }
    val response = shopifyAdminGateway.executeGraphQL(storeId, FETCH_PAGE_NATIVE_QUERY, variables).bind()
    val page = (response as? JsonObject)?.get("page") as? JsonObject
    page?.let {
        PageNative(
            id = it["id"]?.jsonPrimitive?.contentOrNull.orEmpty(),
            title = it["title"]?.jsonPrimitive?.contentOrNull.orEmpty(),
            handle = it["handle"]?.jsonPrimitive?.contentOrNull.orEmpty(),
            isPublished = it["isPublished"]?.jsonPrimitive?.boolean ?: false,
            body = it["body"]?.jsonPrimitive?.contentOrNull.orEmpty(),
        )
    }
}
