package com.zickat.shopifymcpserver.pages.domain

import arrow.core.Either
import arrow.core.raise.either
import com.zickat.shopifymcpserver.pages.exposed_interface.model.CreatePageResult
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

@Component
class CreatePageUseCase(
    private val shopifyAdminGateway: ShopifyAdminGateway,
) {

    fun execute(storeId: String, title: String, body: String, handle: String?, publish: Boolean?): Either<UseCaseError, CreatePageResult> = either {
        val isPublished = publish ?: false
        val pageInput = buildJsonObject {
            put("title", JsonPrimitive(title))
            put("body", JsonPrimitive(body))
            handle?.let { put("handle", JsonPrimitive(it)) }
            put("isPublished", JsonPrimitive(isPublished))
        }
        val variables = buildJsonObject { put("page", pageInput) }
        val response = shopifyAdminGateway.executeGraphQL(storeId, CREATE_PAGE_MUTATION, variables).bind()
        val payload = (response as? JsonObject)?.get("pageCreate") as? JsonObject
        val userErrors = ShopifyUserErrors.parse(payload)
        val page = payload?.get("page") as? JsonObject

        if (userErrors.isNotEmpty() || page == null) {
            CreatePageResult.failed(title, ShopifyUserErrors.format(userErrors))
        } else {
            val id = page["id"]?.jsonPrimitive?.contentOrNull.orEmpty()
            val createdTitle = page["title"]?.jsonPrimitive?.contentOrNull.orEmpty()
            val createdHandle = page["handle"]?.jsonPrimitive?.contentOrNull.orEmpty()
            val createdIsPublished = page["isPublished"]?.jsonPrimitive?.boolean ?: isPublished
            val draftNote = if (!createdIsPublished) {
                "\nCréée en brouillon — utiliser publish_page après relecture pour la mettre en ligne."
            } else {
                ""
            }
            CreatePageResult.created("Page \"$createdTitle\" créée ($createdHandle, $id) — isPublished: $createdIsPublished.$draftNote")
        }
    }

    companion object {
        private const val CREATE_PAGE_MUTATION =
            "mutation CreatePage(\$page: PageCreateInput!) {\n" +
                "          pageCreate(page: \$page) {\n" +
                "            page { id title handle isPublished }\n" +
                "            userErrors { field message }\n" +
                "          }\n" +
                "        }"
    }
}
