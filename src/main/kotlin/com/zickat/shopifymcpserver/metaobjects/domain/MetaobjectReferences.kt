package com.zickat.shopifymcpserver.metaobjects.domain

import arrow.core.Either
import arrow.core.raise.either
import com.zickat.shopifymcpserver.metaobjects.domain.models.MetaobjectFieldValue
import com.zickat.shopifymcpserver.metaobjects.domain.models.MetaobjectReferenceStatus
import com.zickat.shopifymcpserver.metaobjects.domain.models.MetaobjectReferencer
import com.zickat.shopifymcpserver.shared_kernel.UseCaseError
import com.zickat.shopifymcpserver.shopify.exposed_interface.ShopifyAdminGateway
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

private val FETCH_METAOBJECT_REFERENCES_QUERY =
    "query FetchMetaobjectReferences(\$id: ID!) {\n" +
        "      metaobject(id: \$id) {\n" +
        "        referencedBy(first: 50) {\n" +
        "          edges {\n" +
        "            node {\n" +
        "              key\n" +
        "              namespace\n" +
        "              referencer {\n" +
        "                __typename\n" +
        "                ... on Product { id title }\n" +
        "                ... on Collection { id title }\n" +
        "                ... on Article { id title }\n" +
        "                ... on Metaobject { id type }\n" +
        "              }\n" +
        "            }\n" +
        "          }\n" +
        "          pageInfo { hasNextPage }\n" +
        "        }\n" +
        "      }\n" +
        "    }"

fun fetchMetaobjectReferences(
    shopifyAdminGateway: ShopifyAdminGateway,
    storeId: String,
    metaobjectId: String,
): Either<UseCaseError, MetaobjectReferenceStatus?> = either {
    val variables = buildJsonObject { put("id", JsonPrimitive(metaobjectId)) }
    val response = shopifyAdminGateway.executeGraphQL(storeId, FETCH_METAOBJECT_REFERENCES_QUERY, variables).bind()
    val metaobject = (response as? JsonObject)?.get("metaobject") as? JsonObject

    if (metaobject == null) {
        null
    } else {
        val referencedBy = metaobject["referencedBy"] as? JsonObject
        val edges = (referencedBy?.get("edges") as? JsonArray).orEmpty()
        val truncated = (referencedBy?.get("pageInfo") as? JsonObject)?.get("hasNextPage")?.jsonPrimitive?.boolean ?: false

        val references = edges.mapNotNull { edge ->
            val node = (edge as? JsonObject)?.get("node") as? JsonObject ?: return@mapNotNull null
            val referencer = node["referencer"] as? JsonObject
            MetaobjectReferencer(
                referencerType = referencer?.get("__typename")?.jsonPrimitive?.contentOrNull.orEmpty(),
                referencerId = referencer?.get("id")?.jsonPrimitive?.contentOrNull,
                referencerTitle = referencer?.get("title")?.jsonPrimitive?.contentOrNull
                    ?: referencer?.get("type")?.jsonPrimitive?.contentOrNull
                    ?: "(ressource non résolue)",
                namespace = node["namespace"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                key = node["key"]?.jsonPrimitive?.contentOrNull.orEmpty(),
            )
        }

        when {
            references.isEmpty() && truncated -> MetaobjectReferenceStatus.Uncertain
            references.isEmpty() -> MetaobjectReferenceStatus.Orphan
            else -> MetaobjectReferenceStatus.Referenced(references, truncated)
        }
    }
}

fun isOrphan(status: MetaobjectReferenceStatus): Boolean = status is MetaobjectReferenceStatus.Orphan

fun formatReferenceStatus(status: MetaobjectReferenceStatus?): String = when (status) {
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

fun formatFields(fields: List<MetaobjectFieldValue>): String =
    if (fields.isEmpty()) {
        "  (aucun champ)"
    } else {
        fields.joinToString("\n") { "  - ${it.key}: ${it.value ?: "(vide)"}" }
    }
