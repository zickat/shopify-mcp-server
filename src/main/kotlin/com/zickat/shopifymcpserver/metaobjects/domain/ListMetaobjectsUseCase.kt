package com.zickat.shopifymcpserver.metaobjects.domain

import arrow.core.Either
import arrow.core.raise.either
import com.zickat.shopifymcpserver.metaobjects.domain.models.MetaobjectFieldValue
import com.zickat.shopifymcpserver.metaobjects.exposed_interface.model.ListMetaobjectsResult
import com.zickat.shopifymcpserver.shared_kernel.MAX_SEARCH_PAGES
import com.zickat.shopifymcpserver.shared_kernel.TechnicalError
import com.zickat.shopifymcpserver.shared_kernel.UseCaseError
import com.zickat.shopifymcpserver.shopify.exposed_interface.ShopifyAdminGateway
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import org.springframework.stereotype.Component

private val LIST_METAOBJECT_DEFINITIONS_QUERY =
    "query ListMetaobjectDefinitions {\n" +
        "            metaobjectDefinitions(first: 50) {\n" +
        "              pageInfo { hasNextPage }\n" +
        "              edges { node { type name metaobjectsCount } }\n" +
        "            }\n" +
        "          }"

private val LIST_METAOBJECTS_BY_TYPE_QUERY =
    "query ListMetaobjectsByType(\$type: String!, \$cursor: String) {\n" +
        "            metaobjects(type: \$type, first: 50, after: \$cursor) {\n" +
        "              pageInfo { hasNextPage endCursor }\n" +
        "              edges { node { id fields { key value } } }\n" +
        "            }\n" +
        "          }"

private data class MetaobjectDefinitionSummary(val type: String, val name: String, val instanceCount: Int)

private data class MetaobjectInstance(val id: String, val fields: List<MetaobjectFieldValue>)

@Component
class ListMetaobjectsUseCase(
    private val shopifyAdminGateway: ShopifyAdminGateway,
) {

    fun execute(storeId: String, type: String?): Either<UseCaseError, ListMetaobjectsResult> =
        if (type == null) executeDefinitions(storeId) else executeInstances(storeId, type)

    private fun executeDefinitions(storeId: String): Either<UseCaseError, ListMetaobjectsResult> = either {
        val response = shopifyAdminGateway.executeGraphQL(storeId, LIST_METAOBJECT_DEFINITIONS_QUERY, JsonObject(emptyMap())).bind()
        val connection = (response as? JsonObject)?.get("metaobjectDefinitions") as? JsonObject
            ?: raise(TechnicalError("shopify.graphql.response.malformed"))

        val defs = (connection["edges"] as? JsonArray).orEmpty().mapNotNull { edge ->
            val node = (edge as? JsonObject)?.get("node") as? JsonObject ?: return@mapNotNull null
            MetaobjectDefinitionSummary(
                type = node["type"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                name = node["name"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                instanceCount = node["metaobjectsCount"]?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: 0,
            )
        }
        val hasNextPage = (connection["pageInfo"] as? JsonObject)?.get("hasNextPage")?.jsonPrimitive?.boolean ?: false

        val text = if (defs.isEmpty()) {
            "Aucune définition de metaobject trouvée sur le store."
        } else {
            val lines = defs.joinToString("\n") { d -> "- ${d.type} (${d.name}) : ${d.instanceCount} instance(s)" }
            val truncationNote = if (hasNextPage) {
                "\n\n⚠️ Balayage des définitions tronqué à 50 — d'autres types peuvent exister au-delà de cette borne."
            } else {
                ""
            }
            "${defs.size} type(s) de metaobject trouvé(s) sur le store :\n$lines\n\n" +
                "Appeler list_metaobjects(type: \"...\") pour lister les instances d'un type.$truncationNote"
        }
        ListMetaobjectsResult(text)
    }

    private fun executeInstances(storeId: String, type: String): Either<UseCaseError, ListMetaobjectsResult> = either {
        val instances = mutableListOf<MetaobjectInstance>()
        var cursor: String? = null
        var page = 0
        var truncated = false

        do {
            val variables = buildJsonObject {
                put("type", JsonPrimitive(type))
                put("cursor", cursor?.let { JsonPrimitive(it) } ?: JsonNull)
            }
            val response = shopifyAdminGateway.executeGraphQL(storeId, LIST_METAOBJECTS_BY_TYPE_QUERY, variables).bind()
            val connection = (response as? JsonObject)?.get("metaobjects") as? JsonObject
                ?: raise(TechnicalError("shopify.graphql.response.malformed"))

            (connection["edges"] as? JsonArray).orEmpty().forEach { edge ->
                val node = (edge as? JsonObject)?.get("node") as? JsonObject
                if (node != null) {
                    instances += MetaobjectInstance(
                        id = node["id"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                        fields = (node["fields"] as? JsonArray).orEmpty().mapNotNull { f ->
                            val fieldObject = f as? JsonObject ?: return@mapNotNull null
                            MetaobjectFieldValue(
                                key = fieldObject["key"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                                value = (fieldObject["value"] as? JsonPrimitive)?.contentOrNull,
                            )
                        },
                    )
                }
            }

            val pageInfo = connection["pageInfo"] as? JsonObject
            val hasNextPage = pageInfo?.get("hasNextPage")?.jsonPrimitive?.boolean ?: false
            cursor = if (hasNextPage) pageInfo?.get("endCursor")?.jsonPrimitive?.contentOrNull else null
            page += 1
            if (cursor != null && page >= MAX_SEARCH_PAGES) {
                truncated = true
                cursor = null
            }
        } while (cursor != null)

        val text = if (instances.isEmpty()) {
            "Aucun metaobject de type \"$type\" trouvé."
        } else {
            val blocks = mutableListOf<String>()
            for (instance in instances) {
                val refs = fetchMetaobjectReferences(shopifyAdminGateway, storeId, instance.id).bind()
                blocks += "${instance.id} — ${formatReferenceStatus(refs)}\n${formatFields(instance.fields)}"
            }
            val truncationNote = if (truncated) {
                "\n\n⚠️ Balayage tronqué à ${MAX_SEARCH_PAGES * 50} instances — d'autres instances de ce type " +
                    "peuvent exister au-delà de cette borne."
            } else {
                ""
            }
            "${instances.size} instance(s) de type \"$type\" :\n\n${blocks.joinToString("\n\n")}$truncationNote"
        }
        ListMetaobjectsResult(text)
    }
}
