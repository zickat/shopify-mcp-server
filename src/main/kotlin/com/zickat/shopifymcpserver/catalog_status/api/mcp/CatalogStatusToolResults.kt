package com.zickat.shopifymcpserver.catalog_status.api.mcp

import com.zickat.shopifymcpserver.catalog_status.domain.models.SearchResourceType
import com.zickat.shopifymcpserver.catalog_status.domain.models.SearchResourcesResult
import com.zickat.shopifymcpserver.shared_kernel.MAX_SEARCH_PAGES
import com.zickat.shopifymcpserver.shared_kernel.UseCaseError
import com.zickat.shopifymcpserver.shared_kernel.UseCaseErrorException
import io.modelcontextprotocol.spec.McpSchema.CallToolResult

object CatalogStatusToolResults {

    fun errorResult(storeSlug: String, error: UseCaseError): CallToolResult =
        withBanner(storeSlug, UseCaseErrorException(error).message ?: "technical.error", isError = true)

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

    fun invalidResourceType(storeSlug: String, value: String): CallToolResult =
        withBanner(storeSlug, "Type de ressource invalide : \"$value\" (attendu \"collection\" ou \"article\").", isError = true)

    private fun withBanner(storeSlug: String, text: String, isError: Boolean = false): CallToolResult =
        CallToolResult.builder()
            .isError(isError)
            .addTextContent("Boutique : $storeSlug")
            .addTextContent(text)
            .build()
}
