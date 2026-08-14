package com.zickat.shopifymcpserver.metaobjects.domain

import arrow.core.Either
import arrow.core.raise.either
import com.zickat.shopifymcpserver.metaobjects.domain.models.MetaobjectFieldValue
import com.zickat.shopifymcpserver.metaobjects.exposed_interface.model.MetaobjectFieldInput
import com.zickat.shopifymcpserver.metaobjects.exposed_interface.model.UpdateMetaobjectResult
import com.zickat.shopifymcpserver.shared_kernel.UseCaseError
import com.zickat.shopifymcpserver.shopify.exposed_interface.ShopifyAdminGateway
import com.zickat.shopifymcpserver.shopify.exposed_interface.ShopifyUserErrors
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import org.springframework.stereotype.Component

@Component
class UpdateMetaobjectUseCase(
    private val shopifyAdminGateway: ShopifyAdminGateway,
) {

    fun execute(storeId: String, metaobjectId: String, fields: List<MetaobjectFieldInput>): Either<UseCaseError, UpdateMetaobjectResult> = either {
        val before = fetchExistingType(storeId, metaobjectId).bind()
            ?: return@either UpdateMetaobjectResult.notFound(metaobjectId)

        val fieldTypes = fetchMetaobjectDefinitionFieldTypes(shopifyAdminGateway, storeId, before).bind()
        val convertedFields = convertRichTextFields(fields, fieldTypes).bind()

        val variables = buildJsonObject {
            put("id", JsonPrimitive(metaobjectId))
            put("metaobject", buildJsonObject { put("fields", fieldsJson(convertedFields)) })
        }
        val response = shopifyAdminGateway.executeGraphQL(storeId, UPDATE_METAOBJECT_MUTATION, variables).bind()
        val payload = (response as? JsonObject)?.get("metaobjectUpdate") as? JsonObject
        val userErrors = ShopifyUserErrors.parse(payload)
        val updatedId = (payload?.get("metaobject") as? JsonObject)?.get("id")?.jsonPrimitive?.contentOrNull

        if (userErrors.isNotEmpty() || updatedId == null) {
            UpdateMetaobjectResult.failed(metaobjectId, ShopifyUserErrors.format(userErrors))
        } else {
            val fieldsText = formatFields(fields.map { MetaobjectFieldValue(it.key, it.value) })
            UpdateMetaobjectResult.updated("Metaobject $metaobjectId (type: $before) mis à jour.\n$fieldsText")
        }
    }

    private fun fetchExistingType(storeId: String, metaobjectId: String): Either<UseCaseError, String?> = either {
        val variables = buildJsonObject { put("id", JsonPrimitive(metaobjectId)) }
        val response = shopifyAdminGateway.executeGraphQL(storeId, GET_METAOBJECT_BEFORE_UPDATE_QUERY, variables).bind()
        val metaobject = (response as? JsonObject)?.get("metaobject") as? JsonObject
        metaobject?.get("type")?.jsonPrimitive?.contentOrNull
    }

    companion object {
        private const val GET_METAOBJECT_BEFORE_UPDATE_QUERY =
            "query GetMetaobjectBeforeUpdate(\$id: ID!) {\n" +
                "          metaobject(id: \$id) { id type fields { key value } }\n" +
                "        }"

        private const val UPDATE_METAOBJECT_MUTATION =
            "mutation UpdateMetaobject(\$id: ID!, \$metaobject: MetaobjectUpdateInput!) {\n" +
                "      metaobjectUpdate(id: \$id, metaobject: \$metaobject) {\n" +
                "        metaobject { id }\n" +
                "        userErrors { field message }\n" +
                "      }\n" +
                "    }"
    }
}
