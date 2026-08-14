package com.zickat.shopifymcpserver.seo.domain

import arrow.core.Either
import arrow.core.raise.either
import com.zickat.shopifymcpserver.seo.domain.models.SeoMechanism
import com.zickat.shopifymcpserver.shared_kernel.UseCaseError
import com.zickat.shopifymcpserver.shopify.exposed_interface.ShopifyAdminGateway
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

internal data class FetchedResourceSeo(
    val typename: String?,
    val title: String,
    val metaTitle: String?,
    val metaDescription: String?,
)

internal const val GET_RESOURCE_SEO_QUERY =
    "query GetResourceSeo(\$id: ID!) {\n" +
        "      node(id: \$id) {\n" +
        "        __typename\n" +
        "        ... on Product    { title seo { title description } }\n" +
        "        ... on Collection { title seo { title description } }\n" +
        "        ... on Article {\n" +
        "          title\n" +
        "          titleTag: metafield(namespace: \"global\", key: \"title_tag\") { value }\n" +
        "          descriptionTag: metafield(namespace: \"global\", key: \"description_tag\") { value }\n" +
        "        }\n" +
        "        ... on Page {\n" +
        "          title\n" +
        "          titleTag: metafield(namespace: \"global\", key: \"title_tag\") { value }\n" +
        "          descriptionTag: metafield(namespace: \"global\", key: \"description_tag\") { value }\n" +
        "        }\n" +
        "      }\n" +
        "    }"

internal fun fetchResourceSeo(
    gateway: ShopifyAdminGateway,
    storeId: String,
    resourceId: String,
    mechanism: SeoMechanism,
): Either<UseCaseError, FetchedResourceSeo?> = either {
    val variables = buildJsonObject { put("id", JsonPrimitive(resourceId)) }
    val response = gateway.executeGraphQL(storeId, GET_RESOURCE_SEO_QUERY, variables).bind()
    val node = (response as? JsonObject)?.get("node") as? JsonObject
    node?.let {
        val title = it["title"]?.jsonPrimitive?.contentOrNull.orEmpty()
        val (metaTitle, metaDescription) = when (mechanism) {
            SeoMechanism.NATIVE -> {
                val seo = it["seo"] as? JsonObject
                (seo?.get("title")?.jsonPrimitive?.contentOrNull) to (seo?.get("description")?.jsonPrimitive?.contentOrNull)
            }
            SeoMechanism.METAFIELD -> {
                val titleTag = it["titleTag"] as? JsonObject
                val descriptionTag = it["descriptionTag"] as? JsonObject
                (titleTag?.get("value")?.jsonPrimitive?.contentOrNull) to (descriptionTag?.get("value")?.jsonPrimitive?.contentOrNull)
            }
        }
        FetchedResourceSeo(
            typename = it["__typename"]?.jsonPrimitive?.contentOrNull,
            title = title,
            metaTitle = metaTitle,
            metaDescription = metaDescription,
        )
    }
}
