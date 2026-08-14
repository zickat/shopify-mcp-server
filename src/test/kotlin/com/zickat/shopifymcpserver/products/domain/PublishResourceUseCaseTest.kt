package com.zickat.shopifymcpserver.products.domain

import arrow.core.right
import com.zickat.shopifymcpserver.products.ProductsFakeRepository
import com.zickat.shopifymcpserver.products.domain.repositories.ProductPublishOutcome
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class PublishResourceUseCaseTest {

    private fun useCase(repository: ProductsFakeRepository = ProductsFakeRepository()) = PublishResourceUseCase(repository)

    @Test
    fun `should return NotFound carrying the resourceId when the repository reports the resource as not found`() {
        val repository = ProductsFakeRepository().apply { publishResponse = ProductPublishOutcome.NotFound.right() }

        val result = useCase(repository).execute("store-1", "gid://shopify/Product/999").shouldBeRight()

        result.outcome shouldBe PublishResourceOutcome.NOT_FOUND
        result.resourceId shouldBe "gid://shopify/Product/999"
    }

    @Test
    fun `should return a failed outcome carrying the repository's detail`() {
        val repository = ProductsFakeRepository().apply {
            publishResponse = ProductPublishOutcome.Failed("status : cannot activate").right()
        }

        val result = useCase(repository).execute("store-1", "gid://shopify/Product/1").shouldBeRight()

        result.outcome shouldBe PublishResourceOutcome.FAILED
        result.failureDetail shouldBe "status : cannot activate"
    }

    @Test
    fun `should return Published carrying the title and wasNeverPublished untouched`() {
        val repository = ProductsFakeRepository().apply {
            publishResponse = ProductPublishOutcome.Published("Item", wasNeverPublished = false).right()
        }

        val result = useCase(repository).execute("store-1", "gid://shopify/Product/1").shouldBeRight()

        result.outcome shouldBe PublishResourceOutcome.PUBLISHED
        result.resourceTitle shouldBe "Item"
        result.wasNeverPublished shouldBe false
    }

    @Test
    fun `should forward storeId and resourceId to the repository untouched`() {
        val repository = ProductsFakeRepository().apply { publishResponse = ProductPublishOutcome.NotFound.right() }

        useCase(repository).execute("store-1", "gid://shopify/Product/1").shouldBeRight()

        repository.publishCalls shouldBe listOf(ProductsFakeRepository.ResourceCall("store-1", "gid://shopify/Product/1"))
    }
}
