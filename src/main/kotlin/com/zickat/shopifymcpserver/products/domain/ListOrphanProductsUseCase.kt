package com.zickat.shopifymcpserver.products.domain

import arrow.core.Either
import arrow.core.raise.either
import com.zickat.shopifymcpserver.products.exposed_interface.model.ListOrphanProductsResult
import com.zickat.shopifymcpserver.shared_kernel.MAX_SEARCH_PAGES
import com.zickat.shopifymcpserver.shared_kernel.TechnicalError
import com.zickat.shopifymcpserver.shared_kernel.UseCaseError
import com.zickat.shopifymcpserver.shopify.exposed_interface.ShopifyAdminGateway
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import org.springframework.stereotype.Component

private val ORPHAN_SCAN_COLLECTIONS_QUERY =
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

private val ORPHAN_SCAN_PRODUCTS_QUERY =
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

private data class OrphanProduct(val id: String, val title: String, val handle: String, val status: String, val contentStatus: String)

@Component
class ListOrphanProductsUseCase(
    private val shopifyAdminGateway: ShopifyAdminGateway,
) {

    fun execute(storeId: String): Either<UseCaseError, ListOrphanProductsResult> = either {
        val categorizedProductIds = mutableSetOf<String>()
        val collectionsWithTruncatedProducts = mutableListOf<String>()
        var collectionsTruncated = false

        var collCursor: String? = null
        var collPage = 0
        do {
            val variables = buildJsonObject { put("cursor", collCursor?.let { JsonPrimitive(it) } ?: JsonNull) }
            val response = shopifyAdminGateway.executeGraphQL(storeId, ORPHAN_SCAN_COLLECTIONS_QUERY, variables).bind()
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
                    val title = node["title"]?.jsonPrimitive?.contentOrNull.orEmpty()
                    val id = node["id"]?.jsonPrimitive?.contentOrNull.orEmpty()
                    collectionsWithTruncatedProducts += "$title ($id)"
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

        val orphans = mutableListOf<OrphanProduct>()
        var prodCursor: String? = null
        var prodPage = 0
        var productsTruncated = false
        do {
            val variables = buildJsonObject { put("cursor", prodCursor?.let { JsonPrimitive(it) } ?: JsonNull) }
            val response = shopifyAdminGateway.executeGraphQL(storeId, ORPHAN_SCAN_PRODUCTS_QUERY, variables).bind()
            val connection = (response as? JsonObject)?.get("products") as? JsonObject
                ?: raise(TechnicalError("shopify.graphql.response.malformed"))

            (connection["edges"] as? JsonArray).orEmpty().forEach { edge ->
                val node = (edge as? JsonObject)?.get("node") as? JsonObject ?: return@forEach
                val id = node["id"]?.jsonPrimitive?.contentOrNull ?: return@forEach
                val status = node["status"]?.jsonPrimitive?.contentOrNull.orEmpty()
                if (status == "ARCHIVED") return@forEach
                if (id in categorizedProductIds) return@forEach

                val contentStatusValue = ((node["contentStatus"] as? JsonObject)?.get("value"))?.jsonPrimitive?.contentOrNull
                val summaryPointsValue = ((node["summaryPoints"] as? JsonObject)?.get("value"))?.jsonPrimitive?.contentOrNull
                val hasSummaryPoints = RichText.parseStringArray(summaryPointsValue).isNotEmpty()

                orphans += OrphanProduct(
                    id = id,
                    title = node["title"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                    handle = node["handle"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                    status = status,
                    contentStatus = deriveContentStatus(contentStatusValue, hasSummaryPoints),
                )
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

        val truncationNotes = mutableListOf<String>()
        if (collectionsTruncated) {
            truncationNotes += "⚠️ Balayage des collections tronqué à ${MAX_SEARCH_PAGES * 50} collections — l'ensemble " +
                "des produits déjà catégorisés peut être incomplet, ce listing n'est alors PAS garanti exhaustif."
        }
        if (collectionsWithTruncatedProducts.isNotEmpty()) {
            truncationNotes += "⚠️ ${collectionsWithTruncatedProducts.size} collection(s) dépassent 250 produits (pagination " +
                "interne non exhaustive) : ${collectionsWithTruncatedProducts.joinToString(", ")} — les produits au-delà " +
                "de cette borne n'ont pas pu être exclus avec certitude, ce listing n'est alors PAS garanti exhaustif."
        }
        if (productsTruncated) {
            truncationNotes += "⚠️ Balayage des produits tronqué à ${MAX_SEARCH_PAGES * 50} produits — des produits orphelins " +
                "au-delà de cette borne peuvent exister sans être listés ici."
        }
        val truncationSuffix = if (truncationNotes.isNotEmpty()) "\n\n${truncationNotes.joinToString("\n")}" else ""

        val text = if (orphans.isEmpty()) {
            "Aucun produit orphelin actuellement (tout produit ACTIVE/DRAFT appartient à au moins " +
                "une collection).$truncationSuffix"
        } else {
            val lines = orphans.joinToString("\n") {
                "- ${it.title} (${it.handle}) — statut Shopify: ${it.status}, pipeline: ${it.contentStatus} — id: ${it.id}"
            }
            "${orphans.size} produit(s) orphelin(s) (aucune collection, ARCHIVED exclu) :\n$lines$truncationSuffix"
        }
        ListOrphanProductsResult(text)
    }
}
