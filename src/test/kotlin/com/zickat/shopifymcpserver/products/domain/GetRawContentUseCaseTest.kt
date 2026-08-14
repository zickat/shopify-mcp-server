package com.zickat.shopifymcpserver.products.domain

import arrow.core.right
import com.zickat.shopifymcpserver.products.ProductsFakeRepository
import com.zickat.shopifymcpserver.products.domain.models.ProductRawSnapshot
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class GetRawContentUseCaseTest {

    private fun useCase(repository: ProductsFakeRepository = ProductsFakeRepository()) = GetRawContentUseCase(repository)

    @Test
    fun `should return NotFound carrying the productId when the repository returns null`() {
        val repository = ProductsFakeRepository().apply { rawContentResponse = null.right() }

        val result = useCase(repository).execute("store-1", "gid://shopify/Product/999").shouldBeRight()

        result.outcome shouldBe GetRawContentOutcome.NOT_FOUND
        result.productId shouldBe "gid://shopify/Product/999"
    }

    @Test
    fun `should return Found carrying the repository's snapshot untouched`() {
        val snapshot = ProductRawSnapshot(
            title = "Maillot",
            handle = "maillot",
            status = "ACTIVE",
            descriptionHtml = "<p>Un maillot</p>",
            minPrice = "29.99",
            maxPrice = "29.99",
            currency = "EUR",
            media = emptyList(),
            options = emptyList(),
            variants = emptyList(),
        )
        val repository = ProductsFakeRepository().apply { rawContentResponse = snapshot.right() }

        val result = useCase(repository).execute("store-1", "gid://shopify/Product/1").shouldBeRight()

        result.outcome shouldBe GetRawContentOutcome.FOUND
        result.snapshot shouldBe snapshot
    }

    @Test
    fun `should forward storeId and productId to the repository untouched`() {
        val repository = ProductsFakeRepository().apply { rawContentResponse = null.right() }

        useCase(repository).execute("store-1", "gid://shopify/Product/1").shouldBeRight()

        repository.rawContentCalls shouldBe listOf(ProductsFakeRepository.ResourceCall("store-1", "gid://shopify/Product/1"))
    }
}
