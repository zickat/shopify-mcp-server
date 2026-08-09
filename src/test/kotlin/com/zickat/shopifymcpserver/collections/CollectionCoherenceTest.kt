package com.zickat.shopifymcpserver.collections

import com.zickat.shopifymcpserver.collections.domain.CollectionCoherence.judgeCoherence
import com.zickat.shopifymcpserver.collections.domain.models.CollectionCoherenceVerdict
import com.zickat.shopifymcpserver.collections.domain.models.ProductForSegmentation
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import org.junit.jupiter.api.Test

class CollectionCoherenceTest {

    @Test
    fun `refuses a single product — a collection helps choose between several products`() {
        // given
        val products = listOf(
            ProductForSegmentation(
                id = "gid://shopify/Product/1",
                title = "Sacoche de guidon",
                status = "ACTIVE",
                productType = "Sacoche",
                tags = listOf("rangement"),
            ),
        )

        // when
        val verdict = judgeCoherence(products)

        // then
        val incoherent = verdict.shouldBeInstanceOf<CollectionCoherenceVerdict.Incoherent>()
        incoherent.reason.contains("Sacoche de guidon") shouldBe true
    }

    @Test
    fun `refuses two products when one carries no productType, naming that product`() {
        // given
        val products = listOf(
            ProductForSegmentation(
                id = "gid://shopify/Product/1",
                title = "Sacoche de guidon",
                status = "ACTIVE",
                productType = "Sacoche",
                tags = emptyList(),
            ),
            ProductForSegmentation(
                id = "gid://shopify/Product/2",
                title = "Sacoche de cadre",
                status = "ACTIVE",
                productType = "",
                tags = emptyList(),
            ),
        )

        // when
        val verdict = judgeCoherence(products)

        // then
        val incoherent = verdict.shouldBeInstanceOf<CollectionCoherenceVerdict.Incoherent>()
        incoherent.reason.contains("Sacoche de cadre") shouldBe true
        incoherent.reason.contains("Sacoche de guidon") shouldBe false
    }

    @Test
    fun `accepts two products sharing the same productType`() {
        // given
        val products = listOf(
            ProductForSegmentation(
                id = "gid://shopify/Product/1",
                title = "Sacoche de guidon",
                status = "ACTIVE",
                productType = "Sacoche",
                tags = emptyList(),
            ),
            ProductForSegmentation(
                id = "gid://shopify/Product/2",
                title = "Sacoche de cadre",
                status = "ACTIVE",
                productType = "Sacoche",
                tags = emptyList(),
            ),
        )

        // when
        val verdict = judgeCoherence(products)

        // then
        verdict shouldBe CollectionCoherenceVerdict.Coherent
    }

    @Test
    fun `accepts two products with different productType when a tag is shared by both, case-insensitively`() {
        // given
        val products = listOf(
            ProductForSegmentation(
                id = "gid://shopify/Product/1",
                title = "Sacoche de guidon",
                status = "ACTIVE",
                productType = "Sacoche de guidon",
                tags = listOf("sacoches-rangement"),
            ),
            ProductForSegmentation(
                id = "gid://shopify/Product/2",
                title = "Sacoche de cadre",
                status = "ACTIVE",
                productType = "Sacoche de cadre",
                tags = listOf("Sacoches-Rangement"),
            ),
        )

        // when
        val verdict = judgeCoherence(products)

        // then
        verdict shouldBe CollectionCoherenceVerdict.Coherent
    }

    @Test
    fun `refuses two products with different productType and no tag common to both, listing the distinct types`() {
        // given
        val products = listOf(
            ProductForSegmentation(
                id = "gid://shopify/Product/1",
                title = "Sacoche de guidon",
                status = "ACTIVE",
                productType = "Sacoche",
                tags = listOf("rangement"),
            ),
            ProductForSegmentation(
                id = "gid://shopify/Product/2",
                title = "Casque route",
                status = "ACTIVE",
                productType = "Casque",
                tags = listOf("securite"),
            ),
        )

        // when
        val verdict = judgeCoherence(products)

        // then
        val incoherent = verdict.shouldBeInstanceOf<CollectionCoherenceVerdict.Incoherent>()
        incoherent.reason.contains("Sacoche") shouldBe true
        incoherent.reason.contains("Casque") shouldBe true
    }

    @Test
    fun `refuses three products when a tag is shared by two but not the third — common to all, not to some`() {
        // given
        val products = listOf(
            ProductForSegmentation(
                id = "gid://shopify/Product/1",
                title = "Sacoche de guidon",
                status = "ACTIVE",
                productType = "Sacoche de guidon",
                tags = listOf("rangement"),
            ),
            ProductForSegmentation(
                id = "gid://shopify/Product/2",
                title = "Sacoche de cadre",
                status = "ACTIVE",
                productType = "Sacoche de cadre",
                tags = listOf("rangement"),
            ),
            ProductForSegmentation(
                id = "gid://shopify/Product/3",
                title = "Casque route",
                status = "ACTIVE",
                productType = "Casque",
                tags = listOf("securite"),
            ),
        )

        // when
        val verdict = judgeCoherence(products)

        // then
        verdict.shouldBeInstanceOf<CollectionCoherenceVerdict.Incoherent>()
    }
}
