package com.zickat.shopifymcpserver.products.domain

import arrow.core.right
import com.zickat.shopifymcpserver.products.ProductsFakeRepository
import com.zickat.shopifymcpserver.products.domain.repositories.ProductUnpublishOutcome
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class UnpublishResourceUseCaseTest {

    private fun useCase(repository: ProductsFakeRepository = ProductsFakeRepository()) = UnpublishResourceUseCase(repository)

    @Test
    fun `should return NotFound carrying the resourceId when the repository reports the resource as not found`() {
        val repository = ProductsFakeRepository().apply { unpublishResponse = ProductUnpublishOutcome.NotFound.right() }

        val result = useCase(repository).execute("store-1", "gid://shopify/Product/999").shouldBeRight()

        result.outcome shouldBe UnpublishResourceOutcome.NOT_FOUND
        result.resourceId shouldBe "gid://shopify/Product/999"
    }

    @Test
    fun `should return a failed outcome carrying the repository's detail`() {
        val repository = ProductsFakeRepository().apply {
            unpublishResponse = ProductUnpublishOutcome.Failed("cannot unpublish").right()
        }

        val result = useCase(repository).execute("store-1", "gid://shopify/Product/1").shouldBeRight()

        result.outcome shouldBe UnpublishResourceOutcome.FAILED
        result.failureDetail shouldBe "cannot unpublish"
    }

    @Test
    fun `should return Noop carrying the title when the repository reports no active publication`() {
        val repository = ProductsFakeRepository().apply { unpublishResponse = ProductUnpublishOutcome.Noop("Item").right() }

        val result = useCase(repository).execute("store-1", "gid://shopify/Product/1").shouldBeRight()

        result.outcome shouldBe UnpublishResourceOutcome.NOOP
        result.resourceTitle shouldBe "Item"
    }

    @Test
    fun `should return Unpublished carrying the title and countBefore untouched`() {
        val repository = ProductsFakeRepository().apply {
            unpublishResponse = ProductUnpublishOutcome.Unpublished("Item", countBefore = 1).right()
        }

        val result = useCase(repository).execute("store-1", "gid://shopify/Product/1").shouldBeRight()

        result.outcome shouldBe UnpublishResourceOutcome.UNPUBLISHED
        result.resourceTitle shouldBe "Item"
        result.countBefore shouldBe 1
    }

    @Test
    fun `should forward storeId and resourceId to the repository untouched`() {
        val repository = ProductsFakeRepository().apply { unpublishResponse = ProductUnpublishOutcome.NotFound.right() }

        useCase(repository).execute("store-1", "gid://shopify/Product/1").shouldBeRight()

        repository.unpublishCalls shouldBe listOf(ProductsFakeRepository.ResourceCall("store-1", "gid://shopify/Product/1"))
    }
}
