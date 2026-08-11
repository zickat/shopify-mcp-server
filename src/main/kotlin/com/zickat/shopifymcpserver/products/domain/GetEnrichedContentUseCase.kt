package com.zickat.shopifymcpserver.products.domain

import arrow.core.Either
import arrow.core.raise.either
import com.zickat.shopifymcpserver.products.exposed_interface.model.GetEnrichedContentResult
import com.zickat.shopifymcpserver.shared_kernel.UseCaseError
import com.zickat.shopifymcpserver.shopify.exposed_interface.ShopifyAdminGateway
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import org.springframework.stereotype.Component

private val FETCH_ENRICHED_CONTENT_QUERY =
    "query FetchEnrichedContent(\$id: ID!) {\n" +
        "      product(id: \$id) {\n" +
        "        id\n" +
        "        title\n" +
        "        handle\n" +
        "        status\n" +
        "        productType\n" +
        "        tags\n" +
        "        descriptionHtml\n" +
        "        contentStatus: metafield(namespace: \"custom\", key: \"content_status\") { value }\n" +
        "        summaryPoints: metafield(namespace: \"custom\", key: \"summary_points\") { value }\n" +
        "        whyRecommend: metafield(namespace: \"custom\", key: \"why_recommend\") { value }\n" +
        "        howToUse: metafield(namespace: \"custom\", key: \"how_to_use\") { value }\n" +
        "        specs: metafield(namespace: \"custom\", key: \"specs\") {\n" +
        "          references(first: 50) {\n" +
        "            edges {\n" +
        "              node {\n" +
        "                ... on Metaobject {\n" +
        "                  label: field(key: \"label\") { value }\n" +
        "                  value: field(key: \"value\") { value }\n" +
        "                }\n" +
        "              }\n" +
        "            }\n" +
        "          }\n" +
        "        }\n" +
        "        faq: metafield(namespace: \"custom\", key: \"faq\") {\n" +
        "          references(first: 50) {\n" +
        "            edges {\n" +
        "              node {\n" +
        "                ... on Metaobject {\n" +
        "                  question: field(key: \"question\") { value }\n" +
        "                  answer: field(key: \"answer\") { value }\n" +
        "                }\n" +
        "              }\n" +
        "            }\n" +
        "          }\n" +
        "        }\n" +
        "        complementaryProducts: metafield(namespace: \"custom\", key: \"complementary_products\") {\n" +
        "          references(first: 50) {\n" +
        "            edges {\n" +
        "              node {\n" +
        "                ... on Product { id title }\n" +
        "              }\n" +
        "            }\n" +
        "          }\n" +
        "        }\n" +
        "        originalTitle: metafield(namespace: \"custom\", key: \"original_title\") { value }\n" +
        "        originalDescriptionHtml: metafield(namespace: \"custom\", key: \"original_description_html\") { value }\n" +
        "        relatedGuides: metafield(namespace: \"custom\", key: \"related_guides\") {\n" +
        "          references(first: 50) {\n" +
        "            edges {\n" +
        "              node {\n" +
        "                ... on Article { id handle title }\n" +
        "              }\n" +
        "            }\n" +
        "          }\n" +
        "        }\n" +
        "        relatedGuidesSource: metafield(namespace: \"custom\", key: \"related_guides_source\") { value }\n" +
        "        idealFor: metafield(namespace: \"custom\", key: \"ideal_for\") { value }\n" +
        "      }\n" +
        "    }"

