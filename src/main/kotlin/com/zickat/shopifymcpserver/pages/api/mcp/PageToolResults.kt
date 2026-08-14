package com.zickat.shopifymcpserver.pages.api.mcp

import com.zickat.shopifymcpserver.pages.domain.CreatePageOutcome
import com.zickat.shopifymcpserver.pages.domain.CreatePageResult
import com.zickat.shopifymcpserver.pages.domain.DeletePageOutcome
import com.zickat.shopifymcpserver.pages.domain.DeletePageResult
import com.zickat.shopifymcpserver.pages.domain.GetPageMetafieldsOutcome
import com.zickat.shopifymcpserver.pages.domain.GetPageMetafieldsResult
import com.zickat.shopifymcpserver.pages.domain.ListPagesResult
import com.zickat.shopifymcpserver.pages.domain.TogglePagePublishOutcome
import com.zickat.shopifymcpserver.pages.domain.TogglePagePublishResult
import com.zickat.shopifymcpserver.pages.domain.UpdatePageMetafieldsOutcome
import com.zickat.shopifymcpserver.pages.domain.UpdatePageMetafieldsResult
import com.zickat.shopifymcpserver.pages.domain.UpdatePageOutcome
import com.zickat.shopifymcpserver.pages.domain.UpdatePageResult
import com.zickat.shopifymcpserver.shared_kernel.MAX_SEARCH_PAGES
import com.zickat.shopifymcpserver.shared_kernel.UseCaseError
import com.zickat.shopifymcpserver.shared_kernel.UseCaseErrorException
import com.zickat.shopifymcpserver.tenancy.exposed_interface.AccessExposedService
import io.modelcontextprotocol.spec.McpSchema.CallToolResult

fun AccessExposedService.slugFor(identityId: String, storeId: String): String =
    listGrantedStores(identityId).fold({ emptyList() }, { it })
        .firstOrNull { it.storeId == storeId }
        ?.slug
        ?: storeId

object PageToolResults {

    fun errorResult(storeSlug: String, error: UseCaseError): CallToolResult =
        withBanner(storeSlug, UseCaseErrorException(error).message ?: "technical.error", isError = true)

    fun invalidGidType(storeSlug: String, label: String, value: String, expectedType: String): CallToolResult =
        withBanner(
            storeSlug,
            "$label invalide : \"$value\" n'est pas un identifiant $expectedType (attendu gid://shopify/$expectedType/...).",
            isError = true,
        )

    fun listPagesResult(storeSlug: String, result: ListPagesResult): CallToolResult {
        val text = if (result.pages.isEmpty()) {
            if (result.hadQuery) "Aucune page trouvée pour ce filtre." else "Aucune page trouvée sur le store."
        } else {
            val truncationNote = if (result.truncated) " (résultats tronqués à ${MAX_SEARCH_PAGES * 50} — affiner la requête)" else ""
            val lines = result.pages.joinToString("\n") { "- ${it.title} (${it.handle}) — id: ${it.id}" }
            "${result.pages.size} page(s) trouvée(s)$truncationNote.\n\n$lines"
        }
        return withBanner(storeSlug, text)
    }

    fun getPageMetafieldsResult(storeSlug: String, result: GetPageMetafieldsResult): CallToolResult =
        when (result.outcome) {
            GetPageMetafieldsOutcome.NOT_FOUND ->
                withBanner(storeSlug, "Page introuvable : ${result.pageId}", isError = true)
            GetPageMetafieldsOutcome.FOUND -> {
                val truncationNote = if (result.truncated) {
                    "\n\n⚠️ Lecture des metafields tronquée — d'autres metafields custom.* peuvent exister au-delà de la borne."
                } else {
                    ""
                }
                val keys = result.requestedKeys
                val text = if (keys != null && keys.isNotEmpty()) {
                    val found = result.metafields.filter { it.key in keys }
                    val missing = keys.filter { key -> result.metafields.none { it.key == key } }
                    val foundLines = if (found.isNotEmpty()) {
                        found.joinToString("\n") { "- ${it.key}: ${it.value} (${it.type})" }
                    } else {
                        "  (aucune des clés demandées n'est définie)"
                    }
                    val missingNote = if (missing.isNotEmpty()) "\nNon défini : ${missing.joinToString(", ")}" else ""
                    "Page \"${result.title}\" — ${found.size} metafield(s) custom.* parmi les clés demandées :\n$foundLines$missingNote$truncationNote"
                } else if (result.metafields.isEmpty()) {
                    "Page \"${result.title}\" — aucun metafield custom.* défini.$truncationNote"
                } else {
                    val lines = result.metafields.joinToString("\n") { "- ${it.key}: ${it.value} (${it.type})" }
                    "Page \"${result.title}\" — ${result.metafields.size} metafield(s) custom.* :\n$lines$truncationNote"
                }
                withBanner(storeSlug, text)
            }
        }

