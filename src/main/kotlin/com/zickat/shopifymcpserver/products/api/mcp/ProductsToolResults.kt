package com.zickat.shopifymcpserver.products.api.mcp

import com.zickat.shopifymcpserver.products.domain.GetEnrichedContentOutcome
import com.zickat.shopifymcpserver.products.domain.GetEnrichedContentResult
import com.zickat.shopifymcpserver.products.domain.GetRawContentOutcome
import com.zickat.shopifymcpserver.products.domain.GetRawContentResult
import com.zickat.shopifymcpserver.products.domain.ListOrphanProductsResult
import com.zickat.shopifymcpserver.products.domain.ListToReviewResult
import com.zickat.shopifymcpserver.products.domain.MarkBlockedOutcome
import com.zickat.shopifymcpserver.products.domain.MarkBlockedResult
import com.zickat.shopifymcpserver.products.domain.PublishResourceOutcome
import com.zickat.shopifymcpserver.products.domain.PublishResourceResult
import com.zickat.shopifymcpserver.products.domain.SearchProductsResult
import com.zickat.shopifymcpserver.products.domain.UnpublishResourceOutcome
import com.zickat.shopifymcpserver.products.domain.UnpublishResourceResult
import com.zickat.shopifymcpserver.products.domain.models.ProductEnrichedSnapshot
import com.zickat.shopifymcpserver.products.domain.models.ProductListingEntry
import com.zickat.shopifymcpserver.products.domain.models.ProductRawSnapshot
import com.zickat.shopifymcpserver.products.domain.models.RelatedGuidesSource
import com.zickat.shopifymcpserver.shared_kernel.MAX_SEARCH_PAGES
import com.zickat.shopifymcpserver.shared_kernel.UseCaseError
import com.zickat.shopifymcpserver.shared_kernel.UseCaseErrorException
import com.zickat.shopifymcpserver.shared_kernel.stripHtml
import io.modelcontextprotocol.spec.McpSchema.CallToolResult

object ProductsToolResults {

    fun errorResult(storeSlug: String, error: UseCaseError): CallToolResult =
        withBanner(storeSlug, UseCaseErrorException(error).message ?: "technical.error", isError = true)

    fun invalidGidType(storeSlug: String, label: String, value: String, expectedType: String): CallToolResult =
        withBanner(
            storeSlug,
            "$label invalide : \"$value\" n'est pas un identifiant $expectedType (attendu gid://shopify/$expectedType/...).",
            isError = true,
        )

    fun searchProductsResult(storeSlug: String, result: SearchProductsResult): CallToolResult =
        withBanner(storeSlug, renderProductListing(result.entries, result.truncated, "Aucun produit trouvé pour ce filtre.", "produit(s) trouvé(s)"))

    fun getRawContentResult(storeSlug: String, result: GetRawContentResult): CallToolResult =
        when (result.outcome) {
            GetRawContentOutcome.NOT_FOUND ->
                withBanner(storeSlug, "Produit introuvable : ${result.productId}", isError = true)
            GetRawContentOutcome.FOUND ->
                withBanner(storeSlug, renderRawContent(requireNotNull(result.snapshot)))
        }

    fun getEnrichedContentResult(storeSlug: String, result: GetEnrichedContentResult): CallToolResult =
        when (result.outcome) {
            GetEnrichedContentOutcome.NOT_FOUND ->
                withBanner(storeSlug, "Produit introuvable : ${result.productId}", isError = true)
            GetEnrichedContentOutcome.FOUND ->
                withBanner(storeSlug, renderEnrichedContent(requireNotNull(result.snapshot)))
        }

    fun listToReviewResult(storeSlug: String, result: ListToReviewResult): CallToolResult =
        withBanner(storeSlug, renderToReview(result))

    fun listOrphanProductsResult(storeSlug: String, result: ListOrphanProductsResult): CallToolResult =
        withBanner(storeSlug, renderOrphans(result))

    fun invalidToReviewResourceType(storeSlug: String, value: String): CallToolResult =
        withBanner(
            storeSlug,
            "Type de ressource invalide : \"$value\" (attendu \"product\", \"collection\" ou \"article\").",
            isError = true,
        )

