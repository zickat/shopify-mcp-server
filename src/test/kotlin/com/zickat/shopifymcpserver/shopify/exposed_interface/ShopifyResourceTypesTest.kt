package com.zickat.shopifymcpserver.shopify.exposed_interface

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class ShopifyResourceTypesTest {

    @Test
    fun `should map each known tool resource_type value to its Shopify gid type`() {
        ShopifyResourceTypes.gidTypeFor("product") shouldBe "Product"
        ShopifyResourceTypes.gidTypeFor("collection") shouldBe "Collection"
        ShopifyResourceTypes.gidTypeFor("article") shouldBe "Article"
        ShopifyResourceTypes.gidTypeFor("page") shouldBe "Page"
    }

    @Test
    fun `should return null for an unknown resource_type value`() {
        ShopifyResourceTypes.gidTypeFor("prodcut") shouldBe null
    }
}
