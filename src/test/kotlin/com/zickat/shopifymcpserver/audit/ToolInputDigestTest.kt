package com.zickat.shopifymcpserver.audit

import com.zickat.shopifymcpserver.audit.domain.ToolInputDigest
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class ToolInputDigestTest {

    private val editorialText =
        "Découvrez notre nouvelle collection été 2026 — édition limitée, -30% ce week-end seulement"
    private val signedUrl =
        "https://cdn.shopify.com/s/files/1/0042/product-hero.jpg?sig=AbCdEf0123456789&expires=1999999999"

    @Test
    fun `digest of an input carrying editorial text and a signed URL should contain no raw fragment of either`() {
        val input = mapOf("description" to editorialText, "heroImageUrl" to signedUrl)

        val digest = ToolInputDigest.of(input)

        digest.values.forEach { value ->
            (value.contains(editorialText)) shouldBe false
            (value.contains("édition limitée")) shouldBe false
            (value.contains(signedUrl)) shouldBe false
            (value.contains("AbCdEf0123456789")) shouldBe false
            (value.contains("shopify.com")) shouldBe false
        }
    }

    @Test
    fun `digest should only carry a single sha256 key with a 64-character hex value`() {
        val digest = ToolInputDigest.of(mapOf("query" to "shoes"))

        digest.keys shouldBe setOf("sha256")
        digest.getValue("sha256").length shouldBe 64
        digest.getValue("sha256").all { it.isDigit() || it in 'a'..'f' } shouldBe true
    }

    @Test
    fun `digest should be deterministic for the same input`() {
        val input = mapOf("query" to "shoes", "limit" to "10")

        ToolInputDigest.of(input) shouldBe ToolInputDigest.of(input)
    }

    @Test
    fun `digest should change when the input changes`() {
        val a = ToolInputDigest.of(mapOf("query" to "shoes"))
        val b = ToolInputDigest.of(mapOf("query" to "boots"))

        (a == b) shouldBe false
    }

    @Test
    fun `digest of an empty input should still be a valid single hash, not an empty map`() {
        val digest = ToolInputDigest.of(emptyMap())

        digest.keys shouldBe setOf("sha256")
        digest.getValue("sha256").length shouldBe 64
    }
}
