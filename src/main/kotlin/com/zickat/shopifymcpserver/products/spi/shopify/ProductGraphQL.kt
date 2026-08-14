package com.zickat.shopifymcpserver.products.spi.shopify

import com.zickat.shopifymcpserver.products.domain.models.CollectionRef
import com.zickat.shopifymcpserver.products.domain.models.ProductComplementary
import com.zickat.shopifymcpserver.products.domain.models.ProductEnrichedFetch
import com.zickat.shopifymcpserver.products.domain.models.ProductFact
import com.zickat.shopifymcpserver.products.domain.models.ProductFaqEntry
import com.zickat.shopifymcpserver.products.domain.models.ProductRawMedia
import com.zickat.shopifymcpserver.products.domain.models.ProductRawOption
import com.zickat.shopifymcpserver.products.domain.models.ProductRawSnapshot
import com.zickat.shopifymcpserver.products.domain.models.ProductRawVariant
import com.zickat.shopifymcpserver.products.domain.models.ProductSpecEntry
import com.zickat.shopifymcpserver.products.domain.models.RelatedGuide
import com.zickat.shopifymcpserver.products.domain.models.ToReviewEntry
import com.zickat.shopifymcpserver.shopify.exposed_interface.RichText
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

internal object ProductGraphQL {

    val SEARCH_PRODUCTS_QUERY =
        "query SearchProducts(\$query: String, \$cursor: String) {\n" +
            "            products(first: 50, after: \$cursor, query: \$query) {\n" +
            "              pageInfo { hasNextPage endCursor }\n" +
            "              edges {\n" +
            "                node {\n" +
            "                  id\n" +
            "                  title\n" +
            "                  handle\n" +
            "                  status\n" +
            "                  contentStatus: metafield(namespace: \"custom\", key: \"content_status\") { value }\n" +
            "                  summaryPoints: metafield(namespace: \"custom\", key: \"summary_points\") { value }\n" +
            "                }\n" +
            "              }\n" +
            "            }\n" +
            "          }"

    val ORPHAN_SCAN_COLLECTIONS_QUERY =
        "query OrphanScanCollections(\$cursor: String) {\n" +
            "            collections(first: 50, after: \$cursor) {\n" +
            "              pageInfo { hasNextPage endCursor }\n" +
            "              edges {\n" +
            "                node {\n" +
            "                  id\n" +
            "                  title\n" +
            "                  products(first: 250) {\n" +
            "                    pageInfo { hasNextPage }\n" +
            "                    nodes { id }\n" +
            "                  }\n" +
            "                }\n" +
            "              }\n" +
            "            }\n" +
            "          }"

    val ORPHAN_SCAN_PRODUCTS_QUERY =
        "query OrphanScanProducts(\$cursor: String) {\n" +
            "            products(first: 50, after: \$cursor) {\n" +
            "              pageInfo { hasNextPage endCursor }\n" +
            "              edges {\n" +
            "                node {\n" +
            "                  id\n" +
            "                  title\n" +
            "                  handle\n" +
            "                  status\n" +
            "                  contentStatus: metafield(namespace: \"custom\", key: \"content_status\") { value }\n" +
            "                  summaryPoints: metafield(namespace: \"custom\", key: \"summary_points\") { value }\n" +
            "                }\n" +
            "              }\n" +
            "            }\n" +
            "          }"

    fun listToReviewQueryFor(pluralField: String): String =
        "query ListToReview(\$cursor: String) {\n" +
            "            $pluralField(first: 50, after: \$cursor) {\n" +
            "              pageInfo { hasNextPage endCursor }\n" +
            "              edges {\n" +
            "                node {\n" +
            "                  id\n" +
            "                  title\n" +
            "                  handle\n" +
            "                  contentStatus: metafield(namespace: \"custom\", key: \"content_status\") { value }\n" +
            "                }\n" +
            "              }\n" +
            "            }\n" +
            "          }"

    val GET_RAW_CONTENT_QUERY =
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

    const val MAX_PRODUCT_MEDIA = 50

    val FETCH_PRODUCT_MEDIA_QUERY =
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

    val FETCH_ENRICHED_CONTENT_QUERY =
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

