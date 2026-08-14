package com.zickat.shopifymcpserver.seo.domain

import arrow.core.left
import arrow.core.right
import com.zickat.shopifymcpserver.seo.SeoFakeRepository
import com.zickat.shopifymcpserver.seo.domain.models.SeoResourceType
import com.zickat.shopifymcpserver.seo.domain.repositories.SeoWriteOutcome
import com.zickat.shopifymcpserver.seo.exposed_interface.model.UpdateSeoOutcome
import com.zickat.shopifymcpserver.shared_kernel.TechnicalError
import io.kotest.assertions.arrow.core.shouldBeLeft
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import org.junit.jupiter.api.Test

class UpdateSeoUseCaseTest {

    private fun useCase(repository: SeoFakeRepository = SeoFakeRepository()) = UpdateSeoUseCase(repository)

    @Test
    fun `should return a no-op outcome and never call the repository when both seo fields are omitted`() {
        val repository = SeoFakeRepository()

        val result = useCase(repository).execute("store-1", SeoResourceType.PRODUCT, "gid://shopify/Product/1", null, null).shouldBeRight()

        result.outcome shouldBe UpdateSeoOutcome.NO_OP
        repository.updateCalls shouldHaveSize 0
    }

    @Test
    fun `should return Updated marking only the title as modified when only the title was provided`() {
        val repository = SeoFakeRepository().apply {
            updateResponse = SeoWriteOutcome.Updated("Item", "New title", "Old desc").right()
        }

        val result = useCase(repository).execute("store-1", SeoResourceType.PRODUCT, "gid://shopify/Product/1", "New title", null).shouldBeRight()

        result.outcome shouldBe UpdateSeoOutcome.UPDATED
        result.resourceTitle shouldBe "Item"
        result.finalMetaTitle shouldBe "New title"
        result.finalMetaDescription shouldBe "Old desc"
        result.titleModified shouldBe true
        result.descriptionModified shouldBe false
    }

    @Test
    fun `should return Updated marking only the description as modified when only the description was provided`() {
        val repository = SeoFakeRepository().apply {
            updateResponse = SeoWriteOutcome.Updated("Item", "Old title", "New desc").right()
        }

        val result = useCase(repository).execute("store-1", SeoResourceType.PAGE, "gid://shopify/Page/1", null, "New desc").shouldBeRight()

        result.titleModified shouldBe false
        result.descriptionModified shouldBe true
    }

    @Test
    fun `should return Updated marking both fields as modified when both were provided`() {
        val repository = SeoFakeRepository().apply {
            updateResponse = SeoWriteOutcome.Updated("Item", "New title", "New desc").right()
        }

        val result = useCase(repository).execute("store-1", SeoResourceType.COLLECTION, "gid://shopify/Collection/1", "New title", "New desc").shouldBeRight()

        result.titleModified shouldBe true
        result.descriptionModified shouldBe true
    }

    @Test
    fun `should return NotFound when the repository reports the resource as not found`() {
        val repository = SeoFakeRepository().apply {
            updateResponse = SeoWriteOutcome.NotFound.right()
        }

        val result = useCase(repository).execute("store-1", SeoResourceType.COLLECTION, "gid://shopify/Collection/999", "New title", null).shouldBeRight()

        result.outcome shouldBe UpdateSeoOutcome.NOT_FOUND
    }

    @Test
    fun `should return a failed outcome carrying the repository's formatted detail when the repository reports a failure`() {
        val repository = SeoFakeRepository().apply {
            updateResponse = SeoWriteOutcome.Failed("seo.title : is too long").right()
        }

        val result = useCase(repository).execute("store-1", SeoResourceType.PRODUCT, "gid://shopify/Product/1", "New title", null).shouldBeRight()

        result.outcome shouldBe UpdateSeoOutcome.FAILED
        result.failureDetail shouldBe "seo.title : is too long"
    }

    @Test
    fun `should propagate a Left when the repository fails technically`() {
        val repository = SeoFakeRepository().apply {
            updateResponse = TechnicalError("shopify.graphql.response.malformed").left()
        }

        useCase(repository).execute("store-1", SeoResourceType.PRODUCT, "gid://shopify/Product/1", "New title", null).shouldBeLeft()
            .shouldBeInstanceOf<TechnicalError>().messageKey shouldBe "shopify.graphql.response.malformed"
    }

    @Test
    fun `should forward storeId, resourceType, resourceId and both seo fields to the repository untouched`() {
        val repository = SeoFakeRepository().apply {
            updateResponse = SeoWriteOutcome.Updated("Item", "New title", "New desc").right()
        }

        useCase(repository).execute("store-1", SeoResourceType.ARTICLE, "gid://shopify/Article/1", "New title", "New desc").shouldBeRight()

        repository.updateCalls shouldBe listOf(
            SeoFakeRepository.UpdateCall("store-1", SeoResourceType.ARTICLE, "gid://shopify/Article/1", "New title", "New desc"),
        )
    }
}
