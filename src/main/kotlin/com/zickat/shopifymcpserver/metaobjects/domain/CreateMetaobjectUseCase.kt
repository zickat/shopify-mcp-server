package com.zickat.shopifymcpserver.metaobjects.domain

import arrow.core.Either
import arrow.core.raise.either
import com.zickat.shopifymcpserver.metaobjects.domain.models.MetaobjectFieldValue
import com.zickat.shopifymcpserver.metaobjects.exposed_interface.model.CreateMetaobjectResult
import com.zickat.shopifymcpserver.metaobjects.exposed_interface.model.MetaobjectFieldInput
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
class CreateMetaobjectUseCase(
    private val shopifyAdminGateway: ShopifyAdminGateway,
) {

    fun execute(storeId: String, type: String, fields: List<MetaobjectFieldInput>): Either<UseCaseError, CreateMetaobjectResult> = either {
        val fieldTypes = fetchMetaobjectDefinitionFieldTypes(shopifyAdminGateway, storeId, type).bind()
        val convertedFields = convertRichTextFields(fields, fieldTypes).bind()

        val variables = buildJsonObject {
            put(
                "metaobject",
                buildJsonObject {
                    put("type", JsonPrimitive(type))
                    put("fields", fieldsJson(convertedFields))
                },
            )
        }
        val response = shopifyAdminGateway.executeGraphQL(storeId, CREATE_METAOBJECT_MUTATION, variables).bind()
        val payload = (response as? JsonObject)?.get("metaobjectCreate") as? JsonObject
        val userErrors = ShopifyUserErrors.parse(payload)
        val createdId = (payload?.get("metaobject") as? JsonObject)?.get("id")?.jsonPrimitive?.contentOrNull

        if (userErrors.isNotEmpty() || createdId == null) {
            CreateMetaobjectResult.failed(type, ShopifyUserErrors.format(userErrors))
        } else {
            val fieldsText = formatFields(fields.map { MetaobjectFieldValue(it.key, it.value) })
            CreateMetaobjectResult.created("Metaobject $createdId (type: $type) créé.\n$fieldsText")
        }
    }

    companion object {
        private const val CREATE_METAOBJECT_MUTATION =
            "mutation CreateMetaobject(\$metaobject: MetaobjectCreateInput!) {\n" +
                "      metaobjectCreate(metaobject: \$metaobject) {\n" +
                "        metaobject { id }\n" +
                "        userErrors { field message }\n" +
                "      }\n" +
                "    }"
    }
}
