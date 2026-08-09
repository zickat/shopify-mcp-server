package com.zickat.shopifymcpserver.seo.domain.models

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class SeoResourceTypeTest {

    @Test
    fun `fromToolValue should map every accepted tool value to its resource type`() {
        SeoResourceType.fromToolValue("product") shouldBe SeoResourceType.PRODUCT
        SeoResourceType.fromToolValue("collection") shouldBe SeoResourceType.COLLECTION
        SeoResourceType.fromToolValue("article") shouldBe SeoResourceType.ARTICLE
        SeoResourceType.fromToolValue("page") shouldBe SeoResourceType.PAGE
    }

    @Test
    fun `fromToolValue should return null for an unknown value`() {
        SeoResourceType.fromToolValue("guide") shouldBe null
    }

    @Test
    fun `product and collection should dispatch to the native mechanism, article and page to the metafield mechanism`() {
        SeoResourceType.PRODUCT.mechanism shouldBe SeoMechanism.NATIVE
        SeoResourceType.COLLECTION.mechanism shouldBe SeoMechanism.NATIVE
        SeoResourceType.ARTICLE.mechanism shouldBe SeoMechanism.METAFIELD
        SeoResourceType.PAGE.mechanism shouldBe SeoMechanism.METAFIELD
    }
}
