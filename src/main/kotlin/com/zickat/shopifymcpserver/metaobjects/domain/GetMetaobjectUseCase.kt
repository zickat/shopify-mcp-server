package com.zickat.shopifymcpserver.metaobjects.domain

import arrow.core.Either
import arrow.core.raise.either
import com.zickat.shopifymcpserver.metaobjects.domain.models.MetaobjectFieldValue
import com.zickat.shopifymcpserver.metaobjects.exposed_interface.model.GetMetaobjectResult
import com.zickat.shopifymcpserver.shared_kernel.UseCaseError
import com.zickat.shopifymcpserver.shopify.exposed_interface.ShopifyAdminGateway
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import org.springframework.stereotype.Component

private val GET_METAOBJECT_QUERY =
    "query GetMetaobject(\$id: ID!) {\n" +
        "          metaobject(id: \$id) { id type fields { key value } }\n" +
        "        }"

@Component
class GetMetaobjectUseCase(
    private val shopifyAdminGateway: ShopifyAdminGateway,
) {

    fun execute(storeId: String, metaobjectId: String): Either<UseCaseError, GetMetaobjectResult> = either {
        val variables = buildJsonObject { put("id", JsonPrimitive(metaobjectId)) }
        val response = shopifyAdminGateway.executeGraphQL(storeId, GET_METAOBJECT_QUERY, variables).bind()
        val metaobject = (response as? JsonObject)?.get("metaobject") as? JsonObject

        if (metaobject == null) {
            GetMetaobjectResult.notFound(metaobjectId)
        } else {
            val id = metaobject["id"]?.jsonPrimitive?.contentOrNull.orEmpty()
            val type = metaobject["type"]?.jsonPrimitive?.contentOrNull.orEmpty()
            val fields = (metaobject["fields"] as? JsonArray).orEmpty().mapNotNull { f ->
                val fieldObject = f as? JsonObject ?: return@mapNotNull null
                MetaobjectFieldValue(
                    key = fieldObject["key"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                    value = (fieldObject["value"] as? JsonPrimitive)?.contentOrNull,
                )
            }
            val refs = fetchMetaobjectReferences(shopifyAdminGateway, storeId, id).bind()
            GetMetaobjectResult.found("$id — type: $type — ${formatReferenceStatus(refs)}\n${formatFields(fields)}")
        }
    }
}
