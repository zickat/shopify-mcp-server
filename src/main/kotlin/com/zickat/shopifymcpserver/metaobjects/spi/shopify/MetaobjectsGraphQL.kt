package com.zickat.shopifymcpserver.metaobjects.spi.shopify

import com.zickat.shopifymcpserver.metaobjects.domain.MetaobjectFieldInput
import com.zickat.shopifymcpserver.metaobjects.domain.models.MetaobjectFieldValue
import com.zickat.shopifymcpserver.metaobjects.domain.models.MetaobjectReferenceStatus
import com.zickat.shopifymcpserver.metaobjects.domain.models.MetaobjectReferencer
import com.zickat.shopifymcpserver.metaobjects.domain.repositories.MetaobjectDefinitionSummary
import com.zickat.shopifymcpserver.metaobjects.domain.repositories.MetaobjectInstance
import com.zickat.shopifymcpserver.metaobjects.domain.repositories.MetaobjectSnapshot
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

internal object MetaobjectsGraphQL {

    val LIST_METAOBJECT_DEFINITIONS_QUERY =
        "query ListMetaobjectDefinitions {\n" +
            "            metaobjectDefinitions(first: 50) {\n" +
            "              pageInfo { hasNextPage }\n" +
            "              edges { node { type name metaobjectsCount } }\n" +
            "            }\n" +
            "          }"

    val LIST_METAOBJECTS_BY_TYPE_QUERY =
        "query ListMetaobjectsByType(\$type: String!, \$cursor: String) {\n" +
            "            metaobjects(type: \$type, first: 50, after: \$cursor) {\n" +
            "              pageInfo { hasNextPage endCursor }\n" +
            "              edges { node { id fields { key value } } }\n" +
            "            }\n" +
            "          }"

    val GET_METAOBJECT_QUERY =
        "query GetMetaobject(\$id: ID!) {\n" +
            "          metaobject(id: \$id) { id type fields { key value } }\n" +
            "        }"

    val GET_METAOBJECT_BEFORE_UPDATE_QUERY =
        "query GetMetaobjectBeforeUpdate(\$id: ID!) {\n" +
            "          metaobject(id: \$id) { id type fields { key value } }\n" +
            "        }"

    val GET_METAOBJECT_BEFORE_DELETE_QUERY =
        "query GetMetaobjectBeforeDelete(\$id: ID!) {\n" +
            "          metaobject(id: \$id) { id type fields { key value } }\n" +
            "        }"

    val CREATE_METAOBJECT_MUTATION =
        "mutation CreateMetaobject(\$metaobject: MetaobjectCreateInput!) {\n" +
            "      metaobjectCreate(metaobject: \$metaobject) {\n" +
            "        metaobject { id }\n" +
            "        userErrors { field message }\n" +
            "      }\n" +
            "    }"

    val UPDATE_METAOBJECT_MUTATION =
        "mutation UpdateMetaobject(\$id: ID!, \$metaobject: MetaobjectUpdateInput!) {\n" +
            "      metaobjectUpdate(id: \$id, metaobject: \$metaobject) {\n" +
            "        metaobject { id }\n" +
            "        userErrors { field message }\n" +
            "      }\n" +
            "    }"

    val DELETE_METAOBJECT_MUTATION =
        "mutation DeleteMetaobject(\$id: ID!) {\n" +
            "      metaobjectDelete(id: \$id) {\n" +
            "        deletedId\n" +
            "        userErrors { field message }\n" +
            "      }\n" +
            "    }"

    val FETCH_METAOBJECT_REFERENCES_QUERY =
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

    fun idVariables(id: String): JsonObject = buildJsonObject { put("id", JsonPrimitive(id)) }

    fun listInstancesVariables(type: String, cursor: String?): JsonObject = buildJsonObject {
        put("type", JsonPrimitive(type))
        put("cursor", cursor?.let { JsonPrimitive(it) } ?: JsonNull)
    }

