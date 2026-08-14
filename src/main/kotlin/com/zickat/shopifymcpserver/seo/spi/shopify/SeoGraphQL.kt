package com.zickat.shopifymcpserver.seo.spi.shopify

import com.zickat.shopifymcpserver.seo.domain.models.SeoMechanism
import com.zickat.shopifymcpserver.seo.domain.models.SeoSnapshot
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

internal object SeoGraphQL {

    val GET_RESOURCE_SEO_QUERY =
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

    fun node(response: JsonElement): JsonObject? = (response as? JsonObject)?.get("node") as? JsonObject

    fun typename(node: JsonObject): String? = node["__typename"]?.jsonPrimitive?.contentOrNull

    fun snapshotFrom(node: JsonObject, mechanism: SeoMechanism): SeoSnapshot {
        val title = node["title"]?.jsonPrimitive?.contentOrNull.orEmpty()
        val (metaTitle, metaDescription) = when (mechanism) {
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
        return SeoSnapshot(title, metaTitle, metaDescription)
    }

    fun nativeEntityField(typename: String): String = typename.replaceFirstChar { it.lowercase() }

    fun nativeMutationField(typename: String): String = "${nativeEntityField(typename)}Update"

    fun nativeSeoMutation(typename: String): String {
        val entityField = nativeEntityField(typename)
        val mutationField = nativeMutationField(typename)
        return "mutation Update${typename}Seo(\$input: ${typename}Input!) {\n" +
            "        $mutationField(input: \$input) { $entityField { id } userErrors { field message } }\n" +
            "      }"
    }

    fun mutationPayload(response: JsonElement, mutationField: String): JsonObject? = (response as? JsonObject)?.get(mutationField) as? JsonObject
}
