package com.zickat.shopifymcpserver.seo.domain

import arrow.core.Either
import arrow.core.raise.either
import com.zickat.shopifymcpserver.seo.domain.models.SeoMechanism
import com.zickat.shopifymcpserver.seo.domain.models.SeoResourceType
import com.zickat.shopifymcpserver.seo.exposed_interface.model.UpdateSeoResult
import com.zickat.shopifymcpserver.shared_kernel.UseCaseError
import com.zickat.shopifymcpserver.shopify.exposed_interface.ShopifyAdminGateway
import com.zickat.shopifymcpserver.shopify.exposed_interface.ShopifyMetafieldWrite
import com.zickat.shopifymcpserver.shopify.exposed_interface.ShopifyMetafields
import com.zickat.shopifymcpserver.shopify.exposed_interface.ShopifyUserError
import com.zickat.shopifymcpserver.shopify.exposed_interface.ShopifyUserErrors
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import org.springframework.stereotype.Component

@Component
class UpdateSeoUseCase(
    private val shopifyAdminGateway: ShopifyAdminGateway,
) {

    fun execute(
        storeId: String,
        resourceType: SeoResourceType,
        resourceId: String,
        seoTitle: String?,
        seoDescription: String?,
    ): Either<UseCaseError, UpdateSeoResult> = either {
        if (seoTitle == null && seoDescription == null) return@either UpdateSeoResult.NoOp

        val fetched = fetchResourceSeo(shopifyAdminGateway, storeId, resourceId, resourceType.mechanism).bind()
            ?.takeIf { it.typename == resourceType.typename }
            ?: return@either UpdateSeoResult.NotFound

        val finalTitle = seoTitle ?: fetched.metaTitle
        val finalDescription = seoDescription ?: fetched.metaDescription

        val writeErrors = when (resourceType.mechanism) {
            SeoMechanism.NATIVE -> writeNativeSeo(storeId, resourceType.typename, resourceId, finalTitle, finalDescription).bind()
            SeoMechanism.METAFIELD -> writeMetafieldSeo(storeId, resourceId, seoTitle, seoDescription).bind()
        }

        if (writeErrors.isNotEmpty()) {
            UpdateSeoResult.failed(ShopifyUserErrors.format(writeErrors))
        } else {
            UpdateSeoResult.updated(
                title = fetched.title,
                finalMetaTitle = finalTitle,
                finalMetaDescription = finalDescription,
                titleModified = seoTitle != null,
                descriptionModified = seoDescription != null,
            )
        }
    }

    private fun writeNativeSeo(
        storeId: String,
        typename: String,
        resourceId: String,
        finalTitle: String?,
        finalDescription: String?,
    ): Either<UseCaseError, List<ShopifyUserError>> = either {
        val fieldName = typename.replaceFirstChar { it.lowercase() }
        val mutation =
            "mutation Update${typename}Seo(\$input: ${typename}Input!) {\n" +
                "        ${fieldName}Update(input: \$input) { $fieldName { id } userErrors { field message } }\n" +
                "      }"
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
        val response = shopifyAdminGateway.executeGraphQL(storeId, mutation, variables).bind()
        ShopifyUserErrors.parse((response as? JsonObject)?.get("${fieldName}Update") as? JsonObject)
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
