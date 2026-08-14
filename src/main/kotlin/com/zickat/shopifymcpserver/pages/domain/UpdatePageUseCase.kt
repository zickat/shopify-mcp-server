package com.zickat.shopifymcpserver.pages.domain

import arrow.core.Either
import arrow.core.raise.either
import com.zickat.shopifymcpserver.pages.exposed_interface.model.UpdatePageResult
import com.zickat.shopifymcpserver.shared_kernel.UseCaseError
import com.zickat.shopifymcpserver.shopify.exposed_interface.ShopifyAdminGateway
import com.zickat.shopifymcpserver.shopify.exposed_interface.ShopifyUserErrors
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import org.springframework.stereotype.Component

@Component
class UpdatePageUseCase(
    private val shopifyAdminGateway: ShopifyAdminGateway,
) {

    fun execute(storeId: String, pageId: String, title: String?, body: String?, handle: String?): Either<UseCaseError, UpdatePageResult> = either {
        if (title == null && body == null && handle == null) {
            return@either UpdatePageResult.NoOp
        }

        val before = fetchPageNative(shopifyAdminGateway, storeId, pageId).bind()
            ?: return@either UpdatePageResult.notFound(pageId)

        val changedFields = mutableListOf<String>()
        val pageInput = buildJsonObject {
            title?.let { put("title", JsonPrimitive(it)); changedFields += "title" }
            body?.let { put("body", JsonPrimitive(it)); changedFields += "body" }
            handle?.let { put("handle", JsonPrimitive(it)); changedFields += "handle" }
        }
        val variables = buildJsonObject {
            put("id", JsonPrimitive(pageId))
            put("page", pageInput)
        }
        val response = shopifyAdminGateway.executeGraphQL(storeId, UPDATE_PAGE_MUTATION, variables).bind()
        val payload = (response as? JsonObject)?.get("pageUpdate") as? JsonObject
        val userErrors = ShopifyUserErrors.parse(payload)
        val page = payload?.get("page") as? JsonObject

        if (userErrors.isNotEmpty() || page == null) {
            UpdatePageResult.failed(pageId, ShopifyUserErrors.format(userErrors))
        } else {
            val id = page["id"]?.jsonPrimitive?.contentOrNull.orEmpty()
            val updatedTitle = page["title"]?.jsonPrimitive?.contentOrNull.orEmpty()
            val updatedHandle = page["handle"]?.jsonPrimitive?.contentOrNull.orEmpty()
            val handleNote = if (handle != null) "\nHandle effectif : $updatedHandle" else ""
            UpdatePageResult.updated("Page \"$updatedTitle\" ($id) mise à jour — champs modifiés : ${changedFields.joinToString(", ")}.$handleNote")
        }
    }

    companion object {
        private const val UPDATE_PAGE_MUTATION =
            "mutation UpdatePage(\$id: ID!, \$page: PageUpdateInput!) {\n" +
                "          pageUpdate(id: \$id, page: \$page) {\n" +
                "            page { id title handle isPublished }\n" +
                "            userErrors { field message }\n" +
                "          }\n" +
                "        }"
    }
}
