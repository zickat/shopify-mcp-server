package com.zickat.shopifymcpserver.products.domain

import arrow.core.Either
import arrow.core.raise.either
import com.zickat.shopifymcpserver.products.exposed_interface.model.UnpublishResourceResult
import com.zickat.shopifymcpserver.shared_kernel.UseCaseError
import com.zickat.shopifymcpserver.shopify.exposed_interface.ShopifyAdminGateway
import com.zickat.shopifymcpserver.shopify.exposed_interface.ShopifyOnlineStorePublication
import com.zickat.shopifymcpserver.shopify.exposed_interface.ShopifyUserErrors
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonPrimitive
import org.springframework.stereotype.Component

@Component
class UnpublishResourceUseCase(
    private val shopifyAdminGateway: ShopifyAdminGateway,
) {

    fun execute(storeId: String, resourceId: String): Either<UseCaseError, UnpublishResourceResult> = either {
        val state = readUnpublishState(storeId, resourceId).bind()
            ?: return@either UnpublishResourceResult.notFound(resourceId)

        if (state.publicationsCount == 0) {
            return@either UnpublishResourceResult.noop(state.title)
        }

        val publicationId = ShopifyOnlineStorePublication.resolveId(shopifyAdminGateway, storeId).bind()
        val unpublishErrors = unpublishFromOnlineStore(storeId, resourceId, publicationId).bind()
        if (unpublishErrors.isNotEmpty()) return@either UnpublishResourceResult.failed(ShopifyUserErrors.format(unpublishErrors))

        UnpublishResourceResult.unpublished(state.title, countBefore = state.publicationsCount)
    }

    private data class UnpublishState(val title: String, val publicationsCount: Int)

    private fun readUnpublishState(storeId: String, resourceId: String): Either<UseCaseError, UnpublishState?> = either {
        val variables = buildJsonObject { put("id", JsonPrimitive(resourceId)) }
        val response = shopifyAdminGateway.executeGraphQL(storeId, PRODUCT_UNPUBLISH_STATE_QUERY, variables).bind()
        val product = (response as? JsonObject)?.get("product") as? JsonObject
        product?.let {
            UnpublishState(
                title = it["title"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                publicationsCount = (it["resourcePublicationsCount"] as? JsonObject)?.get("count")?.jsonPrimitive?.int ?: 0,
            )
        }
    }

    private fun unpublishFromOnlineStore(storeId: String, resourceId: String, publicationId: String) = either {
        val variables = buildJsonObject {
            put("id", JsonPrimitive(resourceId))
            put(
                "input",
                buildJsonArray {
                    add(buildJsonObject { put("publicationId", JsonPrimitive(publicationId)) })
                },
            )
        }
        val response = shopifyAdminGateway.executeGraphQL(storeId, UNPUBLISH_FROM_ONLINE_STORE_MUTATION, variables).bind()
        ShopifyUserErrors.parse((response as? JsonObject)?.get("publishableUnpublish") as? JsonObject)
    }

    companion object {
        private const val PRODUCT_UNPUBLISH_STATE_QUERY =
            "query ProductUnpublishState(\$id: ID!) {\n" +
                "          product(id: \$id) { id title resourcePublicationsCount { count } }\n" +
                "        }"

        private const val UNPUBLISH_FROM_ONLINE_STORE_MUTATION =
            "mutation UnpublishProductFromOnlineStore(\$id: ID!, \$input: [PublicationInput!]!) {\n" +
                "          publishableUnpublish(id: \$id, input: \$input) {\n" +
                "            publishable { ... on Product { id } }\n" +
                "            userErrors { field message }\n" +
                "          }\n" +
                "        }"
    }
}