    fun createPageResult(storeSlug: String, result: CreatePageResult): CallToolResult =
        when (result.outcome) {
            CreatePageOutcome.CREATED -> {
                val draftNote = if (requireNotNull(result.isPublished)) {
                    ""
                } else {
                    "\nCréée en brouillon — utiliser publish_page après relecture pour la mettre en ligne."
                }
                withBanner(storeSlug, "Page \"${result.title}\" créée (${result.handle}, ${result.pageId}) — isPublished: ${result.isPublished}.$draftNote")
            }
            CreatePageOutcome.FAILED ->
                withBanner(storeSlug, "Échec de la création de la Page \"${result.title}\" : ${result.failureDetail}", isError = true)
        }

    fun updatePageResult(storeSlug: String, result: UpdatePageResult): CallToolResult =
        when (result.outcome) {
            UpdatePageOutcome.NO_OP ->
                withBanner(storeSlug, "Aucun champ fourni — aucune modification demandée.")
            UpdatePageOutcome.UPDATED -> {
                val handleNote = result.effectiveHandle?.let { "\nHandle effectif : $it" } ?: ""
                withBanner(
                    storeSlug,
                    "Page \"${result.title}\" (${result.pageId}) mise à jour — champs modifiés : ${result.changedFields.joinToString(", ")}.$handleNote",
                )
            }
            UpdatePageOutcome.NOT_FOUND ->
                withBanner(storeSlug, "Page introuvable : ${result.pageId}", isError = true)
            UpdatePageOutcome.FAILED ->
                withBanner(storeSlug, "Échec de la mise à jour de la Page \"${result.pageId}\" : ${result.failureDetail}", isError = true)
        }

    fun togglePagePublishResult(storeSlug: String, result: TogglePagePublishResult): CallToolResult =
        when (result.outcome) {
            TogglePagePublishOutcome.TOGGLED -> {
                val verb = if (requireNotNull(result.targetPublished)) "publiée" else "dépubliée"
                withBanner(storeSlug, "Page \"${result.title}\" $verb : isPublished=${result.isPublished}.")
            }
            TogglePagePublishOutcome.NO_OP -> {
                val state = if (requireNotNull(result.targetPublished)) "déjà publiée" else "déjà dépubliée (brouillon)"
                withBanner(storeSlug, "Page \"${result.title}\" : $state (isPublished: ${result.isPublished}) — aucune action (no-op).")
            }
            TogglePagePublishOutcome.NOT_FOUND ->
                withBanner(storeSlug, "Page introuvable : ${result.pageId}", isError = true)
            TogglePagePublishOutcome.FAILED ->
                withBanner(storeSlug, "Échec de la publication/dépublication de la Page ${result.pageId} : ${result.failureDetail}", isError = true)
        }

    fun updatePageMetafieldsResult(storeSlug: String, result: UpdatePageMetafieldsResult): CallToolResult =
        when (result.outcome) {
            UpdatePageMetafieldsOutcome.UPDATED -> {
                val lines = result.metafields.joinToString("\n") { "- ${it.key}: ${it.value} (${it.type})" }
                withBanner(storeSlug, "Page \"${result.title}\" mise à jour — ${result.metafields.size} metafield(s) custom.* écrit(s) :\n$lines")
            }
            UpdatePageMetafieldsOutcome.NOT_FOUND ->
                withBanner(storeSlug, "Page introuvable : ${result.pageId}", isError = true)
            UpdatePageMetafieldsOutcome.FAILED ->
                withBanner(
                    storeSlug,
                    "Échec de la mise à jour des metafields de la Page ${result.pageId} : ${result.failureDetail}",
                    isError = true,
                )
        }

    fun deletePageResult(storeSlug: String, result: DeletePageResult): CallToolResult =
        when (result.outcome) {
            DeletePageOutcome.DELETED ->
                withBanner(storeSlug, "Page \"${result.title}\" (${result.handle}) supprimée définitivement. Action irréversible (aucune restauration côté MCP).")
            DeletePageOutcome.NOT_FOUND ->
                withBanner(storeSlug, "Page introuvable : ${result.pageId}", isError = true)
            DeletePageOutcome.FAILED ->
                withBanner(storeSlug, "Échec de la suppression de la Page ${result.pageId} : ${result.failureDetail}", isError = true)
        }

    private fun withBanner(storeSlug: String, text: String, isError: Boolean = false): CallToolResult =
        CallToolResult.builder()
            .isError(isError)
            .addTextContent("Boutique : $storeSlug")
            .addTextContent(text)
            .build()
}
