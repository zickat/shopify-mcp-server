package com.zickat.shopifymcpserver.products.domain

import arrow.core.Either
import arrow.core.raise.either
import com.zickat.shopifymcpserver.products.exposed_interface.model.GetRawContentResult
import com.zickat.shopifymcpserver.shared_kernel.UseCaseError
import com.zickat.shopifymcpserver.shopify.exposed_interface.ShopifyAdminGateway
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import org.springframework.stereotype.Component

private const val MAX_PRODUCT_MEDIA = 50

private val GET_RAW_CONTENT_QUERY =
    "query GetRawContent(\$id: ID!) {\n" +
        "          product(id: \$id) {\n" +
        "            id\n" +
        "            title\n" +
        "            handle\n" +
        "            status\n" +
        "            descriptionHtml\n" +
        "            priceRangeV2 {\n" +
        "              minVariantPrice { amount currencyCode }\n" +
        "              maxVariantPrice { amount currencyCode }\n" +
        "            }\n" +
        "            variants(first: 50) {\n" +
        "              edges {\n" +
        "                node {\n" +
        "                  id\n" +
        "                  selectedOptions { name value }\n" +
        "                  media(first: 5) { edges { node { ... on MediaImage { id } } } }\n" +
        "                }\n" +
        "              }\n" +
        "            }\n" +
        "            options {\n" +
        "              id\n" +
        "              name\n" +
        "              optionValues { id name }\n" +
        "            }\n" +
        "          }\n" +
        "        }"

private val FETCH_PRODUCT_MEDIA_QUERY =
    "query FetchProductMedia(\$id: ID!) {\n" +
        "      product(id: \$id) {\n" +
        "        status\n" +
        "        media(first: $MAX_PRODUCT_MEDIA) {\n" +
        "          edges {\n" +
        "            node {\n" +
        "              ... on MediaImage {\n" +
        "                id\n" +
        "                alt\n" +
        "                image { url altText }\n" +
        "              }\n" +
        "            }\n" +
        "          }\n" +
        "        }\n" +
        "      }\n" +
        "    }"

private data class RawMediaItem(val id: String, val url: String?, val altText: String?)
private data class RawVariant(val id: String, val combo: String, val mediaId: String?)
private data class RawOption(val name: String, val values: List<String>)