    fun invalidMarkBlockedResourceType(storeSlug: String, value: String): CallToolResult =
        withBanner(
            storeSlug,
            "Type de ressource invalide : \"$value\" (attendu \"product\", \"collection\" ou \"article\").",
            isError = true,
        )

    fun markBlockedResult(storeSlug: String, resourceType: String, resourceId: String, reason: String, result: MarkBlockedResult): CallToolResult =
        when (result.outcome) {
            MarkBlockedOutcome.MARKED ->
                withBanner(storeSlug, "Ressource $resourceType ($resourceId) marquée bloquée. Raison à restituer à l'appelant : $reason")
            MarkBlockedOutcome.FAILED ->
                withBanner(storeSlug, "Échec du marquage de $resourceType ($resourceId) : ${result.failureDetail}", isError = true)
        }

    fun invalidPublishResourceType(storeSlug: String, value: String): CallToolResult =
        withBanner(storeSlug, "Type de ressource invalide : \"$value\" (attendu \"product\").", isError = true)

    fun publishResourceResult(storeSlug: String, result: PublishResourceResult): CallToolResult =
        when (result.outcome) {
            PublishResourceOutcome.PUBLISHED -> {
                val tail = if (result.wasNeverPublished) {
                    "n'était publié sur aucun canal — publishablePublish appliqué sur Online Store."
                } else {
                    "déjà publié sur Online Store, aucune action supplémentaire."
                }
                withBanner(storeSlug, "Produit \"${result.resourceTitle}\" publié : statut ACTIVE, content_status retiré, $tail")
            }
            PublishResourceOutcome.NOT_FOUND ->
                withBanner(storeSlug, "Produit introuvable : ${result.resourceId}", isError = true)
            PublishResourceOutcome.FAILED ->
                withBanner(storeSlug, "Échec de la publication : ${result.failureDetail}", isError = true)
        }

    fun unpublishResourceResult(storeSlug: String, result: UnpublishResourceResult): CallToolResult =
        when (result.outcome) {
            UnpublishResourceOutcome.UNPUBLISHED -> {
                val before = requireNotNull(result.countBefore)
                withBanner(
                    storeSlug,
                    "Produit \"${result.resourceTitle}\" dépublié : retiré du canal Online Store ($before → ${before - 1} canal(aux)). " +
                        "Statut natif inchangé (reste ACTIVE) — le produit n'est plus visible sur le storefront.",
                )
            }
            UnpublishResourceOutcome.NOOP ->
                withBanner(storeSlug, "Produit \"${result.resourceTitle}\" : déjà non publié sur aucun canal — aucune action (no-op).")
            UnpublishResourceOutcome.NOT_FOUND ->
                withBanner(storeSlug, "Produit introuvable : ${result.resourceId}", isError = true)
            UnpublishResourceOutcome.FAILED ->
                withBanner(storeSlug, "Échec de la dépublication : ${result.failureDetail}", isError = true)
        }

    private fun renderProductListing(entries: List<ProductListingEntry>, truncated: Boolean, emptyMessage: String, countLabel: String): String =
        if (entries.isEmpty()) {
            emptyMessage
        } else {
            val truncationNote = if (truncated) " (résultats tronqués à ${MAX_SEARCH_PAGES * 50} — affiner la requête)" else ""
            val lines = entries.joinToString("\n") {
                "- ${it.title} (${it.handle}) — statut Shopify: ${it.status}, pipeline: ${it.contentStatus} — id: ${it.id}"
            }
            "${entries.size} $countLabel$truncationNote.\n\n$lines"
        }

