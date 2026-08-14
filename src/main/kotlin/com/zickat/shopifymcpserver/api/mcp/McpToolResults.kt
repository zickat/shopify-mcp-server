package com.zickat.shopifymcpserver.api.mcp

import com.zickat.shopifymcpserver.catalog_status.exposed_interface.model.SearchResourceType
import com.zickat.shopifymcpserver.catalog_status.exposed_interface.model.SearchResourcesResult
import com.zickat.shopifymcpserver.menus.exposed_interface.model.ListMenusResult
import com.zickat.shopifymcpserver.metaobjects.exposed_interface.model.CreateMetaobjectOutcome
import com.zickat.shopifymcpserver.metaobjects.exposed_interface.model.CreateMetaobjectResult
import com.zickat.shopifymcpserver.metaobjects.exposed_interface.model.DeleteMetaobjectOutcome
import com.zickat.shopifymcpserver.metaobjects.exposed_interface.model.DeleteMetaobjectResult
import com.zickat.shopifymcpserver.metaobjects.exposed_interface.model.GetMetaobjectOutcome
import com.zickat.shopifymcpserver.metaobjects.exposed_interface.model.GetMetaobjectResult
import com.zickat.shopifymcpserver.metaobjects.exposed_interface.model.ListMetaobjectsResult
import com.zickat.shopifymcpserver.metaobjects.exposed_interface.model.UpdateMetaobjectOutcome
import com.zickat.shopifymcpserver.metaobjects.exposed_interface.model.UpdateMetaobjectResult
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
import com.zickat.shopifymcpserver.redirects.exposed_interface.model.CreateRedirectOutcome
import com.zickat.shopifymcpserver.redirects.exposed_interface.model.CreateRedirectStatus
import com.zickat.shopifymcpserver.redirects.exposed_interface.model.RequiredRedirectField
import com.zickat.shopifymcpserver.shared_kernel.MAX_SEARCH_PAGES
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

    fun createRedirectResult(storeSlug: String, fromPath: String, toPath: String, outcome: CreateRedirectOutcome): CallToolResult =
        when (outcome.status) {
            CreateRedirectStatus.CREATED ->
                withBanner(storeSlug, "Redirection créée : $fromPath → $toPath.")
            CreateRedirectStatus.ALREADY_EXISTS ->
                withBanner(
                    storeSlug,
                    "Redirection déjà en place pour $fromPath (aucune action nécessaire). Une redirection " +
                        "existe déjà pour ce chemin — sa cible n'a pas été vérifiée ni modifiée vers $toPath " +
                        "(cet outil crée, il ne met pas à jour une cible existante).",
                )
            CreateRedirectStatus.FAILED ->
                withBanner(
                    storeSlug,
                    "Échec de la création de la redirection $fromPath → $toPath : ${outcome.failureDetail}",
                    isError = true,
                )
            CreateRedirectStatus.INVALID_INPUT ->
                withBanner(storeSlug, invalidRedirectInputMessage(requireNotNull(outcome.invalidField)), isError = true)
        }

    fun searchResourcesResult(storeSlug: String, result: SearchResourcesResult): CallToolResult {
        val label = if (result.resourceType == SearchResourceType.COLLECTION) "collection(s)" else "guide(s)"
        val text = if (result.resources.isEmpty()) {
            "Aucun(e) $label trouvé(e) pour ce filtre."
        } else {
            val truncationNote = if (result.truncated) {
                " (résultats tronqués à ${MAX_SEARCH_PAGES * 50} — affiner la requête)"
            } else {
                ""
            }
            val lines = result.resources.joinToString("\n") { resource ->
                "- ${resource.title} (${resource.handle}) — statut pipeline : ${resource.contentStatus} — id: ${resource.id}"
            }
            "${result.resources.size} $label trouvé(e)(s)$truncationNote.\n\n$lines"
        }
        return withBanner(storeSlug, text)
    }

    fun listMenusResult(storeSlug: String, result: ListMenusResult): CallToolResult {
        val text = if (result.blocks.isEmpty()) {
            if (result.hadQuery) "Aucun menu trouvé pour ce filtre." else "Aucun menu trouvé sur le store."
        } else {
            val truncationNote = if (result.truncated) {
                " (résultats tronqués à ${MAX_SEARCH_PAGES * 50} — affiner la requête)"
            } else {
                ""
            }
            "${result.blocks.size} menu(s) trouvé(s)$truncationNote.\n\n${result.blocks.joinToString("\n\n")}"
        }
        return withBanner(storeSlug, text)
    }

    fun invalidResourceType(storeSlug: String, value: String): CallToolResult =
        withBanner(storeSlug, "Type de ressource invalide : \"$value\" (attendu \"collection\" ou \"article\").", isError = true)

    fun invalidGidType(storeSlug: String, label: String, value: String, expectedType: String): CallToolResult =
        withBanner(
            storeSlug,
            "$label invalide : \"$value\" n'est pas un identifiant $expectedType (attendu gid://shopify/$expectedType/...).",
            isError = true,
        )

    fun listMetaobjectsResult(storeSlug: String, result: ListMetaobjectsResult): CallToolResult =
        withBanner(storeSlug, result.text)

    fun getMetaobjectResult(storeSlug: String, result: GetMetaobjectResult): CallToolResult =
        when (result.outcome) {
            GetMetaobjectOutcome.NOT_FOUND ->
                withBanner(storeSlug, "Metaobject introuvable : ${result.metaobjectId}", isError = true)
            GetMetaobjectOutcome.FOUND ->
                withBanner(storeSlug, requireNotNull(result.text))
        }

    fun createMetaobjectResult(storeSlug: String, result: CreateMetaobjectResult): CallToolResult =
        when (result.outcome) {
            CreateMetaobjectOutcome.CREATED ->
                withBanner(storeSlug, requireNotNull(result.text))
            CreateMetaobjectOutcome.FAILED ->
                withBanner(storeSlug, "Échec de la création du metaobject \"${result.type}\" : ${result.failureDetail}", isError = true)
        }

    fun updateMetaobjectResult(storeSlug: String, result: UpdateMetaobjectResult): CallToolResult =
        when (result.outcome) {
            UpdateMetaobjectOutcome.UPDATED ->
                withBanner(storeSlug, requireNotNull(result.text))
            UpdateMetaobjectOutcome.NOT_FOUND ->
                withBanner(storeSlug, "Metaobject introuvable : ${result.metaobjectId}", isError = true)
            UpdateMetaobjectOutcome.FAILED ->
                withBanner(storeSlug, "Échec de la mise à jour du metaobject ${result.metaobjectId} : ${result.failureDetail}", isError = true)
        }

    fun deleteMetaobjectResult(storeSlug: String, result: DeleteMetaobjectResult): CallToolResult =
        when (result.outcome) {
            DeleteMetaobjectOutcome.DELETED ->
                withBanner(storeSlug, requireNotNull(result.text))
            DeleteMetaobjectOutcome.REFUSED ->
                withBanner(storeSlug, requireNotNull(result.text), isError = true)
            DeleteMetaobjectOutcome.NOT_FOUND ->
                withBanner(storeSlug, "Metaobject introuvable : ${result.metaobjectId}", isError = true)
            DeleteMetaobjectOutcome.FAILED ->
                withBanner(storeSlug, "Échec de la suppression du metaobject ${result.metaobjectId} : ${result.text}", isError = true)
        }

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

    private fun invalidRedirectInputMessage(field: RequiredRedirectField): String = when (field) {
        RequiredRedirectField.FROM_PATH -> "Paramètre requis manquant ou vide : from_path (ancien chemin à rediriger)."
        RequiredRedirectField.TO_PATH -> "Paramètre requis manquant ou vide : to_path (nouveau chemin cible)."
    }

    private fun withBanner(storeSlug: String, text: String, isError: Boolean = false): CallToolResult =
        CallToolResult.builder()
            .isError(isError)
            .addTextContent("Boutique : $storeSlug")
            .addTextContent(text)
            .build()
}
