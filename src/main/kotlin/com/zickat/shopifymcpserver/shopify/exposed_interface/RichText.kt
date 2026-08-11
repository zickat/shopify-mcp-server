package com.zickat.shopifymcpserver.shopify.exposed_interface

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import org.springframework.modulith.NamedInterface

@NamedInterface("exposed_interface")
object RichText {

    private val json = Json { ignoreUnknownKeys = true }

    fun toPlainText(value: String?): String {
        if (value.isNullOrEmpty()) return ""
        val root = parseObjectOrNull(value) ?: return ""
        val children = (root["children"] as? JsonArray).orEmpty()
        val blocks = children.mapNotNull { block -> blockToPlainText(block).takeIf { it.trim().isNotEmpty() } }
        return blocks.joinToString("\n\n")
    }

    fun parseStringArray(value: String?): List<String> {
        if (value.isNullOrEmpty()) return emptyList()
        val parsed = try {
            json.parseToJsonElement(value)
        } catch (e: SerializationException) {
            return emptyList()
        }
        val array = parsed as? JsonArray ?: return emptyList()
        return array.mapNotNull { element -> (element as? JsonPrimitive)?.takeIf { it.isString }?.content }
    }

    private fun blockToPlainText(block: JsonElement): String {
        val obj = block as? JsonObject ?: return ""
        val children = obj["children"] as? JsonArray
        return if (obj["type"]?.jsonPrimitive?.contentOrNull == "list" && children != null) {
            children.joinToString("\n") { item -> "- ${nodeToPlainText(item)}" }
        } else {
            nodeToPlainText(block)
        }
    }

    private fun nodeToPlainText(node: JsonElement): String {
        val obj = node as? JsonObject ?: return ""
        if (obj["type"]?.jsonPrimitive?.contentOrNull == "link") {
            val children = obj["children"] as? JsonArray
            val inner = children?.joinToString("") { nodeToPlainText(it) }.orEmpty()
            val url = obj["url"]?.jsonPrimitive?.contentOrNull.orEmpty()
            return "$inner ($url)"
        }
        val value = obj["value"]?.jsonPrimitive?.contentOrNull
        if (value != null) return value
        val children = obj["children"] as? JsonArray
        if (children != null) return children.joinToString("") { nodeToPlainText(it) }
        return ""
    }

    private fun parseObjectOrNull(value: String): JsonObject? =
        try {
            json.parseToJsonElement(value) as? JsonObject
        } catch (e: SerializationException) {
            null
        }
}
