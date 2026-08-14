package com.zickat.shopifymcpserver.products.domain

import arrow.core.Either
import arrow.core.raise.either
import com.zickat.shopifymcpserver.products.exposed_interface.model.PublishResourceResult
import com.zickat.shopifymcpserver.shared_kernel.UseCaseError
import com.zickat.shopifymcpserver.shopify.exposed_interface.ShopifyAdminGateway
import com.zickat.shopifymcpserver.shopify.exposed_interface.ShopifyMetafields
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
class PublishResourceUseCase(
    private val shopifyAdminGateway: ShopifyAdminGateway,
) {

    fun execute(storeId: String, resourceId: String): Either<UseCaseError, PublishResourceResult> = either {
        val state = readPublishState(storeId, resourceId).bind()
            ?: return@either PublishResourceResult.notFound(resourceId)

        val activateErrors = activateProduct(storeId, resourceId).bind()
        if (activateErrors.isNotEmpty()) return@either PublishResourceResult.failed(ShopifyUserErrors.format(activateErrors))

        val deleteErrors = ShopifyMetafields.delete(
            shopifyAdminGateway,
            storeId,
            resourceId,
            MarkBlockedUseCase.CONTENT_STATUS_NAMESPACE,
            MarkBlockedUseCase.CONTENT_STATUS_KEY,
        ).bind()
        if (deleteErrors.isNotEmpty()) return@either PublishResourceResult.failed(ShopifyUserErrors.format(deleteErrors))

        val wasNeverPublished = state.publicationsCount == 0
        if (wasNeverPublished) {
            val publicationId = ShopifyOnlineStorePublication.resolveId(shopifyAdminGateway, storeId).bind()
            val publishErrors = publishToOnlineStore(storeId, resourceId, publicationId).bind()
            if (publishErrors.isNotEmpty()) return@either PublishResourceResult.failed(ShopifyUserErrors.format(publishErrors))
        }

        PublishResourceResult.published(state.title, wasNeverPublished = wasNeverPublished)
    }

    private data class PublishState(val title: String, val publicationsCount: Int)

    private fun readPublishState(storeId: String, resourceId: String): Either<UseCaseError, PublishState?> = either {
        val variables = buildJsonObject { put("id", JsonPrimitive(resourceId)) }
        val response = shopifyAdminGateway.executeGraphQL(storeId, PRODUCT_PUBLISH_STATE_QUERY, variables).bind()
        val product = (response as? JsonObject)?.get("product") as? JsonObject
        product?.let {
            PublishState(
                title = it["title"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                publicationsCount = (it["resourcePublicationsCount"] as? JsonObject)?.get("count")?.jsonPrimitive?.int ?: 0,
            )
        }
    }

    private fun activateProduct(storeId: String, resourceId: String) = either {
        val variables = buildJsonObject {
            put(
                "product",
                buildJsonObject {
                    put("id", JsonPrimitive(resourceId))
                    put("status", JsonPrimitive("ACTIVE"))
                },
            )
        }
        val response = shopifyAdminGateway.executeGraphQL(storeId, ACTIVATE_PRODUCT_MUTATION, variables).bind()
        ShopifyUserErrors.parse((response as? JsonObject)?.get("productUpdate") as? JsonObject)
    }

    private fun publishToOnlineStore(storeId: String, resourceId: String, publicationId: String) = either {
        val variables = buildJsonObject {
            put("id", JsonPrimitive(resourceId))
            put(
                "input",
                buildJsonArray {
                    add(buildJsonObject { put("publicationId", JsonPrimitive(publicationId)) })
                },
            )
        }
        val response = shopifyAdminGateway.executeGraphQL(storeId, PUBLISH_TO_ONLINE_STORE_MUTATION, variables).bind()
        ShopifyUserErrors.parse((response as? JsonObject)?.get("publishablePublish") as? JsonObject)
    }

    companion object {
        private const val PRODUCT_PUBLISH_STATE_QUERY =
            "query ProductPublishState(\$id: ID!) {\n" +
                "          product(id: \$id) { id title status resourcePublicationsCount { count } }\n" +
                "        }"

        private const val ACTIVATE_PRODUCT_MUTATION =
            "mutation ActivateProduct(\$product: ProductUpdateInput!) {\n" +
                "          productUpdate(product: \$product) {\n" +
                "            product { id }\n" +
                "            userErrors { field message }\n" +
                "          }\n" +
                "        }"

        private const val PUBLISH_TO_ONLINE_STORE_MUTATION =
            "mutation PublishToOnlineStore(\$id: ID!, \$input: [PublicationInput!]!) {\n" +
                "            publishablePublish(id: \$id, input: \$input) {\n" +
                "              publishable { ... on Product { id } }\n" +
                "              userErrors { field message }\n" +
                "            }\n" +
                "          }"
    }
}
