package com.zickat.shopifymcpserver.shopify.exposed_interface

import arrow.core.Either
import arrow.core.raise.either
import com.zickat.shopifymcpserver.shared_kernel.UseCaseError
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import org.springframework.modulith.NamedInterface

@NamedInterface("exposed_interface")
data class ShopifyMetafieldWrite(
    val ownerId: String,
    val namespace: String,
    val key: String,
    val type: String,
    val value: String,
)

@NamedInterface("exposed_interface")
object ShopifyMetafields {

    fun set(gateway: ShopifyAdminGateway, storeId: String, metafields: List<ShopifyMetafieldWrite>): Either<UseCaseError, List<ShopifyUserError>> = either {
        val variables = buildJsonObject {
            put(
                "metafields",
                buildJsonArray {
                    metafields.forEach { metafield ->
                        add(
                            buildJsonObject {
                                put("ownerId", JsonPrimitive(metafield.ownerId))
                                put("namespace", JsonPrimitive(metafield.namespace))
                                put("key", JsonPrimitive(metafield.key))
                                put("type", JsonPrimitive(metafield.type))
                                put("value", JsonPrimitive(metafield.value))
                            },
                        )
                    }
                },
            )
        }
        val response = gateway.executeGraphQL(storeId, SET_METAFIELDS_MUTATION, variables).bind()
        ShopifyUserErrors.parse((response as? JsonObject)?.get("metafieldsSet") as? JsonObject)
    }

    fun delete(gateway: ShopifyAdminGateway, storeId: String, ownerId: String, namespace: String, key: String): Either<UseCaseError, List<ShopifyUserError>> = either {
        val variables = buildJsonObject {
            put(
                "metafields",
                buildJsonArray {
                    add(
                        buildJsonObject {
                            put("ownerId", JsonPrimitive(ownerId))
                            put("namespace", JsonPrimitive(namespace))
                            put("key", JsonPrimitive(key))
                        },
                    )
                },
            )
        }
        val response = gateway.executeGraphQL(storeId, DELETE_METAFIELDS_MUTATION, variables).bind()
        ShopifyUserErrors.parse((response as? JsonObject)?.get("metafieldsDelete") as? JsonObject)
    }

    private const val SET_METAFIELDS_MUTATION =
        "mutation SetMetafields(\$metafields: [MetafieldsSetInput!]!) {\n" +
            "      metafieldsSet(metafields: \$metafields) {\n" +
            "        metafields { id key }\n" +
            "        userErrors { field message }\n" +
            "      }\n" +
            "    }"

    private const val DELETE_METAFIELDS_MUTATION =
        "mutation DeleteMetafields(\$metafields: [MetafieldIdentifierInput!]!) {\n" +
            "      metafieldsDelete(metafields: \$metafields) {\n" +
            "        deletedMetafields { key }\n" +
            "        userErrors { field message }\n" +
            "      }\n" +
            "    }"
}
