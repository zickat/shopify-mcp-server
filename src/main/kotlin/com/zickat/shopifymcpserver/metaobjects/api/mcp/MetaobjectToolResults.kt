package com.zickat.shopifymcpserver.metaobjects.api.mcp

import com.zickat.shopifymcpserver.metaobjects.domain.CreateMetaobjectOutcome
import com.zickat.shopifymcpserver.metaobjects.domain.CreateMetaobjectResult
import com.zickat.shopifymcpserver.metaobjects.domain.DeleteMetaobjectOutcome
import com.zickat.shopifymcpserver.metaobjects.domain.DeleteMetaobjectResult
import com.zickat.shopifymcpserver.metaobjects.domain.GetMetaobjectOutcome
import com.zickat.shopifymcpserver.metaobjects.domain.GetMetaobjectResult
import com.zickat.shopifymcpserver.metaobjects.domain.ListMetaobjectsResult
import com.zickat.shopifymcpserver.metaobjects.domain.UpdateMetaobjectOutcome
import com.zickat.shopifymcpserver.metaobjects.domain.UpdateMetaobjectResult
import com.zickat.shopifymcpserver.metaobjects.domain.models.MetaobjectFieldValue
import com.zickat.shopifymcpserver.metaobjects.domain.models.MetaobjectReferenceStatus
import com.zickat.shopifymcpserver.metaobjects.domain.models.isOrphan
import com.zickat.shopifymcpserver.shared_kernel.MAX_SEARCH_PAGES
import com.zickat.shopifymcpserver.shared_kernel.UseCaseError
import com.zickat.shopifymcpserver.shared_kernel.UseCaseErrorException
import io.modelcontextprotocol.spec.McpSchema.CallToolResult

object MetaobjectToolResults {

    fun errorResult(storeSlug: String, error: UseCaseError): CallToolResult =
        withBanner(storeSlug, UseCaseErrorException(error).message ?: "technical.error", isError = true)

    fun invalidGidType(storeSlug: String, label: String, value: String, expectedType: String): CallToolResult =
        withBanner(
            storeSlug,
            "$label invalide : \"$value\" n'est pas un identifiant $expectedType (attendu gid://shopify/$expectedType/...).",
            isError = true,
        )

    fun listMetaobjectsResult(storeSlug: String, result: ListMetaobjectsResult): CallToolResult {
        val text = when (result) {
            is ListMetaobjectsResult.Definitions -> definitionsText(result)
            is ListMetaobjectsResult.Instances -> instancesText(result)
        }
        return withBanner(storeSlug, text)
    }

    fun getMetaobjectResult(storeSlug: String, result: GetMetaobjectResult): CallToolResult =
        when (result.outcome) {
            GetMetaobjectOutcome.NOT_FOUND ->
                withBanner(storeSlug, "Metaobject introuvable : ${result.metaobjectId}", isError = true)
            GetMetaobjectOutcome.FOUND -> {
                val text = "${result.metaobjectId} — type: ${result.type} — ${formatReferenceStatus(result.referenceStatus)}\n" +
                    formatFields(requireNotNull(result.fields))
                withBanner(storeSlug, text)
            }
        }

    fun createMetaobjectResult(storeSlug: String, result: CreateMetaobjectResult): CallToolResult =
        when (result.outcome) {
            CreateMetaobjectOutcome.CREATED -> {
                val fieldsText = formatFields(requireNotNull(result.fields))
                withBanner(storeSlug, "Metaobject ${result.metaobjectId} (type: ${result.type}) créé.\n$fieldsText")
            }
            CreateMetaobjectOutcome.FAILED ->
                withBanner(storeSlug, "Échec de la création du metaobject \"${result.type}\" : ${result.failureDetail}", isError = true)
        }

    fun updateMetaobjectResult(storeSlug: String, result: UpdateMetaobjectResult): CallToolResult =
        when (result.outcome) {
            UpdateMetaobjectOutcome.UPDATED -> {
                val fieldsText = formatFields(requireNotNull(result.fields))
                withBanner(storeSlug, "Metaobject ${result.metaobjectId} (type: ${result.type}) mis à jour.\n$fieldsText")
            }
            UpdateMetaobjectOutcome.NOT_FOUND ->
                withBanner(storeSlug, "Metaobject introuvable : ${result.metaobjectId}", isError = true)
            UpdateMetaobjectOutcome.FAILED ->
                withBanner(storeSlug, "Échec de la mise à jour du metaobject ${result.metaobjectId} : ${result.failureDetail}", isError = true)
        }