    val PRODUCT_PUBLISH_STATE_QUERY =
        "query ProductPublishState(\$id: ID!) {\n" +
            "          product(id: \$id) { id title status resourcePublicationsCount { count } }\n" +
            "        }"

    val ACTIVATE_PRODUCT_MUTATION =
        "mutation ActivateProduct(\$product: ProductUpdateInput!) {\n" +
            "          productUpdate(product: \$product) {\n" +
            "            product { id }\n" +
            "            userErrors { field message }\n" +
            "          }\n" +
            "        }"

    val PUBLISH_TO_ONLINE_STORE_MUTATION =
        "mutation PublishToOnlineStore(\$id: ID!, \$input: [PublicationInput!]!) {\n" +
            "            publishablePublish(id: \$id, input: \$input) {\n" +
            "              publishable { ... on Product { id } }\n" +
            "              userErrors { field message }\n" +
            "            }\n" +
            "          }"

    val PRODUCT_UNPUBLISH_STATE_QUERY =
        "query ProductUnpublishState(\$id: ID!) {\n" +
            "          product(id: \$id) { id title resourcePublicationsCount { count } }\n" +
            "        }"

    val UNPUBLISH_FROM_ONLINE_STORE_MUTATION =
        "mutation UnpublishProductFromOnlineStore(\$id: ID!, \$input: [PublicationInput!]!) {\n" +
            "          publishableUnpublish(id: \$id, input: \$input) {\n" +
            "            publishable { ... on Product { id } }\n" +
            "            userErrors { field message }\n" +
            "          }\n" +
            "        }"

    fun productFactOrNull(node: JsonObject): ProductFact? {
        val id = node["id"]?.jsonPrimitive?.contentOrNull ?: return null
        return ProductFact(
            id = id,
            title = node["title"]?.jsonPrimitive?.contentOrNull.orEmpty(),
            handle = node["handle"]?.jsonPrimitive?.contentOrNull.orEmpty(),
            status = node["status"]?.jsonPrimitive?.contentOrNull.orEmpty(),
            contentStatusValue = node.metafieldValue("contentStatus"),
            hasSummaryPoints = RichText.parseStringArray(node.metafieldValue("summaryPoints")).isNotEmpty(),
        )
    }

    fun toReviewEntryOrNull(node: JsonObject?): ToReviewEntry? {
        if (node == null) return null
        val contentStatus = node.metafieldValue("contentStatus")
        if (contentStatus != "to_review") return null
        return ToReviewEntry(
            id = node["id"]?.jsonPrimitive?.contentOrNull.orEmpty(),
            title = node["title"]?.jsonPrimitive?.contentOrNull.orEmpty(),
            handle = node["handle"]?.jsonPrimitive?.contentOrNull.orEmpty(),
        )
    }

    fun collectionRef(node: JsonObject): CollectionRef =
        CollectionRef(
            id = node["id"]?.jsonPrimitive?.contentOrNull.orEmpty(),
            title = node["title"]?.jsonPrimitive?.contentOrNull.orEmpty(),
        )

    fun enrichedFetchFrom(product: JsonObject): ProductEnrichedFetch {
        val specs = product.referenceNodes("specs").map { node ->
            ProductSpecEntry(node.fieldValue("label"), node.fieldValue("value"))
        }
        val faq = product.referenceNodes("faq").map { node ->
            ProductFaqEntry(node.fieldValue("question"), node.fieldValue("answer"))
        }
        return ProductEnrichedFetch(
            title = product["title"]?.jsonPrimitive?.contentOrNull.orEmpty(),
            descriptionHtml = product["descriptionHtml"]?.jsonPrimitive?.contentOrNull.orEmpty(),
            status = product["status"]?.jsonPrimitive?.contentOrNull.orEmpty(),
            contentStatusValue = product.metafieldValue("contentStatus"),
            productType = product["productType"]?.jsonPrimitive?.contentOrNull.orEmpty(),
            tags = (product["tags"] as? JsonArray).orEmpty().mapNotNull { it.jsonPrimitive.contentOrNull },
            originalTitle = product.metafieldValue("originalTitle"),
            originalDescriptionHtml = product.metafieldValue("originalDescriptionHtml"),
            summaryPoints = RichText.parseStringArray(product.metafieldValue("summaryPoints")),
            whyRecommend = RichText.toPlainText(product.metafieldValue("whyRecommend")),
            howToUse = RichText.toPlainText(product.metafieldValue("howToUse")),
            specs = specs,
            faq = faq,
            complementaryProducts = product.productReferences("complementaryProducts")
                .map { (id, title) -> ProductComplementary(id, title) },
            relatedGuides = product.articleReferences("relatedGuides")
                .map { (id, handle, title) -> RelatedGuide(id, handle, title) },
            relatedGuidesSourceRaw = product.metafieldValue("relatedGuidesSource"),
            idealFor = RichText.parseStringArray(product.metafieldValue("idealFor")),
        )
    }