    private fun renderOrphans(result: ListOrphanProductsResult): String {
        val truncationNotes = mutableListOf<String>()
        if (result.collectionsTruncated) {
            truncationNotes += "⚠️ Balayage des collections tronqué à ${MAX_SEARCH_PAGES * 50} collections — l'ensemble " +
                "des produits déjà catégorisés peut être incomplet, ce listing n'est alors PAS garanti exhaustif."
        }
        if (result.collectionsWithTruncatedProducts.isNotEmpty()) {
            val refs = result.collectionsWithTruncatedProducts.joinToString(", ") { "${it.title} (${it.id})" }
            truncationNotes += "⚠️ ${result.collectionsWithTruncatedProducts.size} collection(s) dépassent 250 produits (pagination " +
                "interne non exhaustive) : $refs — les produits au-delà " +
                "de cette borne n'ont pas pu être exclus avec certitude, ce listing n'est alors PAS garanti exhaustif."
        }
        if (result.productsTruncated) {
            truncationNotes += "⚠️ Balayage des produits tronqué à ${MAX_SEARCH_PAGES * 50} produits — des produits orphelins " +
                "au-delà de cette borne peuvent exister sans être listés ici."
        }
        val truncationSuffix = if (truncationNotes.isNotEmpty()) "\n\n${truncationNotes.joinToString("\n")}" else ""

        return if (result.orphans.isEmpty()) {
            "Aucun produit orphelin actuellement (tout produit ACTIVE/DRAFT appartient à au moins " +
                "une collection).$truncationSuffix"
        } else {
            val lines = result.orphans.joinToString("\n") {
                "- ${it.title} (${it.handle}) — statut Shopify: ${it.status}, pipeline: ${it.contentStatus} — id: ${it.id}"
            }
            "${result.orphans.size} produit(s) orphelin(s) (aucune collection, ARCHIVED exclu) :\n$lines$truncationSuffix"
        }
    }

    private fun renderToReview(result: ListToReviewResult): String =
        if (result.entries.isEmpty()) {
            "Aucun(e) ${result.resourceType.toolValue} à review actuellement."
        } else {
            val lines = result.entries.joinToString("\n") { "- ${it.title} (${it.handle}) — id: ${it.id}" }
            "${result.entries.size} ${result.resourceType.toolValue}(s) à review :\n$lines"
        }

    private fun renderRawContent(snapshot: ProductRawSnapshot): String {
        val price = if (snapshot.minPrice == snapshot.maxPrice) {
            "${snapshot.minPrice} ${snapshot.currency}"
        } else {
            "${snapshot.minPrice}–${snapshot.maxPrice} ${snapshot.currency}"
        }

        val mediaLines = if (snapshot.media.isNotEmpty()) {
            snapshot.media.mapIndexed { i, m ->
                val altSuffix = m.altText?.let { " — alt : \"$it\"" }.orEmpty()
                val variantSuffix = if (m.variantCombos.isNotEmpty()) " — variante(s) : ${m.variantCombos.joinToString(", ")}" else ""
                "  ${i + 1}. ${m.url ?: "(url absente)"}$altSuffix$variantSuffix — id : ${m.id}"
            }
        } else {
            listOf("  (aucune image attachée)")
        }

        val optionLines = if (snapshot.options.isNotEmpty()) {
            snapshot.options.map { "  - ${it.name} : ${it.values.joinToString(", ")}" }
        } else {
            listOf("  (aucune option de variante)")
        }

        return listOf(
            "Titre : ${snapshot.title}",
            "Handle : ${snapshot.handle}",
            "Statut Shopify : ${snapshot.status}",
            "Prix : $price",
            "Nombre d'images : ${snapshot.media.size}",
            "",
            "Images (dans l'ordre d'affichage — position 1 = image principale ; id à passer à " +
                "curate_product_images/enrich_product ; « variante(s) » = couleur/taille dont cette " +
                "image est la photo désignée, absent si l'image n'est liée à aucun variant — regarder " +
                "réellement chaque image avant de rédiger son alt via enrich_product, cf. SKILL.md) :",
            *mediaLines.toTypedArray(),
            "",
            "Description source (texte brut, balises HTML retirées) :",
            stripHtml(snapshot.descriptionHtml).ifEmpty { "(description vide)" },
            "",
            "Options de variante (libellé exact à copier tel quel pour rename_variant_options) :",
            *optionLines.toTypedArray(),
            "",
            "Variantes (gid à passer à variant_ids de link_variant_image ; combo couleur/taille pour " +
                "identifier le bon variant, tous les variants du produit sont listés qu'ils aient une " +
                "image désignée ou non) :",
            *snapshot.variants.map { "  - ${it.combo.ifEmpty { "(aucune option)" }} — variant_id : ${it.id}" }.toTypedArray(),
        ).joinToString("\n")
    }

