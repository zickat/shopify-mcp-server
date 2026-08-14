package com.zickat.shopifymcpserver.metaobjects.spi.shopify

import arrow.core.right
import com.zickat.shopifymcpserver.metaobjects.domain.MetaobjectFieldInput
import com.zickat.shopifymcpserver.shared_kernel.DomainError
import com.zickat.shopifymcpserver.shopify.ShopifyAdminGatewayFake
import io.kotest.assertions.arrow.core.shouldBeLeft
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Test

class MetaobjectsRichTextTest {

    private val json = Json

    @Test
    fun `richTextFromPlainText should convert a single paragraph into a root with one paragraph child made of one text node`() {
        val lexical = MetaobjectsRichText.richTextFromPlainText("Simple paragraph.").shouldBeRight()

        val root = json.parseToJsonElement(lexical).jsonObject
        root["type"]?.jsonPrimitive?.content shouldBe "root"
        val children = root.getValue("children").jsonArray
        children.size shouldBe 1
        val paragraph = children[0].jsonObject
        paragraph["type"]?.jsonPrimitive?.content shouldBe "paragraph"
        val paragraphChildren = paragraph.getValue("children").jsonArray
        paragraphChildren.size shouldBe 1
        paragraphChildren[0].jsonObject["type"]?.jsonPrimitive?.content shouldBe "text"
        paragraphChildren[0].jsonObject["value"]?.jsonPrimitive?.content shouldBe "Simple paragraph."
    }

    @Test
    fun `richTextFromPlainText should convert two blank-line separated blocks into two paragraph children`() {
        val lexical = MetaobjectsRichText.richTextFromPlainText("First paragraph.\n\nSecond paragraph.").shouldBeRight()

        val children = json.parseToJsonElement(lexical).jsonObject.getValue("children").jsonArray
        children.size shouldBe 2
        children.map { it.jsonObject["type"]?.jsonPrimitive?.content } shouldBe listOf("paragraph", "paragraph")
    }

    @Test
    fun `richTextFromPlainText should convert a block whose every line starts with dash-space into an unordered list of list-items`() {
        val lexical = MetaobjectsRichText.richTextFromPlainText("- First item\n- Second item").shouldBeRight()

        val block = json.parseToJsonElement(lexical).jsonObject.getValue("children").jsonArray.single().jsonObject
        block["type"]?.jsonPrimitive?.content shouldBe "list"
        block["listType"]?.jsonPrimitive?.content shouldBe "unordered"
        val items = block.getValue("children").jsonArray
        items.size shouldBe 2
        items.forEach { it.jsonObject["type"]?.jsonPrimitive?.content shouldBe "list-item" }
        val firstItemText = items[0].jsonObject.getValue("children").jsonArray.single().jsonObject
        firstItemText["type"]?.jsonPrimitive?.content shouldBe "text"
        firstItemText["value"]?.jsonPrimitive?.content shouldBe "First item"
    }

    @Test
    fun `richTextFromPlainText should convert a markdown link into a link node carrying the validated href, mixed with surrounding text nodes`() {
        val lexical = MetaobjectsRichText.richTextFromPlainText("See [our guide](https://example.com/guide) for details.").shouldBeRight()

        val paragraphChildren = json.parseToJsonElement(lexical).jsonObject
            .getValue("children").jsonArray.single().jsonObject
            .getValue("children").jsonArray

        paragraphChildren.size shouldBe 3
        paragraphChildren[0].jsonObject["type"]?.jsonPrimitive?.content shouldBe "text"
        paragraphChildren[0].jsonObject["value"]?.jsonPrimitive?.content shouldBe "See "

        val link = paragraphChildren[1].jsonObject
        link["type"]?.jsonPrimitive?.content shouldBe "link"
        link["url"]?.jsonPrimitive?.content shouldBe "https://example.com/guide"
        val linkText = link.getValue("children").jsonArray.single().jsonObject
        linkText["type"]?.jsonPrimitive?.content shouldBe "text"
        linkText["value"]?.jsonPrimitive?.content shouldBe "our guide"

        paragraphChildren[2].jsonObject["type"]?.jsonPrimitive?.content shouldBe "text"
        paragraphChildren[2].jsonObject["value"]?.jsonPrimitive?.content shouldBe " for details."
    }

    @Test
    fun `richTextFromPlainText should reject a non-http(s) link scheme, never building a javascript href`() {
        val error = MetaobjectsRichText.richTextFromPlainText("Click [here](javascript:alert(1)).").shouldBeLeft()

        (error as DomainError).messageKey shouldBe "metaobject.richtext.link.scheme.rejected"
    }

    @Test
    fun `convertRichTextFields should convert only the value of fields declared rich_text_field, leaving other fields untouched`() {
        val fields = listOf(
            MetaobjectFieldInput("title", "Plain title"),
            MetaobjectFieldInput("body", "A single paragraph."),
        )
        val fieldTypes = mapOf("title" to "single_line_text_field", "body" to "rich_text_field")

        val converted = MetaobjectsRichText.convertRichTextFields(fields, fieldTypes).shouldBeRight()

        converted.first { it.key == "title" }.value shouldBe "Plain title"
        val bodyValue = converted.first { it.key == "body" }.value
        json.parseToJsonElement(bodyValue).jsonObject["type"]?.jsonPrimitive?.content shouldBe "root"
    }

    @Test
    fun `convertRichTextFields should pass a field absent from fieldTypes through unchanged`() {
        val fields = listOf(MetaobjectFieldInput("unknown_field", "raw value"))

        val converted = MetaobjectsRichText.convertRichTextFields(fields, emptyMap()).shouldBeRight()

        converted shouldBe fields
    }

    @Test
    fun `fetchMetaobjectDefinitionFieldTypes should return an empty map when the type is not defined server-side`() {
        val gateway = ShopifyAdminGatewayFake().apply {
            enqueue(json.parseToJsonElement("""{"metaobjectDefinitionByType":null}""").right())
        }

        val fieldTypes = MetaobjectsRichText.fetchMetaobjectDefinitionFieldTypes(gateway, "store-1", "unknown_type").shouldBeRight()

        fieldTypes shouldBe emptyMap()
    }

    @Test
    fun `fetchMetaobjectDefinitionFieldTypes should map every declared field key to its Shopify type name`() {
        val gateway = ShopifyAdminGatewayFake().apply {
            enqueue(
                json.parseToJsonElement(
                    """{"metaobjectDefinitionByType":{"fieldDefinitions":[
                        {"key":"title","type":{"name":"single_line_text_field"}},
                        {"key":"body","type":{"name":"rich_text_field"}}
                    ]}}""",
                ).right(),
            )
        }

        val fieldTypes = MetaobjectsRichText.fetchMetaobjectDefinitionFieldTypes(gateway, "store-1", "faq_item").shouldBeRight()

        fieldTypes shouldBe mapOf("title" to "single_line_text_field", "body" to "rich_text_field")
    }
}
