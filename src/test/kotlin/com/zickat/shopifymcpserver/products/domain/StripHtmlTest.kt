package com.zickat.shopifymcpserver.products.domain

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class StripHtmlTest {

    @Test
    fun `should return an empty string for a value with no text`() {
        stripHtml("") shouldBe ""
    }

    @Test
    fun `should remove tags and collapse the resulting whitespace`() {
        stripHtml("<p>Hello <strong>world</strong></p>") shouldBe "Hello world"
    }

    @Test
    fun `should decode the common HTML entities used by Shopify descriptions`() {
        stripHtml("Tom&amp;Jerry&nbsp;&quot;friends&quot;") shouldBe "Tom&Jerry \"friends\""
    }

    @Test
    fun `should trim leading and trailing whitespace produced by stripped block tags`() {
        stripHtml("<div><p>Text</p></div>") shouldBe "Text"
    }
}
