package com.zickat.shopifymcpserver.seo.spi.shopify

import arrow.core.Either
import arrow.core.raise.either
import com.zickat.shopifymcpserver.seo.domain.models.SeoMechanism
import com.zickat.shopifymcpserver.seo.domain.models.SeoResourceType
import com.zickat.shopifymcpserver.seo.domain.models.SeoSnapshot
import com.zickat.shopifymcpserver.seo.domain.repositories.SeoRepository
import com.zickat.shopifymcpserver.seo.domain.repositories.SeoWriteOutcome
import com.zickat.shopifymcpserver.shared_kernel.UseCaseError
import com.zickat.shopifymcpserver.shopify.exposed_interface.ShopifyAdminGateway
import com.zickat.shopifymcpserver.shopify.exposed_interface.ShopifyMetafieldWrite
import com.zickat.shopifymcpserver.shopify.exposed_interface.ShopifyMetafields
import com.zickat.shopifymcpserver.shopify.exposed_interface.ShopifyUserError
import com.zickat.shopifymcpserver.shopify.exposed_interface.ShopifyUserErrors
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import org.springframework.stereotype.Repository

@Repository
class SeoShopifyRepository(
    private val shopifyAdminGateway: ShopifyAdminGateway,
) : SeoRepository {

    override fun get(storeId: String, resourceType: SeoResourceType, resourceId: String): Either<UseCaseError, SeoSnapshot?> = either {
        fetchSnapshot(storeId, resourceType, resourceId).bind()
    }

    override fun update(
        storeId: String,
        resourceType: SeoResourceType,
        resourceId: String,
        seoTitle: String?,
        seoDescription: String?,
    ): Either<UseCaseError, SeoWriteOutcome> = either {
        val fetched = fetchSnapshot(storeId, resourceType, resourceId).bind() ?: return@either SeoWriteOutcome.NotFound

        val finalTitle = seoTitle ?: fetched.metaTitle
        val finalDescription = seoDescription ?: fetched.metaDescription

        val writeErrors = when (resourceType.mechanism) {
            SeoMechanism.NATIVE -> writeNativeSeo(storeId, resourceType.typename, resourceId, finalTitle, finalDescription).bind()
            SeoMechanism.METAFIELD -> writeMetafieldSeo(storeId, resourceId, seoTitle, seoDescription).bind()
        }

        if (writeErrors.isNotEmpty()) {
            SeoWriteOutcome.Failed(ShopifyUserErrors.format(writeErrors))
        } else {
            SeoWriteOutcome.Updated(fetched.title, finalTitle, finalDescription)
        }
    }

    private fun fetchSnapshot(storeId: String, resourceType: SeoResourceType, resourceId: String): Either<UseCaseError, SeoSnapshot?> = either {
        val variables = buildJsonObject { put("id", JsonPrimitive(resourceId)) }
        val response = shopifyAdminGateway.executeGraphQL(storeId, SeoGraphQL.GET_RESOURCE_SEO_QUERY, variables).bind()
        SeoGraphQL.node(response)
            ?.takeIf { SeoGraphQL.typename(it) == resourceType.typename }
            ?.let { SeoGraphQL.snapshotFrom(it, resourceType.mechanism) }
    }

    private fun writeNativeSeo(
        storeId: String,
        typename: String,
        resourceId: String,
        finalTitle: String?,
        finalDescription: String?,
    ): Either<UseCaseError, List<ShopifyUserError>> = either {
        val variables = buildJsonObject {
            put(
                "input",
                buildJsonObject {
                    put("id", JsonPrimitive(resourceId))
                    put(
                        "seo",
                        buildJsonObject {
                            put("title", finalTitle?.let { JsonPrimitive(it) } ?: JsonNull)
                            put("description", finalDescription?.let { JsonPrimitive(it) } ?: JsonNull)
                        },
                    )
                },
            )
        }
        val response = shopifyAdminGateway.executeGraphQL(storeId, SeoGraphQL.nativeSeoMutation(typename), variables).bind()
        ShopifyUserErrors.parse(SeoGraphQL.mutationPayload(response, SeoGraphQL.nativeMutationField(typename)))
    }

    private fun writeMetafieldSeo(
        storeId: String,
        resourceId: String,
        seoTitle: String?,
        seoDescription: String?,
    ): Either<UseCaseError, List<ShopifyUserError>> {
        val metafields = listOfNotNull(
            seoTitle?.let { ShopifyMetafieldWrite(resourceId, "global", "title_tag", "single_line_text_field", it) },
            seoDescription?.let { ShopifyMetafieldWrite(resourceId, "global", "description_tag", "single_line_text_field", it) },
        )
        return ShopifyMetafields.set(shopifyAdminGateway, storeId, metafields)
    }
}
