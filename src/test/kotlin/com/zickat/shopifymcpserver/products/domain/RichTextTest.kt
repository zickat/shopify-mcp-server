package com.zickat.shopifymcpserver.products.domain

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class RichTextTest {

    @Test
    fun `toPlainText should return empty for a null value`() {
        RichText.toPlainText(null) shouldBe ""
    }

    @Test
    fun `toPlainText should return empty for a value that is not valid JSON`() {
        RichText.toPlainText("not json at all") shouldBe ""
    }

    @Test
    fun `toPlainText should join paragraph text nodes`() {
        val doc = """{"type":"root","children":[{"type":"paragraph","children":[{"type":"text","value":"Hello world"}]}]}"""

        RichText.toPlainText(doc) shouldBe "Hello world"
    }

    @Test
    fun `toPlainText should render a list block as dash-prefixed lines`() {
        val doc = """
            {"type":"root","children":[{"type":"list","listType":"unordered","children":[
                {"type":"list-item","children":[{"type":"text","value":"Item 1"}]},
                {"type":"list-item","children":[{"type":"text","value":"Item 2"}]}
            ]}]}
        """.trimIndent()

        RichText.toPlainText(doc) shouldBe "- Item 1\n- Item 2"
    }

    @Test
    fun `toPlainText should render a link node as the inner text followed by the URL in parentheses`() {
        val doc = """
            {"type":"root","children":[{"type":"paragraph","children":[
                {"type":"text","value":"See "},
                {"type":"link","url":"https://example.com","children":[{"type":"text","value":"here"}]}
            ]}]}
        """.trimIndent()

        RichText.toPlainText(doc) shouldBe "See here (https://example.com)"
    }

    @Test
    fun `toPlainText should separate multiple non-empty blocks with a blank line`() {
        val doc = """
            {"type":"root","children":[
                {"type":"paragraph","children":[{"type":"text","value":"First"}]},
                {"type":"paragraph","children":[{"type":"text","value":"Second"}]}
            ]}
        """.trimIndent()

        RichText.toPlainText(doc) shouldBe "First\n\nSecond"
    }

    @Test
    fun `parseStringArray should return an empty list for a null value`() {
        RichText.parseStringArray(null) shouldBe emptyList()
    }

    @Test
    fun `parseStringArray should return an empty list for a value that is not valid JSON`() {
        RichText.parseStringArray("not json at all") shouldBe emptyList()
    }

    @Test
    fun `parseStringArray should decode a JSON array of strings`() {
        RichText.parseStringArray("""["a","b","c"]""") shouldBe listOf("a", "b", "c")
    }

    @Test
    fun `parseStringArray should drop non-string entries instead of failing`() {
        RichText.parseStringArray("""["a", 1, null, "b"]""") shouldBe listOf("a", "b")
    }
}