    private fun renderEnrichedContent(snapshot: ProductEnrichedSnapshot): String {
        val specsText = if (snapshot.specs.isNotEmpty()) {
            snapshot.specs.joinToString("\n") { "  - ${it.label} : ${it.value}" }
        } else {
            "  (aucune)"
        }
        val faqText = if (snapshot.faq.isNotEmpty()) {
            snapshot.faq.joinToString("\n") { "  - Q: ${it.question}\n    R: ${it.answer}" }
        } else {
            "  (aucune)"
        }
        val complementaryText = if (snapshot.complementaryProducts.isNotEmpty()) {
            snapshot.complementaryProducts.joinToString("\n") { "  - ${it.title} (${it.id})" }
        } else {
            "  (aucun)"
        }
        val relatedGuidesText = if (snapshot.relatedGuides.isNotEmpty()) {
            snapshot.relatedGuides.joinToString("\n") { "  - ${it.title} (/blogs/guides/${it.handle}, ${it.id})" }
        } else {
            "  (aucun)"
        }
        val relatedGuidesSourceLabel = if (snapshot.relatedGuidesSource == RelatedGuidesSource.MANUAL) "manual" else "auto"
        val relatedGuidesSourceNote = if (snapshot.relatedGuidesSource == RelatedGuidesSource.MANUAL) {
            " — override figé, la projection automatique ne le touche pas"
        } else {
            " — projection automatique, plafonnée à 3 par publishedAt décroissant"
        }

        return listOf(
            "Titre : ${snapshot.title}",
            "Description actuelle (HTML, à repasser tel quel dans body_html si non modifiée) : ${snapshot.descriptionHtml.ifEmpty { "(vide)" }}",
            "Statut Shopify : ${snapshot.status}",
            "Statut pipeline : ${snapshot.pipelineStatus}",
            "productType : ${snapshot.productType.ifEmpty { "(vide)" }}",
            "Tags : ${if (snapshot.tags.isNotEmpty()) snapshot.tags.joinToString(", ") else "(aucun)"}",
            "",
            "Titre original fournisseur (avant 1ère réécriture) : " +
                (snapshot.originalTitle ?: "(non capturé — produit jamais réécrit par enrich_product)"),
            "Description originale fournisseur (avant 1ère réécriture) : " +
                if (snapshot.originalDescriptionHtml != null) {
                    stripHtml(snapshot.originalDescriptionHtml)
                } else {
                    "(non capturée — produit jamais réécrit par enrich_product)"
                },
            "",
            "Points résumé : ${if (snapshot.summaryPoints.isNotEmpty()) snapshot.summaryPoints.joinToString(" | ") else "(aucun)"}",
            "",
            "Pourquoi le recommander :",
            snapshot.whyRecommend.ifEmpty { "(vide)" },
            "",
            "Comment l'utiliser :",
            snapshot.howToUse.ifEmpty { "(vide)" },
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
            "Guides liés (related_guides, source: $relatedGuidesSourceLabel$relatedGuidesSourceNote) :",
            relatedGuidesText,
            "",
            "Idéal pour : " +
                if (snapshot.idealFor.isNotEmpty()) {
                    snapshot.idealFor.joinToString(" | ")
                } else {
                    "(aucun — plomberie de lecture seule, aucun contenu produit tant que la doctrine éditoriale n'est pas tranchée)"
                },
        ).joinToString("\n")
    }

    private fun withBanner(storeSlug: String, text: String, isError: Boolean = false): CallToolResult =
        CallToolResult.builder()
            .isError(isError)
            .addTextContent("Boutique : $storeSlug")
            .addTextContent(text)
            .build()
}
