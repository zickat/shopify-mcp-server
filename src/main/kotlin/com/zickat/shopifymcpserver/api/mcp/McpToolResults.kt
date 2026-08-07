package com.zickat.shopifymcpserver.api.mcp

import com.zickat.shopifymcpserver.redirects.exposed_interface.model.CreateRedirectOutcome
import com.zickat.shopifymcpserver.redirects.exposed_interface.model.CreateRedirectStatus
import com.zickat.shopifymcpserver.redirects.exposed_interface.model.RequiredRedirectField
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