    fun createVariables(type: String, fields: List<MetaobjectFieldInput>): JsonObject = buildJsonObject {
        put(
            "metaobject",
            buildJsonObject {
                put("type", JsonPrimitive(type))
                put("fields", MetaobjectsRichText.fieldsJson(fields))
            },
        )
    }

    fun updateVariables(metaobjectId: String, fields: List<MetaobjectFieldInput>): JsonObject = buildJsonObject {
        put("id", JsonPrimitive(metaobjectId))
        put("metaobject", buildJsonObject { put("fields", MetaobjectsRichText.fieldsJson(fields)) })
    }

    fun definitionsConnection(response: JsonElement): JsonObject? = (response as? JsonObject)?.get("metaobjectDefinitions") as? JsonObject

    fun instancesConnection(response: JsonElement): JsonObject? = (response as? JsonObject)?.get("metaobjects") as? JsonObject

    fun definitionSummaries(connection: JsonObject): List<MetaobjectDefinitionSummary> =
        (connection["edges"] as? JsonArray).orEmpty().mapNotNull { edge ->
            val node = (edge as? JsonObject)?.get("node") as? JsonObject ?: return@mapNotNull null
            MetaobjectDefinitionSummary(
                type = node["type"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                name = node["name"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                instanceCount = node["metaobjectsCount"]?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: 0,
            )
        }

    fun instances(connection: JsonObject): List<MetaobjectInstance> =
        (connection["edges"] as? JsonArray).orEmpty().mapNotNull { edge ->
            val node = (edge as? JsonObject)?.get("node") as? JsonObject ?: return@mapNotNull null
            MetaobjectInstance(
                id = node["id"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                fields = fieldValues(node["fields"] as? JsonArray),
            )
        }

    fun hasNextPage(connection: JsonObject): Boolean =
        (connection["pageInfo"] as? JsonObject)?.get("hasNextPage")?.jsonPrimitive?.boolean ?: false

    fun endCursor(connection: JsonObject): String? =
        (connection["pageInfo"] as? JsonObject)?.get("endCursor")?.jsonPrimitive?.contentOrNull

    fun parseSnapshot(response: JsonElement): MetaobjectSnapshot? {
        val metaobject = (response as? JsonObject)?.get("metaobject") as? JsonObject ?: return null
        return MetaobjectSnapshot(
            id = metaobject["id"]?.jsonPrimitive?.contentOrNull.orEmpty(),
            type = metaobject["type"]?.jsonPrimitive?.contentOrNull.orEmpty(),
            fields = fieldValues(metaobject["fields"] as? JsonArray),
        )
    }

    fun mutationPayload(response: JsonElement, mutationField: String): JsonObject? = (response as? JsonObject)?.get(mutationField) as? JsonObject

    fun mutatedMetaobjectId(payload: JsonObject?): String? = (payload?.get("metaobject") as? JsonObject)?.get("id")?.jsonPrimitive?.contentOrNull

    fun deletedId(payload: JsonObject?): String? = payload?.get("deletedId")?.jsonPrimitive?.contentOrNull

    fun parseReferenceStatus(response: JsonElement): MetaobjectReferenceStatus? {
        val metaobject = (response as? JsonObject)?.get("metaobject") as? JsonObject ?: return null
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

        return when {
            references.isEmpty() && truncated -> MetaobjectReferenceStatus.Uncertain
            references.isEmpty() -> MetaobjectReferenceStatus.Orphan
            else -> MetaobjectReferenceStatus.Referenced(references, truncated)
        }
    }

    private fun fieldValues(fields: JsonArray?): List<MetaobjectFieldValue> =
        fields.orEmpty().mapNotNull { f ->
            val fieldObject = f as? JsonObject ?: return@mapNotNull null
            MetaobjectFieldValue(
                key = fieldObject["key"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                value = (fieldObject["value"] as? JsonPrimitive)?.contentOrNull,
            )
        }
}
