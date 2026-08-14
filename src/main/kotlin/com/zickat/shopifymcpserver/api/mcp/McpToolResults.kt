package com.zickat.shopifymcpserver.api.mcp

import com.zickat.shopifymcpserver.products.exposed_interface.model.GetEnrichedContentOutcome
import com.zickat.shopifymcpserver.products.exposed_interface.model.GetEnrichedContentResult
import com.zickat.shopifymcpserver.products.exposed_interface.model.GetRawContentOutcome
import com.zickat.shopifymcpserver.products.exposed_interface.model.GetRawContentResult
import com.zickat.shopifymcpserver.products.exposed_interface.model.ListOrphanProductsResult
import com.zickat.shopifymcpserver.products.exposed_interface.model.ListToReviewResult
import com.zickat.shopifymcpserver.products.exposed_interface.model.MarkBlockedOutcome
import com.zickat.shopifymcpserver.products.exposed_interface.model.MarkBlockedResult
import com.zickat.shopifymcpserver.products.exposed_interface.model.PublishResourceOutcome
import com.zickat.shopifymcpserver.products.exposed_interface.model.PublishResourceResult
import com.zickat.shopifymcpserver.products.exposed_interface.model.SearchProductsResult
import com.zickat.shopifymcpserver.products.exposed_interface.model.UnpublishResourceOutcome
import com.zickat.shopifymcpserver.products.exposed_interface.model.UnpublishResourceResult
import com.zickat.shopifymcpserver.shared_kernel.UseCaseError
import com.zickat.shopifymcpserver.shared_kernel.UseCaseErrorException
import com.zickat.shopifymcpserver.tenancy.exposed_interface.model.GrantedStore
import io.modelcontextprotocol.spec.McpSchema.CallToolResult

object McpToolResults {

    fun describeStores(stores: List<GrantedStore>, activeStoreId: String?): CallToolResult {
        val body = if (stores.isEmpty()) {
            "Aucune boutique accordée à cette identité."
        } else {
            val lines = stores.joinToString("\n") { store ->
                if (store.storeId == activeStoreId) "  - ${store.slug}  ← active" else "  - ${store.slug}"
            }
            val how = if (activeStoreId != null) {
                "Sélection faite dans cette session avec use_store."
            } else {
                "Aucune boutique sélectionnée — les outils refuseront d'agir tant que ce n'est pas fait."
            }
            "Boutiques configurées sur ce poste :\n$lines\n\n$how"
        }
        return CallToolResult.builder().addTextContent(body).build()
    }

    fun storeActivated(storeSlug: String): CallToolResult =
        CallToolResult.builder()
            .addTextContent("Boutique : $storeSlug")
            .addTextContent(
                "Boutique active : $storeSlug. Tout ce qui avait été mémorisé pour la boutique " +
                    "précédente a été vidé — jeton d'accès, configuration, profil de marque et prompts " +
                    "d'image seront rechargés au prochain appel.",
            )
            .build()

    fun invalidGidType(storeSlug: String, label: String, value: String, expectedType: String): CallToolResult =
        withBanner(
            storeSlug,
            "$label invalide : \"$value\" n'est pas un identifiant $expectedType (attendu gid://shopify/$expectedType/...).",
            isError = true,
        )

    fun searchProductsResult(storeSlug: String, result: SearchProductsResult): CallToolResult =
        withBanner(storeSlug, result.text)

    fun getRawContentResult(storeSlug: String, result: GetRawContentResult): CallToolResult =
        when (result.outcome) {
            GetRawContentOutcome.NOT_FOUND ->
                withBanner(storeSlug, "Produit introuvable : ${result.productId}", isError = true)
            GetRawContentOutcome.FOUND ->
                withBanner(storeSlug, requireNotNull(result.text))
        }

    fun getEnrichedContentResult(storeSlug: String, result: GetEnrichedContentResult): CallToolResult =
        when (result.outcome) {
            GetEnrichedContentOutcome.NOT_FOUND ->
                withBanner(storeSlug, "Produit introuvable : ${result.productId}", isError = true)
            GetEnrichedContentOutcome.FOUND ->
                withBanner(storeSlug, requireNotNull(result.text))
        }

    fun listToReviewResult(storeSlug: String, result: ListToReviewResult): CallToolResult =
        withBanner(storeSlug, result.text)

    fun listOrphanProductsResult(storeSlug: String, result: ListOrphanProductsResult): CallToolResult =
        withBanner(storeSlug, result.text)

    fun invalidToReviewResourceType(storeSlug: String, value: String): CallToolResult =
        withBanner(
            storeSlug,
            "Type de ressource invalide : \"$value\" (attendu \"product\", \"collection\" ou \"article\").",
            isError = true,
        )

    fun errorResult(storeSlug: String, error: UseCaseError): CallToolResult =
        withBanner(storeSlug, UseCaseErrorException(error).message ?: "technical.error", isError = true)

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

    private fun withBanner(storeSlug: String, text: String, isError: Boolean = false): CallToolResult =
        CallToolResult.builder()
            .isError(isError)
            .addTextContent("Boutique : $storeSlug")
            .addTextContent(text)
            .build()
}
