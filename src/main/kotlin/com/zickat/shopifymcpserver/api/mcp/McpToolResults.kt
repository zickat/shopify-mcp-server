package com.zickat.shopifymcpserver.api.mcp

import com.zickat.shopifymcpserver.catalog_status.exposed_interface.model.SearchResourceType
import com.zickat.shopifymcpserver.catalog_status.exposed_interface.model.SearchResourcesResult
import com.zickat.shopifymcpserver.menus.exposed_interface.model.ListMenusResult
import com.zickat.shopifymcpserver.redirects.exposed_interface.model.CreateRedirectOutcome
import com.zickat.shopifymcpserver.redirects.exposed_interface.model.CreateRedirectStatus
import com.zickat.shopifymcpserver.redirects.exposed_interface.model.RequiredRedirectField
import com.zickat.shopifymcpserver.seo.exposed_interface.model.GetSeoOutcome
import com.zickat.shopifymcpserver.seo.exposed_interface.model.GetSeoResult
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

    fun invalidSeoResourceType(storeSlug: String, value: String): CallToolResult =
        withBanner(
            storeSlug,
            "Type de ressource invalide : \"$value\" (attendu \"product\", \"collection\", \"article\" ou \"page\").",
            isError = true,
        )

    fun getSeoResult(storeSlug: String, resourceType: String, resourceId: String, result: GetSeoResult): CallToolResult =
        when (result.outcome) {
            GetSeoOutcome.NOT_FOUND ->
                withBanner(storeSlug, "Ressource $resourceType introuvable : $resourceId", isError = true)
            GetSeoOutcome.FOUND ->
                withBanner(
                    storeSlug,
                    "Ressource $resourceType \"${result.title}\" — SEO :\n" +
                        "- meta title : ${formatSeoValue(result.metaTitle)}\n" +
                        "- meta description : ${formatSeoValue(result.metaDescription)}",
                )
        }

    private fun formatSeoValue(value: String?): String = if (value != null) "\"$value\"" else "non défini"

    fun errorResult(storeSlug: String, error: UseCaseError): CallToolResult =
        withBanner(storeSlug, UseCaseErrorException(error).message ?: "technical.error", isError = true)

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