@Component
class GetEnrichedContentUseCase(
    private val shopifyAdminGateway: ShopifyAdminGateway,
) {

    fun execute(storeId: String, productId: String): Either<UseCaseError, GetEnrichedContentResult> = either {
        val variables = buildJsonObject { put("id", JsonPrimitive(productId)) }
        val response = shopifyAdminGateway.executeGraphQL(storeId, FETCH_ENRICHED_CONTENT_QUERY, variables).bind()
        val product = (response as? JsonObject)?.get("product") as? JsonObject

        if (product == null) {
            GetEnrichedContentResult.notFound(productId)
        } else {
            GetEnrichedContentResult.found(renderText(product))
        }
    }

    private fun renderText(product: JsonObject): String {
        val title = product["title"]?.jsonPrimitive?.contentOrNull.orEmpty()
        val descriptionHtml = product["descriptionHtml"]?.jsonPrimitive?.contentOrNull.orEmpty()
        val status = product["status"]?.jsonPrimitive?.contentOrNull.orEmpty()
        val productType = product["productType"]?.jsonPrimitive?.contentOrNull.orEmpty()
        val tags = (product["tags"] as? JsonArray).orEmpty().mapNotNull { it.jsonPrimitive.contentOrNull }
        val contentStatus = product.metafieldValue("contentStatus")
        val summaryPoints = RichText.parseStringArray(product.metafieldValue("summaryPoints"))
        val whyRecommend = product.metafieldValue("whyRecommend")
        val howToUse = product.metafieldValue("howToUse")
        val originalTitle = product.metafieldValue("originalTitle")
        val originalDescriptionHtml = product.metafieldValue("originalDescriptionHtml")
        val relatedGuidesSource = if (product.metafieldValue("relatedGuidesSource") == "manual") "manual" else "auto"
        val idealFor = RichText.parseStringArray(product.metafieldValue("idealFor"))

        val specs = product.referenceNodes("specs").map { node ->
            node.fieldValue("label") to node.fieldValue("value")
        }
        val faq = product.referenceNodes("faq").map { node ->
            node.fieldValue("question") to node.fieldValue("answer")
        }
        val complementaryProducts = product.productReferences("complementaryProducts")
        val relatedGuides = product.articleReferences("relatedGuides")

        val pipelineStatus = contentStatus ?: if (summaryPoints.isNotEmpty()) "published" else "untreated"

        val specsText = if (specs.isNotEmpty()) specs.joinToString("\n") { (label, value) -> "  - $label : $value" } else "  (aucune)"
        val faqText = if (faq.isNotEmpty()) {
            faq.joinToString("\n") { (question, answer) -> "  - Q: $question\n    R: $answer" }
        } else {
            "  (aucune)"
        }
        val complementaryText = if (complementaryProducts.isNotEmpty()) {
            complementaryProducts.joinToString("\n") { (id, productTitle) -> "  - $productTitle ($id)" }
        } else {
            "  (aucun)"
        }
        val relatedGuidesText = if (relatedGuides.isNotEmpty()) {
            relatedGuides.joinToString("\n") { (id, handle, guideTitle) -> "  - $guideTitle (/blogs/guides/$handle, $id)" }
        } else {
            "  (aucun)"
        }
        val relatedGuidesSourceNote = if (relatedGuidesSource == "manual") {
            " — override figé, la projection automatique ne le touche pas"
        } else {
            " — projection automatique, plafonnée à 3 par publishedAt décroissant"
        }

        return listOf(
            "Titre : $title",
            "Description actuelle (HTML, à repasser tel quel dans body_html si non modifiée) : ${descriptionHtml.ifEmpty { "(vide)" }}",
            "Statut Shopify : $status",
            "Statut pipeline : $pipelineStatus",
            "productType : ${productType.ifEmpty { "(vide)" }}",
            "Tags : ${if (tags.isNotEmpty()) tags.joinToString(", ") else "(aucun)"}",
            "",
            "Titre original fournisseur (avant 1ère réécriture) : ${originalTitle ?: "(non capturé — produit jamais réécrit par enrich_product)"}",
            "Description originale fournisseur (avant 1ère réécriture) : ${if (originalDescriptionHtml != null) stripHtml(originalDescriptionHtml) else "(non capturée — produit jamais réécrit par enrich_product)"}",
            "",
            "Points résumé : ${if (summaryPoints.isNotEmpty()) summaryPoints.joinToString(" | ") else "(aucun)"}",
            "",
            "Pourquoi le recommander :",
            RichText.toPlainText(whyRecommend).ifEmpty { "(vide)" },
            "",
            "Comment l'utiliser :",
            RichText.toPlainText(howToUse).ifEmpty { "(vide)" },
            "",
            "Specs :",
            specsText,
            "",
            "FAQ :",
            faqText,
            "",
            "Produits complémentaires :",
            complementaryText,
            "",
            "Guides liés (related_guides, source: $relatedGuidesSource$relatedGuidesSourceNote) :",
            relatedGuidesText,
            "",
            "Idéal pour : ${if (idealFor.isNotEmpty()) idealFor.joinToString(" | ") else "(aucun — plomberie de lecture seule, aucun contenu produit tant que la doctrine éditoriale n'est pas tranchée)"}",
        ).joinToString("\n")
    }

    private fun JsonObject.metafieldValue(key: String): String? =
        ((this[key] as? JsonObject)?.get("value"))?.jsonPrimitive?.contentOrNull

    private fun JsonObject.referenceNodes(key: String): List<JsonObject> {
        val edges = (((this[key] as? JsonObject)?.get("references") as? JsonObject)?.get("edges") as? JsonArray).orEmpty()
        return edges.mapNotNull { edge -> (edge as? JsonObject)?.get("node") as? JsonObject }
    }

    private fun JsonObject.fieldValue(key: String): String =
        ((this[key] as? JsonObject)?.get("value"))?.jsonPrimitive?.contentOrNull.orEmpty()

    private fun JsonObject.productReferences(key: String): List<Pair<String, String>> =
        referenceNodes(key).mapNotNull { node ->
            val id = node["id"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
            id to node["title"]?.jsonPrimitive?.contentOrNull.orEmpty()
        }

    private fun JsonObject.articleReferences(key: String): List<Triple<String, String, String>> =
        referenceNodes(key).mapNotNull { node ->
            val id = node["id"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
            Triple(id, node["handle"]?.jsonPrimitive?.contentOrNull.orEmpty(), node["title"]?.jsonPrimitive?.contentOrNull.orEmpty())
        }
}
