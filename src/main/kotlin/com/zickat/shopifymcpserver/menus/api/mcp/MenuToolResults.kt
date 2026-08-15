package com.zickat.shopifymcpserver.menus.api.mcp

import com.zickat.shopifymcpserver.menus.domain.AddMenuItemOutcome
import com.zickat.shopifymcpserver.menus.domain.AddMenuItemResult
import com.zickat.shopifymcpserver.menus.domain.CreateMenuOutcome
import com.zickat.shopifymcpserver.menus.domain.CreateMenuResult
import com.zickat.shopifymcpserver.menus.domain.DeleteMenuOutcome
import com.zickat.shopifymcpserver.menus.domain.DeleteMenuResult
import com.zickat.shopifymcpserver.menus.domain.RemoveMenuItemOutcome
import com.zickat.shopifymcpserver.menus.domain.RemoveMenuItemResult
import com.zickat.shopifymcpserver.menus.domain.ReorderMenuItemsOutcome
import com.zickat.shopifymcpserver.menus.domain.ReorderMenuItemsResult
import com.zickat.shopifymcpserver.menus.domain.UpdateMenuItemOutcome
import com.zickat.shopifymcpserver.menus.domain.UpdateMenuItemResult
import com.zickat.shopifymcpserver.menus.domain.UpdateMenuOutcome
import com.zickat.shopifymcpserver.menus.domain.UpdateMenuResult
import com.zickat.shopifymcpserver.menus.domain.models.ListMenusResult
import com.zickat.shopifymcpserver.shared_kernel.MAX_SEARCH_PAGES
import com.zickat.shopifymcpserver.shared_kernel.UseCaseError
import com.zickat.shopifymcpserver.shared_kernel.UseCaseErrorException
import io.modelcontextprotocol.spec.McpSchema.CallToolResult

object MenuToolResults {

    fun errorResult(storeSlug: String, error: UseCaseError): CallToolResult =
        withBanner(storeSlug, UseCaseErrorException(error).message ?: "technical.error", isError = true)

    fun invalidGidType(storeSlug: String, label: String, value: String, expectedType: String): CallToolResult =
        withBanner(
            storeSlug,
            "$label invalide : \"$value\" n'est pas un identifiant $expectedType (attendu gid://shopify/$expectedType/...).",
            isError = true,
        )

    fun addMenuItemResult(storeSlug: String, result: AddMenuItemResult): CallToolResult =
        when (result.outcome) {
            AddMenuItemOutcome.ADDED -> {
                val where = result.parentItemId?.let { "sous l'item $it" } ?: "au premier niveau"
                withWarnings(
                    storeSlug,
                    "Lien \"${result.title}\" ajouté au menu \"${result.menuTitle}\" (${result.menuHandle}) $where, en " +
                        "position ${result.position} → ${result.target}.",
                    result.warnings,
                )
            }
            AddMenuItemOutcome.INVALID_ITEM -> withBanner(storeSlug, "Ajout refusé : ${result.failureDetail}", isError = true)
            AddMenuItemOutcome.FAILED ->
                withBanner(storeSlug, "Échec de l'ajout du lien \"${result.title}\" : ${result.failureDetail}", isError = true)
        }

    fun removeMenuItemResult(storeSlug: String, result: RemoveMenuItemResult): CallToolResult =
        when (result.outcome) {
            RemoveMenuItemOutcome.REMOVED -> {
                val detail = if (result.removedIds.size > 1) {
                    "\nSous-arbre supprimé — ${result.removedIds.size} item_id :\n" + result.removedIds.joinToString("\n") { "    $it" }
                } else {
                    ""
                }
                withWarnings(
                    storeSlug,
                    "Lien retiré du menu \"${result.menuTitle}\" (${result.menuHandle}) — item_id: ${result.itemId}.$detail",
                    result.warnings,
                )
            }
            RemoveMenuItemOutcome.FAILED -> withBanner(storeSlug, "Échec du retrait du lien : ${result.failureDetail}", isError = true)
        }

    fun reorderMenuItemsResult(storeSlug: String, result: ReorderMenuItemsResult): CallToolResult =
        when (result.outcome) {
            ReorderMenuItemsOutcome.REORDERED -> {
                val lines = result.orderedItemIds.mapIndexed { index, id -> "    ${index + 1}. item_id: $id" }.joinToString("\n")
                withWarnings(
                    storeSlug,
                    "Menu \"${result.menuTitle}\" (${result.menuHandle}) réordonné — items ${result.scopeLabel}, nouvel ordre " +
                        "(${result.orderedItemIds.size} item(s)) :\n$lines",
                    result.warnings,
                )
            }
            ReorderMenuItemsOutcome.FAILED -> withBanner(storeSlug, "Échec du réordonnancement : ${result.failureDetail}", isError = true)
        }

