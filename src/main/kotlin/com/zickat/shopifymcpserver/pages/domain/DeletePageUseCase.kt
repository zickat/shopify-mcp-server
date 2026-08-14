package com.zickat.shopifymcpserver.pages.domain

import arrow.core.Either
import arrow.core.raise.either
import com.zickat.shopifymcpserver.pages.exposed_interface.model.DeletePageResult
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
class DeletePageUseCase(
    private val shopifyAdminGateway: ShopifyAdminGateway,
) {

    fun execute(storeId: String, pageId: String): Either<UseCaseError, DeletePageResult> = either {
        val before = fetchPageNative(shopifyAdminGateway, storeId, pageId).bind()
            ?: return@either DeletePageResult.notFound(pageId)

        val variables = buildJsonObject { put("id", JsonPrimitive(pageId)) }
        val response = shopifyAdminGateway.executeGraphQL(storeId, DELETE_PAGE_MUTATION, variables).bind()
        val payload = (response as? JsonObject)?.get("pageDelete") as? JsonObject
        val userErrors = ShopifyUserErrors.parse(payload)
        val deletedId = payload?.get("deletedPageId")?.jsonPrimitive?.contentOrNull

        if (userErrors.isNotEmpty() || deletedId == null) {
            DeletePageResult.failed(pageId, ShopifyUserErrors.format(userErrors))
        } else {
            DeletePageResult.deleted(
                "Page \"${before.title}\" (${before.handle}) supprimée définitivement. Action irréversible (aucune restauration côté MCP).",
            )
        }
    }

    companion object {
        private const val DELETE_PAGE_MUTATION =
            "mutation DeletePage(\$id: ID!) {\n" +
                "          pageDelete(id: \$id) {\n" +
                "            deletedPageId\n" +
                "            userErrors { field message }\n" +
                "          }\n" +
                "        }"
    }
}
