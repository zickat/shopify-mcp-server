package com.zickat.shopifymcpserver.products.domain

import arrow.core.right
import com.zickat.shopifymcpserver.products.ProductsFakeRepository
import com.zickat.shopifymcpserver.products.domain.repositories.ProductMetafieldWriteOutcome
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class MarkBlockedUseCaseTest {

    private fun useCase(repository: ProductsFakeRepository = ProductsFakeRepository()) = MarkBlockedUseCase(repository)

    @Test
    fun `should return Marked when the repository marks the resource`() {
        val repository = ProductsFakeRepository().apply { markBlockedResponse = ProductMetafieldWriteOutcome.Marked.right() }

        val result = useCase(repository).execute("store-1", "gid://shopify/Product/1").shouldBeRight()

        result.outcome shouldBe MarkBlockedOutcome.MARKED
    }

    @Test
    fun `should return a failed outcome carrying the repository's detail`() {
        val repository = ProductsFakeRepository().apply {
            markBlockedResponse = ProductMetafieldWriteOutcome.Failed("metafields.0.value : is invalid").right()
        }

        val result = useCase(repository).execute("store-1", "gid://shopify/Product/1").shouldBeRight()

        result.outcome shouldBe MarkBlockedOutcome.FAILED
        result.failureDetail shouldBe "metafields.0.value : is invalid"
    }

    @Test
    fun `should forward storeId and resourceId to the repository untouched`() {
        val repository = ProductsFakeRepository().apply { markBlockedResponse = ProductMetafieldWriteOutcome.Marked.right() }

        useCase(repository).execute("store-1", "gid://shopify/Product/1").shouldBeRight()

        repository.markBlockedCalls shouldBe listOf(ProductsFakeRepository.ResourceCall("store-1", "gid://shopify/Product/1"))
    }
}
