package com.zickat.shopifymcpserver.products.domain

import arrow.core.left
import arrow.core.right
import com.zickat.shopifymcpserver.products.ProductsFakeRepository
import com.zickat.shopifymcpserver.products.domain.models.ProductFact
import com.zickat.shopifymcpserver.products.domain.models.ProductScan
import com.zickat.shopifymcpserver.products.domain.models.ProductStatusFilter
import com.zickat.shopifymcpserver.shared_kernel.TechnicalError
import io.kotest.assertions.arrow.core.shouldBeLeft
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import org.junit.jupiter.api.Test

class SearchProductsUseCaseTest {

    private fun useCase(repository: ProductsFakeRepository = ProductsFakeRepository()) = SearchProductsUseCase(repository)

    private fun fact(id: String, contentStatusValue: String? = null, hasSummaryPoints: Boolean = false) =
        ProductFact(id = id, title = "T $id", handle = "h-$id", status = "ACTIVE", contentStatusValue = contentStatusValue, hasSummaryPoints = hasSummaryPoints)

    @Test
    fun `untreated filter should keep only products with neither content_status nor summary_points — the double guard`() {
        val repository = ProductsFakeRepository().apply {
            searchResponse = ProductScan(
                entries = listOf(
                    fact("gid://shopify/Product/1"),
                    fact("gid://shopify/Product/2", contentStatusValue = "to_review"),
                    fact("gid://shopify/Product/3", hasSummaryPoints = true),
                ),
                truncated = false,
            ).right()
        }

        val result = useCase(repository).execute("store-1", null, ProductStatusFilter.UNTREATED).shouldBeRight()

        result.entries.map { it.id } shouldBe listOf("gid://shopify/Product/1")
    }

    @Test
    fun `all filter should keep every product regardless of status`() {
        val repository = ProductsFakeRepository().apply {
            searchResponse = ProductScan(
                entries = listOf(fact("gid://shopify/Product/1"), fact("gid://shopify/Product/2", contentStatusValue = "blocked")),
                truncated = false,
            ).right()
        }

        val result = useCase(repository).execute("store-1", null, ProductStatusFilter.ALL).shouldBeRight()

        result.entries shouldHaveSize 2
    }

    @Test
    fun `a product with summary_points but no content_status should be derived as published, not untreated — BUG-01`() {
        val repository = ProductsFakeRepository().apply {
            searchResponse = ProductScan(entries = listOf(fact("gid://shopify/Product/1", hasSummaryPoints = true)), truncated = false).right()
        }

        val result = useCase(repository).execute("store-1", null, ProductStatusFilter.ALL).shouldBeRight()

        result.entries.single().contentStatus shouldBe "published"
    }

    @Test
    fun `should forward the repository's truncated flag untouched`() {
        val repository = ProductsFakeRepository().apply {
            searchResponse = ProductScan(entries = emptyList(), truncated = true).right()
        }

        val result = useCase(repository).execute("store-1", null, ProductStatusFilter.ALL).shouldBeRight()

        result.truncated shouldBe true
    }

    @Test
    fun `should forward storeId and query to the repository untouched`() {
        val repository = ProductsFakeRepository().apply {
            searchResponse = ProductScan(entries = emptyList(), truncated = false).right()
        }

        useCase(repository).execute("store-1", "product_type:sacoches", ProductStatusFilter.ALL).shouldBeRight()

        repository.searchCalls shouldBe listOf(ProductsFakeRepository.SearchCall("store-1", "product_type:sacoches"))
    }

    @Test
    fun `should propagate a Left when the repository fails technically`() {
        val repository = ProductsFakeRepository().apply {
            searchResponse = TechnicalError("shopify.graphql.response.malformed").left()
        }

        useCase(repository).execute("store-1", null, ProductStatusFilter.ALL).shouldBeLeft()
            .shouldBeInstanceOf<TechnicalError>().messageKey shouldBe "shopify.graphql.response.malformed"
    }
}
