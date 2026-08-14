package com.zickat.shopifymcpserver.menus.api.mcp

import com.zickat.shopifymcpserver.menus.domain.models.ListMenusResult
import com.zickat.shopifymcpserver.shared_kernel.MAX_SEARCH_PAGES
import com.zickat.shopifymcpserver.shared_kernel.UseCaseError
import com.zickat.shopifymcpserver.shared_kernel.UseCaseErrorException
import io.modelcontextprotocol.spec.McpSchema.CallToolResult

object MenuToolResults {

    fun errorResult(storeSlug: String, error: UseCaseError): CallToolResult =
        withBanner(storeSlug, UseCaseErrorException(error).message ?: "technical.error", isError = true)

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

    private fun withBanner(storeSlug: String, text: String, isError: Boolean = false): CallToolResult =
        CallToolResult.builder()
            .isError(isError)
            .addTextContent("Boutique : $storeSlug")
            .addTextContent(text)
            .build()
}