    fun deleteMetaobjectResult(storeSlug: String, result: DeleteMetaobjectResult): CallToolResult =
        when (result.outcome) {
            DeleteMetaobjectOutcome.DELETED -> {
                val fieldsText = formatFields(requireNotNull(result.fields))
                val orphan = result.referenceStatus?.let(::isOrphan) ?: false
                val pendingReferenceWarning = if (!orphan) {
                    "\n⚠️ Ce metaobject était encore référencé (ou sa détection restait incertaine) au moment de la " +
                        "suppression (${formatReferenceStatus(result.referenceStatus)}) — la ou les ressources qui le référençaient ne sont PAS " +
                        "nettoyées automatiquement : une référence pendante peut y subsister, à corriger séparément si besoin " +
                        "(update_collection_content pour une collection, enrich_product pour un produit)."
                } else {
                    ""
                }
                withBanner(
                    storeSlug,
                    "Metaobject ${result.metaobjectId} (type: ${result.type}) supprimé définitivement. Champs qu'il contenait :\n" +
                        "$fieldsText$pendingReferenceWarning",
                )
            }
            DeleteMetaobjectOutcome.REFUSED ->
                withBanner(
                    storeSlug,
                    "delete_metaobject refusé pour ${result.metaobjectId} (type: ${result.type}) : ${formatReferenceStatus(result.referenceStatus)}. " +
                        "Appeler à nouveau avec confirm_referenced_deletion: true pour forcer la suppression malgré cette " +
                        "référence (ou cette incertitude de détection).",
                    isError = true,
                )
            DeleteMetaobjectOutcome.NOT_FOUND ->
                withBanner(storeSlug, "Metaobject introuvable : ${result.metaobjectId}", isError = true)
            DeleteMetaobjectOutcome.FAILED ->
                withBanner(storeSlug, "Échec de la suppression du metaobject ${result.metaobjectId} : ${result.failureDetail}", isError = true)
        }

    private fun definitionsText(result: ListMetaobjectsResult.Definitions): String =
        if (result.definitions.isEmpty()) {
            "Aucune définition de metaobject trouvée sur le store."
        } else {
            val lines = result.definitions.joinToString("\n") { d -> "- ${d.type} (${d.name}) : ${d.instanceCount} instance(s)" }
            val truncationNote = if (result.truncated) {
                "\n\n⚠️ Balayage des définitions tronqué à 50 — d'autres types peuvent exister au-delà de cette borne."
            } else {
                ""
            }
            "${result.definitions.size} type(s) de metaobject trouvé(s) sur le store :\n$lines\n\n" +
                "Appeler list_metaobjects(type: \"...\") pour lister les instances d'un type.$truncationNote"
        }

    private fun instancesText(result: ListMetaobjectsResult.Instances): String =
        if (result.instances.isEmpty()) {
            "Aucun metaobject de type \"${result.type}\" trouvé."
        } else {
            val blocks = result.instances.joinToString("\n\n") { instance ->
                "${instance.id} — ${formatReferenceStatus(instance.referenceStatus)}\n${formatFields(instance.fields)}"
            }
            val truncationNote = if (result.truncated) {
                "\n\n⚠️ Balayage tronqué à ${MAX_SEARCH_PAGES * 50} instances — d'autres instances de ce type " +
                    "peuvent exister au-delà de cette borne."
            } else {
                ""
            }
            "${result.instances.size} instance(s) de type \"${result.type}\" :\n\n$blocks$truncationNote"
        }

    private fun formatReferenceStatus(status: MetaobjectReferenceStatus?): String = when (status) {
        null ->
            "statut de référence indisponible (metaobject introuvable lors de la relecture) — traité par " +
                "prudence comme potentiellement référencé"
        is MetaobjectReferenceStatus.Orphan -> "orphelin (aucune référence entrante trouvée)"
        is MetaobjectReferenceStatus.Uncertain ->
            "détection de référence incertaine (troncature de la lecture) — traité par prudence comme " +
                "potentiellement référencé, pas déclaré orphelin"
        is MetaobjectReferenceStatus.Referenced -> {
            val list = status.references.joinToString("; ") { r ->
                "${r.referencerType} \"${r.referencerTitle}\"${r.referencerId?.let { " ($it)" } ?: ""} via ${r.namespace}.${r.key}"
            }
            val truncationSuffix = if (status.truncated) " (+ éventuellement d'autres, lecture tronquée)" else ""
            "référencé par : $list$truncationSuffix"
        }
    }

    private fun formatFields(fields: List<MetaobjectFieldValue>): String =
        if (fields.isEmpty()) {
            "  (aucun champ)"
        } else {
            fields.joinToString("\n") { "  - ${it.key}: ${it.value ?: "(vide)"}" }
        }

    private fun withBanner(storeSlug: String, text: String, isError: Boolean = false): CallToolResult =
        CallToolResult.builder()
            .isError(isError)
            .addTextContent("Boutique : $storeSlug")
            .addTextContent(text)
            .build()
}