    fun updateMenuItemResult(storeSlug: String, result: UpdateMenuItemResult): CallToolResult =
        when (result.outcome) {
            UpdateMenuItemOutcome.UPDATED -> {
                val details = mutableListOf<String>()
                result.titleChange?.let { details += "titre : \"${it.first}\" → \"${it.second}\"" }
                result.targetChange?.let { details += "cible : ${it.first} → ${it.second}" }
                val kept = if ((result.childCount ?: 0) > 0) " Sous-menu conservé (${result.childCount} sous-item(s))." else ""
                withWarnings(
                    storeSlug,
                    "Item modifié dans le menu \"${result.menuTitle}\" (${result.menuHandle}) — item_id: ${result.itemId} " +
                        "(inchangé).\n    ${details.joinToString("\n    ")}$kept",
                    result.warnings,
                )
            }
            UpdateMenuItemOutcome.NO_FIELD_PROVIDED -> withBanner(
                storeSlug,
                "Modification refusée : fournir au moins un de title, resource_id ou url — un appel sans aucun des trois " +
                    "serait sans effet. Aucune modification.",
                isError = true,
            )
            UpdateMenuItemOutcome.AMBIGUOUS_TARGET ->
                withBanner(storeSlug, "Fournir au plus un de resource_id ou url (pas les deux) — aucune modification.", isError = true)
            UpdateMenuItemOutcome.INVALID_TARGET -> withBanner(storeSlug, "Modification refusée : ${result.failureDetail}", isError = true)
            UpdateMenuItemOutcome.FAILED ->
                withBanner(storeSlug, "Échec de la modification de l'item : ${result.failureDetail}", isError = true)
        }

    fun updateMenuResult(storeSlug: String, result: UpdateMenuResult): CallToolResult =
        when (result.outcome) {
            UpdateMenuOutcome.UPDATED -> {
                val details = mutableListOf<String>()
                result.titleChange?.let { details += "titre : \"${it.first}\" → \"${it.second}\"" }
                result.handleChange?.let {
                    details += "handle : \"${it.first}\" → \"${it.second}\" — ANCIEN HANDLE À CONSERVER pour un retour en " +
                        "arrière ; vérifier les sections du thème qui référençaient \"${it.first}\""
                }
                withWarnings(
                    storeSlug,
                    "Menu mis à jour — id: ${result.menuId}.\n    ${details.joinToString("\n    ")}\nLes ${result.itemCount} " +
                        "item(s) du menu sont inchangés.",
                    result.warnings,
                )
            }
            UpdateMenuOutcome.NO_FIELD_PROVIDED -> withBanner(
                storeSlug,
                "Modification refusée : fournir au moins un de title ou handle. Un appel sans aucun des deux réécrirait " +
                    "tout l'arbre d'items du menu sans rien changer — l'opération la plus risquée du connecteur, pour " +
                    "rien. Aucune modification.",
                isError = true,
            )
            UpdateMenuOutcome.HANDLE_CHANGE_NOT_CONFIRMED -> withBanner(
                storeSlug,
                "Changement de handle refusé : passer de l'ancien handle à \"${result.requestedHandle}\" casserait toute " +
                    "référence du thème à ce menu (section.settings.menu stocke un handle, pas un id) — la zone de " +
                    "navigation disparaîtrait sans erreur ni log. Relancer avec confirm_handle_change: true si c'est bien " +
                    "l'intention. Aucune modification.",
                isError = true,
            )
            UpdateMenuOutcome.FAILED -> withBanner(storeSlug, "Échec de la mise à jour du menu : ${result.failureDetail}", isError = true)
        }

    fun createMenuResult(storeSlug: String, result: CreateMenuResult): CallToolResult =
        when (result.outcome) {
            CreateMenuOutcome.CREATED -> withWarnings(
                storeSlug,
                "Menu \"${result.title}\" créé — handle attribué : \"${result.handle}\", id: ${result.menuId}.\n${result.block}",
                result.warnings,
            )
            CreateMenuOutcome.INVALID_ITEM -> withBanner(storeSlug, "Création refusée : ${result.failureDetail}", isError = true)
            CreateMenuOutcome.FAILED ->
                withBanner(storeSlug, "Échec de la création du menu \"${result.title}\" : ${result.failureDetail}", isError = true)
        }

    fun deleteMenuResult(storeSlug: String, result: DeleteMenuResult): CallToolResult =
        when (result.outcome) {
            DeleteMenuOutcome.DELETED -> withBanner(
                storeSlug,
                "Menu \"${result.title}\" (${result.handle}) supprimé définitivement — ${result.itemCount} item(s) " +
                    "détruit(s) avec lui, sous-menus compris. Cette suppression est IRRÉVERSIBLE côté Shopify.\n\n" +
                    "Instantané complet du menu supprimé — seule copie restante, et seul plan de reconstruction (à " +
                    "rejouer par create_menu puis add_menu_item) :\n\n${result.snapshot}",
            )
            DeleteMenuOutcome.NOT_FOUND -> withBanner(storeSlug, "Menu introuvable : ${result.menuId}", isError = true)
            DeleteMenuOutcome.REFUSED -> withBanner(
                storeSlug,
                "delete_menu refusé pour \"${result.title}\" (${result.handle}) — ${result.refusals.size} raison(s), " +
                    "aucune suppression :\n" + result.refusals.mapIndexed { index, r -> "${index + 1}. $r" }.joinToString("\n"),
                isError = true,
            )
            DeleteMenuOutcome.FAILED ->
                withBanner(storeSlug, "Échec de la suppression de \"${result.title}\" : ${result.failureDetail}", isError = true)
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

    private fun withWarnings(storeSlug: String, text: String, warnings: List<String>): CallToolResult =
        withBanner(storeSlug, if (warnings.isEmpty()) text else "$text\n\n${warnings.joinToString("\n\n")}")

    private fun withBanner(storeSlug: String, text: String, isError: Boolean = false): CallToolResult =
        CallToolResult.builder()
            .isError(isError)
            .addTextContent("Boutique : $storeSlug")
            .addTextContent(text)
            .build()
}
