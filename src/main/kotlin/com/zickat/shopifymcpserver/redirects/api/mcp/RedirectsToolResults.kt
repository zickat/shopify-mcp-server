package com.zickat.shopifymcpserver.redirects.api.mcp

import com.zickat.shopifymcpserver.redirects.domain.models.CreateRedirectOutcome
import com.zickat.shopifymcpserver.redirects.domain.models.CreateRedirectStatus
import com.zickat.shopifymcpserver.redirects.domain.models.RequiredRedirectField
import com.zickat.shopifymcpserver.shared_kernel.UseCaseError
import com.zickat.shopifymcpserver.shared_kernel.UseCaseErrorException
import io.modelcontextprotocol.spec.McpSchema.CallToolResult

object RedirectsToolResults {

    fun errorResult(storeSlug: String, error: UseCaseError): CallToolResult =
        withBanner(storeSlug, UseCaseErrorException(error).message ?: "technical.error", isError = true)

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
