package com.zickat.shopifymcpserver.metaobjects.domain

import arrow.core.Either
import arrow.core.raise.either
import com.zickat.shopifymcpserver.metaobjects.exposed_interface.model.MetaobjectFieldInput
import com.zickat.shopifymcpserver.shared_kernel.DomainError
import com.zickat.shopifymcpserver.shared_kernel.UseCaseError
import com.zickat.shopifymcpserver.shopify.exposed_interface.ShopifyAdminGateway
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import java.net.URI

private const val RICH_TEXT_FIELD_TYPE = "rich_text_field"

private val METAOBJECT_DEFINITION_FIELD_TYPES_QUERY =
    "query MetaobjectDefinitionFieldTypes(\$type: String!) {\n" +
        "      metaobjectDefinitionByType(type: \$type) {\n" +
        "        fieldDefinitions { key type { name } }\n" +
        "      }\n" +
        "    }"

/**
 * Reads the declared Shopify type (e.g. `rich_text_field`, `single_line_text_field`) of every
 * field defined for a metaobject `type` — read-only, no mutation. A `type` absent server-side
 * (typo, legacy type never defined) yields an empty map, not an error: the mutation that follows
 * surfaces the real Shopify userError.
 */
fun fetchMetaobjectDefinitionFieldTypes(
    shopifyAdminGateway: ShopifyAdminGateway,
    storeId: String,
    type: String,
): Either<UseCaseError, Map<String, String>> = either {
    val variables = buildJsonObject { put("type", JsonPrimitive(type)) }
    val response = shopifyAdminGateway.executeGraphQL(storeId, METAOBJECT_DEFINITION_FIELD_TYPES_QUERY, variables).bind()
    val fieldDefinitions = ((response as? JsonObject)?.get("metaobjectDefinitionByType") as? JsonObject)?.get("fieldDefinitions") as? JsonArray

    fieldDefinitions.orEmpty().mapNotNull { entry ->
        val obj = entry as? JsonObject ?: return@mapNotNull null
        val key = obj["key"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
        val typeName = (obj["type"] as? JsonObject)?.get("name")?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
        key to typeName
    }.toMap()
}

/**
 * Converts, among `fields`, the `value` of every field declared `rich_text_field` (via
 * `fieldTypes`, cf. [fetchMetaobjectDefinitionFieldTypes]) into the Lexical JSON Shopify expects —
 * ported from `convertRichTextFields`/`richTextFromPlainText` (`mcp-shopify-catalog/src/tools/
 * shared.ts`). A field absent from `fieldTypes` (unknown type or unknown field) passes through
 * unchanged — it is not this function's place to judge, the Shopify mutation that follows surfaces
 * the real error.
 */
fun convertRichTextFields(
    fields: List<MetaobjectFieldInput>,
    fieldTypes: Map<String, String>,
): Either<UseCaseError, List<MetaobjectFieldInput>> = either {
    fields.map { field ->
        if (fieldTypes[field.key] == RICH_TEXT_FIELD_TYPE) {
            field.copy(value = richTextFromPlainText(field.value).bind())
        } else {
            field
        }
    }
}

fun fieldsJson(fields: List<MetaobjectFieldInput>): JsonArray = buildJsonArray {
    fields.forEach { field ->
        add(
            buildJsonObject {
                put("key", JsonPrimitive(field.key))
                put("value", JsonPrimitive(field.value))
            },
        )
    }
}

private val LINK_MARKDOWN_PATTERN = Regex("""\[([^]]+)]\(([^)\s]+)\)""")

/**
 * Builds the Lexical JSON value of a Shopify `rich_text_field` metafield from plain editorial
 * text — blocks are separated by a blank line; a block whose every non-blank line starts with
 * "- " becomes an unordered list, otherwise a paragraph. Each line/item may contain one or more
 * `[text](url)`, converted to `link` nodes mixed with `text` nodes — never a single child per
 * block. Ported from `richTextFromPlainText` (`shared.ts`).
 */
fun richTextFromPlainText(text: String): Either<UseCaseError, String> = either {
    val blocks = text.split(Regex("""\n\s*\n"""))
        .map { it.trim() }
        .filter { it.isNotEmpty() }

    val blockNodes = blocks.map { block -> blockToNode(block).bind() }

    buildJsonObject {
        put("type", JsonPrimitive("root"))
        put("children", buildJsonArray { blockNodes.forEach { add(it) } })
    }.toString()
}

private fun blockToNode(block: String): Either<UseCaseError, JsonObject> = either {
    val lines = block.split("\n").map { it.trim() }.filter { it.isNotEmpty() }
    val isList = lines.isNotEmpty() && lines.all { it.startsWith("- ") }

    if (isList) {
        val items = lines.map { line ->
            buildJsonObject {
                put("type", JsonPrimitive("list-item"))
                put("children", buildJsonArray { textLineToChildren(line.removePrefix("- ").trim()).bind().forEach { add(it) } })
            }
        }
        buildJsonObject {
            put("type", JsonPrimitive("list"))
            put("listType", JsonPrimitive("unordered"))
            put("children", buildJsonArray { items.forEach { add(it) } })
        }
    } else {
        buildJsonObject {
            put("type", JsonPrimitive("paragraph"))
            put("children", buildJsonArray { textLineToChildren(lines.joinToString(" ")).bind().forEach { add(it) } })
        }
    }
}

private fun textLineToChildren(line: String): Either<UseCaseError, List<JsonObject>> = either {
    val children = mutableListOf<JsonObject>()
    var lastIndex = 0
    LINK_MARKDOWN_PATTERN.findAll(line).forEach { match ->
        val label = match.groupValues[1]
        val url = match.groupValues[2]
        val start = match.range.first
        if (start > lastIndex) {
            children += textNode(line.substring(lastIndex, start))
        }
        val validatedUrl = validateHref(url).bind()
        children += buildJsonObject {
            put("type", JsonPrimitive("link"))
            put("url", JsonPrimitive(validatedUrl))
            put("title", JsonNull)
            put("target", JsonNull)
            put("children", buildJsonArray { add(textNode(label)) })
        }
        lastIndex = match.range.last + 1
    }
    if (lastIndex < line.length || children.isEmpty()) {
        children += textNode(line.substring(lastIndex))
    }
    children
}

private fun textNode(value: String): JsonObject = buildJsonObject {
    put("type", JsonPrimitive("text"))
    put("value", JsonPrimitive(value))
}

/**
 * Validates a URL destined to become a clickable `link` node — same defense-in-depth as
 * `validateHref` (`shared.ts`): rejects anything that is not a well-formed absolute URL, any
 * scheme other than http(s) (blocks `javascript:`, `data:`, `file:`...), and any URL without a
 * resolved hostname.
 */
private fun validateHref(href: String): Either<UseCaseError, String> {
    val uri = runCatching { URI(href) }.getOrNull()
    return when {
        uri == null || !uri.isAbsolute ->
            Either.Left(DomainError("metaobject.richtext.link.invalid", mapOf("href" to href)))
        uri.scheme != "http" && uri.scheme != "https" ->
            Either.Left(DomainError("metaobject.richtext.link.scheme.rejected", mapOf("href" to href, "scheme" to uri.scheme.orEmpty())))
        uri.host.isNullOrBlank() ->
            Either.Left(DomainError("metaobject.richtext.link.host.missing", mapOf("href" to href)))
        else -> Either.Right(uri.toString())
    }
}
