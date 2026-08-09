package com.zickat.shopifymcpserver.shared_kernel

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class GidTypesTest {

    @Test
    fun `isGidOfType should return true when the gid is well formed and of the expected type`() {
        "gid://shopify/Collection/123".isGidOfType("Collection") shouldBe true
    }

    @Test
    fun `isGidOfType should return false when the gid is well formed but of a different type`() {
        "gid://shopify/Product/123".isGidOfType("Collection") shouldBe false
    }

    @Test
    fun `isGidOfType should return false when the value is not syntactically a gid at all`() {
        "not-a-gid".isGidOfType("Collection") shouldBe false
    }

    @Test
    fun `isGidOfType should return false when the gid carries no resource id segment`() {
        "gid://shopify/Collection".isGidOfType("Collection") shouldBe false
    }

    @Test
    fun `isGidOfType should return false for an empty string`() {
        "".isGidOfType("Collection") shouldBe false
    }

    @Test
    fun `isGidOfAnyType should return true when the gid matches one of several accepted types`() {
        "gid://shopify/Product/123".isGidOfAnyType(listOf("Collection", "Product", "Page")) shouldBe true
    }

    @Test
    fun `isGidOfAnyType should return false when the gid matches none of the accepted types`() {
        "gid://shopify/Metaobject/123".isGidOfAnyType(listOf("Collection", "Product", "Page")) shouldBe false
    }

    @Test
    fun `isGidOfAnyType should return false for a syntactically invalid gid even against a broad type list`() {
        "prodcut/123".isGidOfAnyType(listOf("Collection", "Product", "Page")) shouldBe false
    }

    @Test
    fun `a list of ids can be checked against a single expected type with a plain all`() {
        val ids = listOf("gid://shopify/Product/1", "gid://shopify/Product/2")

        ids.all { it.isGidOfType("Product") } shouldBe true
    }

    @Test
    fun `a list of ids with one wrong-type element fails the plain all check`() {
        val ids = listOf("gid://shopify/Product/1", "gid://shopify/Collection/2")

        ids.all { it.isGidOfType("Product") } shouldBe false
    }

    @Test
    fun `gidResourceType should extract the type segment of a well formed gid`() {
        "gid://shopify/Page/999999999999".gidResourceType() shouldBe "Page"
    }

    @Test
    fun `gidResourceType should return null when the value is not a gid`() {
        "gid://shopify/Page".gidResourceType() shouldBe null
    }
}
