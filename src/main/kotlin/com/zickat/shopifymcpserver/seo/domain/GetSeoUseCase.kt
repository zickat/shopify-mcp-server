package com.zickat.shopifymcpserver.seo.domain

import arrow.core.Either
import arrow.core.raise.either
import com.zickat.shopifymcpserver.seo.domain.models.SeoMechanism
import com.zickat.shopifymcpserver.seo.domain.models.SeoResourceType
import com.zickat.shopifymcpserver.seo.exposed_interface.model.GetSeoResult
import com.zickat.shopifymcpserver.shared_kernel.UseCaseError
import com.zickat.shopifymcpserver.shopify.exposed_interface.ShopifyAdminGateway
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import org.springframework.stereotype.Component

private val GET_RESOURCE_SEO_QUERY =
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

@Component
class GetSeoUseCase(
    private val shopifyAdminGateway: ShopifyAdminGateway,
) {

    fun execute(storeId: String, resourceType: SeoResourceType, resourceId: String): Either<UseCaseError, GetSeoResult> = either {
        val variables = buildJsonObject { put("id", JsonPrimitive(resourceId)) }
        val response = shopifyAdminGateway.executeGraphQL(storeId, GET_RESOURCE_SEO_QUERY, variables).bind()
        val node = (response as? JsonObject)?.get("node") as? JsonObject
        val typename = node?.get("__typename")?.jsonPrimitive?.contentOrNull

        if (node == null || typename != resourceType.typename) {
            GetSeoResult.NotFound
        } else {
            val title = node["title"]?.jsonPrimitive?.contentOrNull.orEmpty()
            val (metaTitle, metaDescription) = when (resourceType.mechanism) {
                SeoMechanism.NATIVE -> {
                    val seo = node["seo"] as? JsonObject
                    (seo?.get("title")?.jsonPrimitive?.contentOrNull) to (seo?.get("description")?.jsonPrimitive?.contentOrNull)
                }
                SeoMechanism.METAFIELD -> {
                    val titleTag = node["titleTag"] as? JsonObject
                    val descriptionTag = node["descriptionTag"] as? JsonObject
                    (titleTag?.get("value")?.jsonPrimitive?.contentOrNull) to (descriptionTag?.get("value")?.jsonPrimitive?.contentOrNull)
                }
            }
            GetSeoResult.found(title, metaTitle, metaDescription)
        }
    }
}