@Component
class GetRawContentUseCase(
    private val shopifyAdminGateway: ShopifyAdminGateway,
) {

    fun execute(storeId: String, productId: String): Either<UseCaseError, GetRawContentResult> = either {
        val variables = buildJsonObject { put("id", JsonPrimitive(productId)) }
        val response = shopifyAdminGateway.executeGraphQL(storeId, GET_RAW_CONTENT_QUERY, variables).bind()
        val product = (response as? JsonObject)?.get("product") as? JsonObject

        if (product == null) {
            GetRawContentResult.notFound(productId)
        } else {
            val media = fetchMedia(storeId, productId).bind()
            GetRawContentResult.found(renderText(product, media))
        }
    }

    private fun fetchMedia(storeId: String, productId: String): Either<UseCaseError, List<RawMediaItem>> = either {
        val variables = buildJsonObject { put("id", JsonPrimitive(productId)) }
        val response = shopifyAdminGateway.executeGraphQL(storeId, FETCH_PRODUCT_MEDIA_QUERY, variables).bind()
        val mediaConnection = ((response as? JsonObject)?.get("product") as? JsonObject)?.get("media") as? JsonObject
        (mediaConnection?.get("edges") as? JsonArray).orEmpty().mapNotNull { edge ->
            val node = (edge as? JsonObject)?.get("node") as? JsonObject ?: return@mapNotNull null
            val id = node["id"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
            if (!id.startsWith("gid://shopify/MediaImage/")) return@mapNotNull null
            val image = node["image"] as? JsonObject
            RawMediaItem(
                id = id,
                url = image?.get("url")?.jsonPrimitive?.contentOrNull,
                altText = node["alt"]?.jsonPrimitive?.contentOrNull ?: image?.get("altText")?.jsonPrimitive?.contentOrNull,
            )
        }
    }

    private fun renderText(product: JsonObject, media: List<RawMediaItem>): String {
        val title = product["title"]?.jsonPrimitive?.contentOrNull.orEmpty()
        val handle = product["handle"]?.jsonPrimitive?.contentOrNull.orEmpty()
        val status = product["status"]?.jsonPrimitive?.contentOrNull.orEmpty()
        val descriptionHtml = product["descriptionHtml"]?.jsonPrimitive?.contentOrNull.orEmpty()

        val priceRange = product["priceRangeV2"] as? JsonObject
        val minPrice = priceRange?.get("minVariantPrice") as? JsonObject
        val maxPrice = priceRange?.get("maxVariantPrice") as? JsonObject
        val minAmount = minPrice?.get("amount")?.jsonPrimitive?.contentOrNull.orEmpty()
        val maxAmount = maxPrice?.get("amount")?.jsonPrimitive?.contentOrNull.orEmpty()
        val currency = minPrice?.get("currencyCode")?.jsonPrimitive?.contentOrNull.orEmpty()
        val price = if (minAmount == maxAmount) "$minAmount $currency" else "$minAmount–$maxAmount $currency"

        val variants = (((product["variants"] as? JsonObject)?.get("edges")) as? JsonArray).orEmpty().mapNotNull { edge ->
            val node = (edge as? JsonObject)?.get("node") as? JsonObject ?: return@mapNotNull null
            val id = node["id"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
            val combo = (node["selectedOptions"] as? JsonArray).orEmpty().joinToString(" / ") { option ->
                val o = option as? JsonObject
                val optionName = o?.get("name")?.jsonPrimitive?.contentOrNull.orEmpty()
                val optionValue = o?.get("value")?.jsonPrimitive?.contentOrNull.orEmpty()
                "$optionName: $optionValue"
            }
            val mediaEdges = ((node["media"] as? JsonObject)?.get("edges") as? JsonArray).orEmpty()
            val mediaId = mediaEdges.firstOrNull()?.let { (it as? JsonObject)?.get("node") as? JsonObject }
                ?.get("id")?.jsonPrimitive?.contentOrNull
            RawVariant(id, combo, mediaId)
        }

        val options = (product["options"] as? JsonArray).orEmpty().mapNotNull { option ->
            val o = option as? JsonObject ?: return@mapNotNull null
            val name = o["name"]?.jsonPrimitive?.contentOrNull.orEmpty()
            val values = (o["optionValues"] as? JsonArray).orEmpty().mapNotNull {
                (it as? JsonObject)?.get("name")?.jsonPrimitive?.contentOrNull
            }
            RawOption(name, values)
        }

        val variantComboByMediaId = mutableMapOf<String, MutableList<String>>()
        variants.forEach { variant ->
            val mediaId = variant.mediaId ?: return@forEach
            variantComboByMediaId.getOrPut(mediaId) { mutableListOf() }.add(variant.combo)
        }

        val mediaLines = if (media.isNotEmpty()) {
            media.mapIndexed { i, m ->
                val altSuffix = m.altText?.let { " — alt : \"$it\"" }.orEmpty()
                val combos = variantComboByMediaId[m.id].orEmpty()
                val variantSuffix = if (combos.isNotEmpty()) " — variante(s) : ${combos.joinToString(", ")}" else ""
                "  ${i + 1}. ${m.url ?: "(url absente)"}$altSuffix$variantSuffix — id : ${m.id}"
            }
        } else {
            listOf("  (aucune image attachée)")
        }

        val optionLines = if (options.isNotEmpty()) {
            options.map { "  - ${it.name} : ${it.values.joinToString(", ")}" }
        } else {
            listOf("  (aucune option de variante)")
        }

        return listOf(
            "Titre : $title",
            "Handle : $handle",
            "Statut Shopify : $status",
            "Prix : $price",
            "Nombre d'images : ${media.size}",
            "",
            "Images (dans l'ordre d'affichage — position 1 = image principale ; id à passer à " +
                "curate_product_images/enrich_product ; « variante(s) » = couleur/taille dont cette " +
                "image est la photo désignée, absent si l'image n'est liée à aucun variant — regarder " +
                "réellement chaque image avant de rédiger son alt via enrich_product, cf. SKILL.md) :",
            *mediaLines.toTypedArray(),
            "",
            "Description source (texte brut, balises HTML retirées) :",
            stripHtml(descriptionHtml).ifEmpty { "(description vide)" },
            "",
            "Options de variante (libellé exact à copier tel quel pour rename_variant_options) :",
            *optionLines.toTypedArray(),
            "",
            "Variantes (gid à passer à variant_ids de link_variant_image ; combo couleur/taille pour " +
                "identifier le bon variant, tous les variants du produit sont listés qu'ils aient une " +
                "image désignée ou non) :",
            *variants.map { "  - ${it.combo.ifEmpty { "(aucune option)" }} — variant_id : ${it.id}" }.toTypedArray(),
        ).joinToString("\n")
    }
}
