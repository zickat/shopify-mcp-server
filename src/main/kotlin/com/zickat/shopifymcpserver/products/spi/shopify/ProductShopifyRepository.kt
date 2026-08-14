package com.zickat.shopifymcpserver.products.spi.shopify

import arrow.core.Either
import arrow.core.raise.either
import com.zickat.shopifymcpserver.products.domain.models.CollectionRef
import com.zickat.shopifymcpserver.products.domain.models.OrphanScan
import com.zickat.shopifymcpserver.products.domain.models.ProductEnrichedFetch
import com.zickat.shopifymcpserver.products.domain.models.ProductFact
import com.zickat.shopifymcpserver.products.domain.models.ProductPublicationState
import com.zickat.shopifymcpserver.products.domain.models.ProductRawMedia
import com.zickat.shopifymcpserver.products.domain.models.ProductRawSnapshot
import com.zickat.shopifymcpserver.products.domain.models.ProductScan
import com.zickat.shopifymcpserver.products.domain.models.ToReviewEntry
import com.zickat.shopifymcpserver.products.domain.models.ToReviewResourceType
import com.zickat.shopifymcpserver.products.domain.repositories.ProductMetafieldWriteOutcome
import com.zickat.shopifymcpserver.products.domain.repositories.ProductPublishOutcome
import com.zickat.shopifymcpserver.products.domain.repositories.ProductRepository
import com.zickat.shopifymcpserver.products.domain.repositories.ProductUnpublishOutcome
import com.zickat.shopifymcpserver.shared_kernel.MAX_SEARCH_PAGES
import com.zickat.shopifymcpserver.shared_kernel.TechnicalError
import com.zickat.shopifymcpserver.shared_kernel.UseCaseError
import com.zickat.shopifymcpserver.shopify.exposed_interface.ShopifyAdminGateway
import com.zickat.shopifymcpserver.shopify.exposed_interface.ShopifyMetafieldWrite
import com.zickat.shopifymcpserver.shopify.exposed_interface.ShopifyMetafields
import com.zickat.shopifymcpserver.shopify.exposed_interface.ShopifyOnlineStorePublication
import com.zickat.shopifymcpserver.shopify.exposed_interface.ShopifyUserError
import com.zickat.shopifymcpserver.shopify.exposed_interface.ShopifyUserErrors
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonPrimitive
import org.springframework.stereotype.Repository

