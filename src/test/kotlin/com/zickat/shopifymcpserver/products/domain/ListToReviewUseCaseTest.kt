package com.zickat.shopifymcpserver.products.domain

import arrow.core.right
import com.zickat.shopifymcpserver.products.ProductsFakeRepository
import com.zickat.shopifymcpserver.products.domain.models.ToReviewEntry
import com.zickat.shopifymcpserver.products.domain.models.ToReviewResourceType
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class ListToReviewUseCaseTest {

    private fun useCase(repository: ProductsFakeRepository = ProductsFakeRepository()) = ListToReviewUseCase(repository)

    @Test
    fun `should carry the requested resource type through to the result`() {
        val repository = ProductsFakeRepository().apply { listToReviewResponse = emptyList<ToReviewEntry>().right() }

        val result = useCase(repository).execute("store-1", ToReviewResourceType.ARTICLE).shouldBeRight()

        result.resourceType shouldBe ToReviewResourceType.ARTICLE
        result.entries shouldBe emptyList()
    }

    @Test
    fun `should forward the repository's entries untouched`() {
        val entries = listOf(ToReviewEntry("gid://shopify/Collection/1", "To review", "to-review"))
        val repository = ProductsFakeRepository().apply { listToReviewResponse = entries.right() }

        val result = useCase(repository).execute("store-1", ToReviewResourceType.COLLECTION).shouldBeRight()

        result.entries shouldBe entries
    }

    @Test
    fun `should forward storeId and resourceType to the repository untouched`() {
        val repository = ProductsFakeRepository().apply { listToReviewResponse = emptyList<ToReviewEntry>().right() }

        useCase(repository).execute("store-1", ToReviewResourceType.PRODUCT).shouldBeRight()

        repository.listToReviewCalls shouldBe listOf(ProductsFakeRepository.ListToReviewCall("store-1", ToReviewResourceType.PRODUCT))
    }
}
