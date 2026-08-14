package com.zickat.shopifymcpserver.seo.domain

import arrow.core.left
import arrow.core.right
import com.zickat.shopifymcpserver.seo.SeoFakeRepository
import com.zickat.shopifymcpserver.seo.domain.models.SeoResourceType
import com.zickat.shopifymcpserver.seo.domain.models.SeoSnapshot
import com.zickat.shopifymcpserver.seo.exposed_interface.model.GetSeoResult
import com.zickat.shopifymcpserver.shared_kernel.TechnicalError
import io.kotest.assertions.arrow.core.shouldBeLeft
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import org.junit.jupiter.api.Test

class GetSeoUseCaseTest {

    private fun useCase(repository: SeoFakeRepository = SeoFakeRepository()) = GetSeoUseCase(repository)

    @Test
    fun `should return Found built from the repository snapshot when the resource exists`() {
        val repository = SeoFakeRepository().apply {
            getResponse = SeoSnapshot("Item", "T", "D").right()
        }

        val result = useCase(repository).execute("store-1", SeoResourceType.PRODUCT, "gid://shopify/Product/1").shouldBeRight()

        result shouldBe GetSeoResult.found("Item", "T", "D")
    }

    @Test
    fun `should return NotFound when the repository returns no snapshot`() {
        val repository = SeoFakeRepository().apply {
            getResponse = null.right()
        }

        val result = useCase(repository).execute("store-1", SeoResourceType.COLLECTION, "gid://shopify/Collection/999").shouldBeRight()

        result shouldBe GetSeoResult.NotFound
    }

    @Test
    fun `should propagate a Left when the repository fails`() {
        val repository = SeoFakeRepository().apply {
            getResponse = TechnicalError("shopify.graphql.response.malformed").left()
        }

        useCase(repository).execute("store-1", SeoResourceType.ARTICLE, "gid://shopify/Article/1").shouldBeLeft()
            .shouldBeInstanceOf<TechnicalError>().messageKey shouldBe "shopify.graphql.response.malformed"
    }

    @Test
    fun `should forward storeId, resourceType and resourceId to the repository untouched`() {
        val repository = SeoFakeRepository()

        useCase(repository).execute("store-1", SeoResourceType.PAGE, "gid://shopify/Page/42").shouldBeRight()

        repository.getCalls shouldBe listOf(SeoFakeRepository.GetCall("store-1", SeoResourceType.PAGE, "gid://shopify/Page/42"))
    }
}