    fun rawSnapshotFrom(product: JsonObject, media: List<ProductRawMedia>): ProductRawSnapshot {
        val priceRange = product["priceRangeV2"] as? JsonObject
        val minPrice = priceRange?.get("minVariantPrice") as? JsonObject
        val maxPrice = priceRange?.get("maxVariantPrice") as? JsonObject
        val minAmount = minPrice?.get("amount")?.jsonPrimitive?.contentOrNull.orEmpty()
        val maxAmount = maxPrice?.get("amount")?.jsonPrimitive?.contentOrNull.orEmpty()
        val currency = minPrice?.get("currencyCode")?.jsonPrimitive?.contentOrNull.orEmpty()

        val rawVariants = (((product["variants"] as? JsonObject)?.get("edges")) as? JsonArray).orEmpty().mapNotNull { edge ->
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
            Triple(id, combo, mediaId)
        }

        val variantComboByMediaId = mutableMapOf<String, MutableList<String>>()
        rawVariants.forEach { (_, combo, mediaId) ->
            if (mediaId != null) variantComboByMediaId.getOrPut(mediaId) { mutableListOf() }.add(combo)
        }

        val mediaWithCombos = media.map { it.copy(variantCombos = variantComboByMediaId[it.id].orEmpty()) }

        val options = (product["options"] as? JsonArray).orEmpty().mapNotNull { option ->
            val o = option as? JsonObject ?: return@mapNotNull null
            val name = o["name"]?.jsonPrimitive?.contentOrNull.orEmpty()
            val values = (o["optionValues"] as? JsonArray).orEmpty().mapNotNull {
                (it as? JsonObject)?.get("name")?.jsonPrimitive?.contentOrNull
            }
            ProductRawOption(name, values)
        }

        return ProductRawSnapshot(
            title = product["title"]?.jsonPrimitive?.contentOrNull.orEmpty(),
            handle = product["handle"]?.jsonPrimitive?.contentOrNull.orEmpty(),
            status = product["status"]?.jsonPrimitive?.contentOrNull.orEmpty(),
            descriptionHtml = product["descriptionHtml"]?.jsonPrimitive?.contentOrNull.orEmpty(),
            minPrice = minAmount,
            maxPrice = maxAmount,
            currency = currency,
            media = mediaWithCombos,
            options = options,
            variants = rawVariants.map { (id, combo, _) -> ProductRawVariant(id, combo) },
        )
    }

    fun mediaItemsFrom(response: JsonObject?): List<ProductRawMedia> {
        val mediaConnection = (response?.get("product") as? JsonObject)?.get("media") as? JsonObject
        return (mediaConnection?.get("edges") as? JsonArray).orEmpty().mapNotNull { edge ->
            val node = (edge as? JsonObject)?.get("node") as? JsonObject ?: return@mapNotNull null
            val id = node["id"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
            if (!id.startsWith("gid://shopify/MediaImage/")) return@mapNotNull null
            val image = node["image"] as? JsonObject
            ProductRawMedia(
                id = id,
                url = image?.get("url")?.jsonPrimitive?.contentOrNull,
                altText = node["alt"]?.jsonPrimitive?.contentOrNull ?: image?.get("altText")?.jsonPrimitive?.contentOrNull,
                variantCombos = emptyList(),
            )
        }
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
