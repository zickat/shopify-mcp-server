package com.zickat.shopifymcpserver.seo.api.mcp

import com.zickat.shopifymcpserver.seo.domain.GetSeoOutcome
import com.zickat.shopifymcpserver.seo.domain.GetSeoResult
import com.zickat.shopifymcpserver.seo.domain.UpdateSeoOutcome
import com.zickat.shopifymcpserver.seo.domain.UpdateSeoResult
import com.zickat.shopifymcpserver.shared_kernel.UseCaseError
import com.zickat.shopifymcpserver.shared_kernel.UseCaseErrorException
import io.modelcontextprotocol.spec.McpSchema.CallToolResult

object SeoToolResults {

    fun errorResult(storeSlug: String, error: UseCaseError): CallToolResult =
        withBanner(storeSlug, UseCaseErrorException(error).message ?: "technical.error", isError = true)

    fun invalidGidType(storeSlug: String, label: String, value: String, expectedType: String): CallToolResult =
        withBanner(
            storeSlug,
            "$label invalide : \"$value\" n'est pas un identifiant $expectedType (attendu gid://shopify/$expectedType/...).",
            isError = true,
        )

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

    fun updateSeoResult(storeSlug: String, resourceType: String, resourceId: String, result: UpdateSeoResult): CallToolResult =
        when (result.outcome) {
            UpdateSeoOutcome.NOT_FOUND ->
                withBanner(storeSlug, "Ressource $resourceType introuvable : $resourceId", isError = true)
            UpdateSeoOutcome.NO_OP ->
                withBanner(storeSlug, "Ressource $resourceType ($resourceId) : aucun champ SEO fourni — aucune modification (no-op).")
            UpdateSeoOutcome.FAILED ->
                withBanner(storeSlug, "Échec de la mise à jour SEO de $resourceType ($resourceId) : ${result.failureDetail}", isError = true)
            UpdateSeoOutcome.UPDATED ->
                withBanner(
                    storeSlug,
                    "Ressource $resourceType \"${result.resourceTitle}\" — SEO mis à jour :\n" +
                        "- meta title : ${formatSeoValue(result.finalMetaTitle)}  ${modifiedLabel(result.titleModified)}\n" +
                        "- meta description : ${formatSeoValue(result.finalMetaDescription)}  ${modifiedLabel(result.descriptionModified)}\n" +
                        "(content_status non modifié — une retouche SEO ne remet pas la fiche à review.)",
                )
        }

    private fun formatSeoValue(value: String?): String = if (value != null) "\"$value\"" else "non défini"

    private fun modifiedLabel(modified: Boolean): String = if (modified) "(modifié)" else "(inchangé)"

    private fun withBanner(storeSlug: String, text: String, isError: Boolean = false): CallToolResult =
        CallToolResult.builder()
            .isError(isError)
            .addTextContent("Boutique : $storeSlug")
            .addTextContent(text)
            .build()
}
