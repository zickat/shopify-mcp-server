package com.zickat.shopifymcpserver.metaobjects.domain

import arrow.core.Either
import arrow.core.raise.either
import com.zickat.shopifymcpserver.metaobjects.domain.models.MetaobjectFieldValue
import com.zickat.shopifymcpserver.metaobjects.exposed_interface.model.DeleteMetaobjectResult
import com.zickat.shopifymcpserver.shared_kernel.UseCaseError
import com.zickat.shopifymcpserver.shopify.exposed_interface.ShopifyAdminGateway
import com.zickat.shopifymcpserver.shopify.exposed_interface.ShopifyUserErrors
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import org.springframework.stereotype.Component

@Component
class DeleteMetaobjectUseCase(
    private val shopifyAdminGateway: ShopifyAdminGateway,
) {

    fun execute(storeId: String, metaobjectId: String, confirmReferencedDeletion: Boolean): Either<UseCaseError, DeleteMetaobjectResult> = either {
        val before = fetchExisting(storeId, metaobjectId).bind()
            ?: return@either DeleteMetaobjectResult.notFound(metaobjectId)

        val refs = fetchMetaobjectReferences(shopifyAdminGateway, storeId, metaobjectId).bind()
        val orphan = refs != null && isOrphan(refs)

        if (!orphan && !confirmReferencedDeletion) {
            return@either DeleteMetaobjectResult.refused(
                "delete_metaobject refusé pour $metaobjectId (type: ${before.type}) : ${formatReferenceStatus(refs)}. " +
                    "Appeler à nouveau avec confirm_referenced_deletion: true pour forcer la suppression malgré cette " +
                    "référence (ou cette incertitude de détection).",
            )
        }

        val variables = buildJsonObject { put("id", JsonPrimitive(metaobjectId)) }
        val response = shopifyAdminGateway.executeGraphQL(storeId, DELETE_METAOBJECT_MUTATION, variables).bind()
        val payload = (response as? JsonObject)?.get("metaobjectDelete") as? JsonObject
        val userErrors = ShopifyUserErrors.parse(payload)
        val deletedId = payload?.get("deletedId")?.jsonPrimitive?.contentOrNull

        if (userErrors.isNotEmpty() || deletedId == null) {
            return@either DeleteMetaobjectResult.failed(metaobjectId, ShopifyUserErrors.format(userErrors))
        }

        val fieldsText = formatFields(before.fields)
        val pendingReferenceWarning = if (!orphan) {
            "\n⚠️ Ce metaobject était encore référencé (ou sa détection restait incertaine) au moment de la " +
                "suppression (${formatReferenceStatus(refs)}) — la ou les ressources qui le référençaient ne sont PAS " +
                "nettoyées automatiquement : une référence pendante peut y subsister, à corriger séparément si besoin " +
                "(update_collection_content pour une collection, enrich_product pour un produit)."
        } else {
            ""
        }

        DeleteMetaobjectResult.deleted(
            "Metaobject $metaobjectId (type: ${before.type}) supprimé définitivement. Champs qu'il contenait :\n" +
                "$fieldsText$pendingReferenceWarning",
        )
    }

    private fun fetchExisting(storeId: String, metaobjectId: String): Either<UseCaseError, MetaobjectSnapshot?> = either {
        val variables = buildJsonObject { put("id", JsonPrimitive(metaobjectId)) }
        val response = shopifyAdminGateway.executeGraphQL(storeId, GET_METAOBJECT_BEFORE_DELETE_QUERY, variables).bind()
        val metaobject = (response as? JsonObject)?.get("metaobject") as? JsonObject
        metaobject?.let {
            val type = it["type"]?.jsonPrimitive?.contentOrNull.orEmpty()
            val fields = (it["fields"] as? JsonArray).orEmpty().mapNotNull { f ->
                val fieldObject = f as? JsonObject ?: return@mapNotNull null
                MetaobjectFieldValue(
                    key = fieldObject["key"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                    value = (fieldObject["value"] as? JsonPrimitive)?.contentOrNull,
                )
            }
            MetaobjectSnapshot(type, fields)
        }
    }

    private data class MetaobjectSnapshot(val type: String, val fields: List<MetaobjectFieldValue>)

    companion object {
        private const val GET_METAOBJECT_BEFORE_DELETE_QUERY =
            "query GetMetaobjectBeforeDelete(\$id: ID!) {\n" +
                "          metaobject(id: \$id) { id type fields { key value } }\n" +
                "        }"

        private const val DELETE_METAOBJECT_MUTATION =
            "mutation DeleteMetaobject(\$id: ID!) {\n" +
                "      metaobjectDelete(id: \$id) {\n" +
                "        deletedId\n" +
                "        userErrors { field message }\n" +
                "      }\n" +
                "    }"
    }
}
