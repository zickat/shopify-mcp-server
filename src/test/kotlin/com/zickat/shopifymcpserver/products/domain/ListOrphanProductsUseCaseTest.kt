package com.zickat.shopifymcpserver.products.domain

import arrow.core.right
import com.zickat.shopifymcpserver.products.ProductsFakeRepository
import com.zickat.shopifymcpserver.products.domain.models.CollectionRef
import com.zickat.shopifymcpserver.products.domain.models.OrphanScan
import com.zickat.shopifymcpserver.products.domain.models.ProductFact
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class ListOrphanProductsUseCaseTest {

    private fun useCase(repository: ProductsFakeRepository = ProductsFakeRepository()) = ListOrphanProductsUseCase(repository)

    @Test
    fun `should map each orphan fact to a listing entry deriving its pipeline status`() {
        val fact = ProductFact(
            id = "gid://shopify/Product/1",
            title = "Orphan",
            handle = "orphan",
            status = "DRAFT",
            contentStatusValue = null,
            hasSummaryPoints = true,
        )
        val repository = ProductsFakeRepository().apply {
            listOrphansResponse = OrphanScan(
                orphans = listOf(fact),
                collectionsTruncated = false,
                collectionsWithTruncatedProducts = emptyList(),
                productsTruncated = false,
            ).right()
        }

        val result = useCase(repository).execute("store-1").shouldBeRight()

        result.orphans.single().contentStatus shouldBe "published"
        result.orphans.single().id shouldBe "gid://shopify/Product/1"
    }

    @Test
    fun `should forward truncation flags and the truncated collection refs untouched`() {
        val repository = ProductsFakeRepository().apply {
            listOrphansResponse = OrphanScan(
                orphans = emptyList(),
                collectionsTruncated = true,
                collectionsWithTruncatedProducts = listOf(CollectionRef("gid://shopify/Collection/1", "Big collection")),
                productsTruncated = true,
            ).right()
        }

        val result = useCase(repository).execute("store-1").shouldBeRight()

        result.collectionsTruncated shouldBe true
        result.productsTruncated shouldBe true
        result.collectionsWithTruncatedProducts shouldBe listOf(CollectionRef("gid://shopify/Collection/1", "Big collection"))
    }

    @Test
    fun `should forward storeId to the repository untouched`() {
        val repository = ProductsFakeRepository().apply {
            listOrphansResponse = OrphanScan(emptyList(), false, emptyList(), false).right()
        }

        useCase(repository).execute("store-1").shouldBeRight()

        repository.listOrphansCalls shouldBe listOf("store-1")
    }
}
