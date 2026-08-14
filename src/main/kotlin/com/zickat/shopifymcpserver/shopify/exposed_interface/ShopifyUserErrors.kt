package com.zickat.shopifymcpserver.shopify.exposed_interface

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import org.springframework.modulith.NamedInterface

@NamedInterface("exposed_interface")
data class ShopifyUserError(val field: List<String>, val message: String)

@NamedInterface("exposed_interface")
object ShopifyUserErrors {

    fun parse(payload: JsonObject?): List<ShopifyUserError> =
        (payload?.get("userErrors") as? JsonArray)?.map { it.toShopifyUserError() }.orEmpty()

    fun format(errors: List<ShopifyUserError>): String =
        errors.joinToString(" ; ") { error ->
            if (error.field.isNotEmpty()) "${error.field.joinToString(".")} : ${error.message}" else error.message
        }

    private fun JsonElement.toShopifyUserError(): ShopifyUserError {
        val obj = this as? JsonObject
        val field = (obj?.get("field") as? JsonArray)?.mapNotNull { it.jsonPrimitive.contentOrNull }.orEmpty()
        val message = obj?.get("message")?.jsonPrimitive?.contentOrNull.orEmpty()
        return ShopifyUserError(field, message)
    }
}
