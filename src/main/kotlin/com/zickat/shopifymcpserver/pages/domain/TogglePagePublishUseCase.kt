package com.zickat.shopifymcpserver.pages.domain

import arrow.core.Either
import arrow.core.raise.either
import com.zickat.shopifymcpserver.pages.exposed_interface.model.TogglePagePublishResult
import com.zickat.shopifymcpserver.shared_kernel.UseCaseError
import com.zickat.shopifymcpserver.shopify.exposed_interface.ShopifyAdminGateway
import com.zickat.shopifymcpserver.shopify.exposed_interface.ShopifyUserErrors
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import org.springframework.stereotype.Component

/**
 * Toggles `Page.isPublished` — the single use case behind both `publish_page` and `unpublish_page`
 * (ported from the `for (variant of [...])` loop in `pages.ts`, one implementation parameterized by
 * the target boolean rather than two duplicated files). No-op documented if the Page is already in
 * the target state — no mutation emitted in that case.
 */
@Component
class TogglePagePublishUseCase(
    private val shopifyAdminGateway: ShopifyAdminGateway,
) {

    fun execute(storeId: String, pageId: String, target: Boolean): Either<UseCaseError, TogglePagePublishResult> = either {
        val before = fetchPageNative(shopifyAdminGateway, storeId, pageId).bind()
            ?: return@either TogglePagePublishResult.notFound(pageId)

        if (before.isPublished == target) {
            val state = if (target) "déjà publiée" else "déjà dépubliée (brouillon)"
            return@either TogglePagePublishResult.noOp(
                "Page \"${before.title}\" : $state (isPublished: ${before.isPublished}) — aucune action (no-op).",
            )
        }

        val variables = buildJsonObject {
            put("id", JsonPrimitive(pageId))
            put("page", buildJsonObject { put("isPublished", JsonPrimitive(target)) })
        }
        val response = shopifyAdminGateway.executeGraphQL(storeId, TOGGLE_PAGE_PUBLISH_MUTATION, variables).bind()
        val payload = (response as? JsonObject)?.get("pageUpdate") as? JsonObject
        val userErrors = ShopifyUserErrors.parse(payload)
        val page = payload?.get("page") as? JsonObject

        if (userErrors.isNotEmpty() || page == null) {
            TogglePagePublishResult.failed(pageId, ShopifyUserErrors.format(userErrors))
        } else {
            val updatedTitle = page["title"]?.jsonPrimitive?.contentOrNull.orEmpty()
            val updatedIsPublished = page["isPublished"]?.jsonPrimitive?.boolean ?: target
            val verb = if (target) "publiée" else "dépubliée"
            TogglePagePublishResult.toggled("Page \"$updatedTitle\" $verb : isPublished=$updatedIsPublished.")
        }
    }

    companion object {
        private const val TOGGLE_PAGE_PUBLISH_MUTATION =
            "mutation TogglePagePublish(\$id: ID!, \$page: PageUpdateInput!) {\n" +
                "            pageUpdate(id: \$id, page: \$page) {\n" +
                "              page { id title isPublished }\n" +
                "              userErrors { field message }\n" +
                "            }\n" +
                "          }"
    }
}