@Repository
class ProductShopifyRepository(
    private val shopifyAdminGateway: ShopifyAdminGateway,
) : ProductRepository {

    override fun search(storeId: String, query: String?): Either<UseCaseError, ProductScan> = either {
        val searchQuery = query?.trim()?.takeIf { it.isNotEmpty() }
        val facts = mutableListOf<ProductFact>()
        var cursor: String? = null
        var page = 0
        var truncated = false

        do {
            val variables = buildJsonObject {
                put("query", searchQuery?.let { JsonPrimitive(it) } ?: JsonNull)
                put("cursor", cursor?.let { JsonPrimitive(it) } ?: JsonNull)
            }
            val response = shopifyAdminGateway.executeGraphQL(storeId, ProductGraphQL.SEARCH_PRODUCTS_QUERY, variables).bind()
            val connection = (response as? JsonObject)?.get("products") as? JsonObject
                ?: raise(TechnicalError("shopify.graphql.response.malformed"))

            (connection["edges"] as? JsonArray).orEmpty().forEach { edge ->
                val node = (edge as? JsonObject)?.get("node") as? JsonObject
                if (node != null) ProductGraphQL.productFactOrNull(node)?.let { facts += it }
            }

            val pageInfo = connection["pageInfo"] as? JsonObject
            val hasNextPage = pageInfo?.get("hasNextPage")?.jsonPrimitive?.boolean ?: false
            cursor = if (hasNextPage) pageInfo?.get("endCursor")?.jsonPrimitive?.contentOrNull else null
            page += 1
            if (cursor != null && page >= MAX_SEARCH_PAGES) {
                truncated = true
                cursor = null
            }
        } while (cursor != null)

        ProductScan(facts, truncated)
    }

    override fun rawContent(storeId: String, productId: String): Either<UseCaseError, ProductRawSnapshot?> = either {
        val variables = buildJsonObject { put("id", JsonPrimitive(productId)) }
        val response = shopifyAdminGateway.executeGraphQL(storeId, ProductGraphQL.GET_RAW_CONTENT_QUERY, variables).bind()
        val product = (response as? JsonObject)?.get("product") as? JsonObject

        if (product == null) {
            null
        } else {
            val media = fetchMedia(storeId, productId).bind()
            ProductGraphQL.rawSnapshotFrom(product, media)
        }
    }

    private fun fetchMedia(storeId: String, productId: String): Either<UseCaseError, List<ProductRawMedia>> = either {
        val variables = buildJsonObject { put("id", JsonPrimitive(productId)) }
        val response = shopifyAdminGateway.executeGraphQL(storeId, ProductGraphQL.FETCH_PRODUCT_MEDIA_QUERY, variables).bind()
        ProductGraphQL.mediaItemsFrom(response as? JsonObject)
    }

    override fun enrichedContent(storeId: String, productId: String): Either<UseCaseError, ProductEnrichedFetch?> = either {
        val variables = buildJsonObject { put("id", JsonPrimitive(productId)) }
        val response = shopifyAdminGateway.executeGraphQL(storeId, ProductGraphQL.FETCH_ENRICHED_CONTENT_QUERY, variables).bind()
        val product = (response as? JsonObject)?.get("product") as? JsonObject
        product?.let { ProductGraphQL.enrichedFetchFrom(it) }
    }

    override fun listToReview(storeId: String, resourceType: ToReviewResourceType): Either<UseCaseError, List<ToReviewEntry>> = either {
        val pluralField = resourceType.pluralField
        val query = ProductGraphQL.listToReviewQueryFor(pluralField)
        val matches = mutableListOf<ToReviewEntry>()
        var cursor: String? = null
        var page = 0

        do {
            val variables = buildJsonObject { put("cursor", cursor?.let { JsonPrimitive(it) } ?: JsonNull) }
            val response = shopifyAdminGateway.executeGraphQL(storeId, query, variables).bind()
            val connection = (response as? JsonObject)?.get(pluralField) as? JsonObject
                ?: raise(TechnicalError("shopify.graphql.response.malformed"))

            (connection["edges"] as? JsonArray).orEmpty().forEach { edge ->
                val node = (edge as? JsonObject)?.get("node") as? JsonObject
                ProductGraphQL.toReviewEntryOrNull(node)?.let { matches += it }
            }

            val pageInfo = connection["pageInfo"] as? JsonObject
            val hasNextPage = pageInfo?.get("hasNextPage")?.jsonPrimitive?.boolean ?: false
            cursor = if (hasNextPage) pageInfo?.get("endCursor")?.jsonPrimitive?.contentOrNull else null
            page += 1
            if (cursor != null && page >= MAX_SEARCH_PAGES) cursor = null
        } while (cursor != null)

        matches
    }

    override fun listOrphans(storeId: String): Either<UseCaseError, OrphanScan> = either {
        val categorizedProductIds = mutableSetOf<String>()
        val collectionsWithTruncatedProducts = mutableListOf<CollectionRef>()
        var collectionsTruncated = false

        var collCursor: String? = null
        var collPage = 0
        do {
            val variables = buildJsonObject { put("cursor", collCursor?.let { JsonPrimitive(it) } ?: JsonNull) }
            val response = shopifyAdminGateway.executeGraphQL(storeId, ProductGraphQL.ORPHAN_SCAN_COLLECTIONS_QUERY, variables).bind()
            val connection = (response as? JsonObject)?.get("collections") as? JsonObject
                ?: raise(TechnicalError("shopify.graphql.response.malformed"))

            (connection["edges"] as? JsonArray).orEmpty().forEach { edge ->
                val node = (edge as? JsonObject)?.get("node") as? JsonObject ?: return@forEach
                val productsConnection = node["products"] as? JsonObject
                (productsConnection?.get("nodes") as? JsonArray).orEmpty().forEach { productNode ->
                    (productNode as? JsonObject)?.get("id")?.jsonPrimitive?.contentOrNull?.let { categorizedProductIds += it }
                }
                val productsHasNextPage = (productsConnection?.get("pageInfo") as? JsonObject)?.get("hasNextPage")?.jsonPrimitive?.boolean ?: false
                if (productsHasNextPage) {
                    collectionsWithTruncatedProducts += ProductGraphQL.collectionRef(node)
                }
            }

            val pageInfo = connection["pageInfo"] as? JsonObject
            val hasNextPage = pageInfo?.get("hasNextPage")?.jsonPrimitive?.boolean ?: false
            collCursor = if (hasNextPage) pageInfo?.get("endCursor")?.jsonPrimitive?.contentOrNull else null
            collPage += 1
            if (collCursor != null && collPage >= MAX_SEARCH_PAGES) {
                collectionsTruncated = true
                collCursor = null
            }
        } while (collCursor != null)

        val orphans = mutableListOf<ProductFact>()
        var prodCursor: String? = null
        var prodPage = 0
        var productsTruncated = false
        do {
            val variables = buildJsonObject { put("cursor", prodCursor?.let { JsonPrimitive(it) } ?: JsonNull) }
            val response = shopifyAdminGateway.executeGraphQL(storeId, ProductGraphQL.ORPHAN_SCAN_PRODUCTS_QUERY, variables).bind()
            val connection = (response as? JsonObject)?.get("products") as? JsonObject
                ?: raise(TechnicalError("shopify.graphql.response.malformed"))

            (connection["edges"] as? JsonArray).orEmpty().forEach { edge ->
                val node = (edge as? JsonObject)?.get("node") as? JsonObject ?: return@forEach
                val fact = ProductGraphQL.productFactOrNull(node) ?: return@forEach
                if (fact.status == "ARCHIVED") return@forEach
                if (fact.id in categorizedProductIds) return@forEach
                orphans += fact
            }

            val pageInfo = connection["pageInfo"] as? JsonObject
            val hasNextPage = pageInfo?.get("hasNextPage")?.jsonPrimitive?.boolean ?: false
            prodCursor = if (hasNextPage) pageInfo?.get("endCursor")?.jsonPrimitive?.contentOrNull else null
            prodPage += 1
            if (prodCursor != null && prodPage >= MAX_SEARCH_PAGES) {
                productsTruncated = true
                prodCursor = null
            }
        } while (prodCursor != null)

        OrphanScan(orphans, collectionsTruncated, collectionsWithTruncatedProducts, productsTruncated)
    }

    override fun markBlocked(storeId: String, resourceId: String): Either<UseCaseError, ProductMetafieldWriteOutcome> = either {
        val userErrors = ShopifyMetafields.set(
            shopifyAdminGateway,
            storeId,
            listOf(
                ShopifyMetafieldWrite(
                    ownerId = resourceId,
                    namespace = CONTENT_STATUS_NAMESPACE,
                    key = CONTENT_STATUS_KEY,
                    type = "single_line_text_field",
                    value = "blocked",
                ),
            ),
        ).bind()

        if (userErrors.isEmpty()) ProductMetafieldWriteOutcome.Marked else ProductMetafieldWriteOutcome.Failed(ShopifyUserErrors.format(userErrors))
    }

    override fun publish(storeId: String, resourceId: String): Either<UseCaseError, ProductPublishOutcome> = either {
        val state = readPublishState(storeId, resourceId).bind()

        if (state == null) {
            ProductPublishOutcome.NotFound
        } else {
            val activateErrors = activateProduct(storeId, resourceId).bind()
            if (activateErrors.isNotEmpty()) {
                ProductPublishOutcome.Failed(ShopifyUserErrors.format(activateErrors))
            } else {
                val deleteErrors = ShopifyMetafields.delete(
                    shopifyAdminGateway,
                    storeId,
                    resourceId,
                    CONTENT_STATUS_NAMESPACE,
                    CONTENT_STATUS_KEY,
                ).bind()
                if (deleteErrors.isNotEmpty()) {
                    ProductPublishOutcome.Failed(ShopifyUserErrors.format(deleteErrors))
                } else {
                    val wasNeverPublished = state.publicationsCount == 0
                    if (!wasNeverPublished) {
                        ProductPublishOutcome.Published(state.title, wasNeverPublished = false)
                    } else {
                        val publicationId = ShopifyOnlineStorePublication.resolveId(shopifyAdminGateway, storeId).bind()
                        val publishErrors = publishToOnlineStore(storeId, resourceId, publicationId).bind()
                        if (publishErrors.isNotEmpty()) {
                            ProductPublishOutcome.Failed(ShopifyUserErrors.format(publishErrors))
                        } else {
                            ProductPublishOutcome.Published(state.title, wasNeverPublished = true)
                        }
                    }
                }
            }
        }
    }

    override fun unpublish(storeId: String, resourceId: String): Either<UseCaseError, ProductUnpublishOutcome> = either {
        val state = readUnpublishState(storeId, resourceId).bind()

        if (state == null) {
            ProductUnpublishOutcome.NotFound
        } else if (state.publicationsCount == 0) {
            ProductUnpublishOutcome.Noop(state.title)
        } else {
            val publicationId = ShopifyOnlineStorePublication.resolveId(shopifyAdminGateway, storeId).bind()
            val unpublishErrors = unpublishFromOnlineStore(storeId, resourceId, publicationId).bind()
            if (unpublishErrors.isNotEmpty()) {
                ProductUnpublishOutcome.Failed(ShopifyUserErrors.format(unpublishErrors))
            } else {
                ProductUnpublishOutcome.Unpublished(state.title, countBefore = state.publicationsCount)
            }
        }
    }

    private fun readPublishState(storeId: String, resourceId: String): Either<UseCaseError, ProductPublicationState?> = either {
        val variables = buildJsonObject { put("id", JsonPrimitive(resourceId)) }
        val response = shopifyAdminGateway.executeGraphQL(storeId, ProductGraphQL.PRODUCT_PUBLISH_STATE_QUERY, variables).bind()
        val product = (response as? JsonObject)?.get("product") as? JsonObject
        product?.let {
            ProductPublicationState(
                title = it["title"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                publicationsCount = (it["resourcePublicationsCount"] as? JsonObject)?.get("count")?.jsonPrimitive?.int ?: 0,
            )
        }
    }

    private fun activateProduct(storeId: String, resourceId: String): Either<UseCaseError, List<ShopifyUserError>> = either {
        val variables = buildJsonObject {
            put(
                "product",
                buildJsonObject {
                    put("id", JsonPrimitive(resourceId))
                    put("status", JsonPrimitive("ACTIVE"))
                },
            )
        }
        val response = shopifyAdminGateway.executeGraphQL(storeId, ProductGraphQL.ACTIVATE_PRODUCT_MUTATION, variables).bind()
        ShopifyUserErrors.parse((response as? JsonObject)?.get("productUpdate") as? JsonObject)
    }

    private fun publishToOnlineStore(storeId: String, resourceId: String, publicationId: String): Either<UseCaseError, List<ShopifyUserError>> = either {
        val variables = buildJsonObject {
            put("id", JsonPrimitive(resourceId))
            put(
                "input",
                buildJsonArray {
                    add(buildJsonObject { put("publicationId", JsonPrimitive(publicationId)) })
                },
            )
        }
        val response = shopifyAdminGateway.executeGraphQL(storeId, ProductGraphQL.PUBLISH_TO_ONLINE_STORE_MUTATION, variables).bind()
        ShopifyUserErrors.parse((response as? JsonObject)?.get("publishablePublish") as? JsonObject)
    }

    private fun readUnpublishState(storeId: String, resourceId: String): Either<UseCaseError, ProductPublicationState?> = either {
        val variables = buildJsonObject { put("id", JsonPrimitive(resourceId)) }
        val response = shopifyAdminGateway.executeGraphQL(storeId, ProductGraphQL.PRODUCT_UNPUBLISH_STATE_QUERY, variables).bind()
        val product = (response as? JsonObject)?.get("product") as? JsonObject
        product?.let {
            ProductPublicationState(
                title = it["title"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                publicationsCount = (it["resourcePublicationsCount"] as? JsonObject)?.get("count")?.jsonPrimitive?.int ?: 0,
            )
        }
    }

    private fun unpublishFromOnlineStore(storeId: String, resourceId: String, publicationId: String): Either<UseCaseError, List<ShopifyUserError>> = either {
        val variables = buildJsonObject {
            put("id", JsonPrimitive(resourceId))
            put(
                "input",
                buildJsonArray {
                    add(buildJsonObject { put("publicationId", JsonPrimitive(publicationId)) })
                },
            )
        }
        val response = shopifyAdminGateway.executeGraphQL(storeId, ProductGraphQL.UNPUBLISH_FROM_ONLINE_STORE_MUTATION, variables).bind()
        ShopifyUserErrors.parse((response as? JsonObject)?.get("publishableUnpublish") as? JsonObject)
    }

    companion object {
        const val CONTENT_STATUS_NAMESPACE = "custom"
        const val CONTENT_STATUS_KEY = "content_status"
    }
}
