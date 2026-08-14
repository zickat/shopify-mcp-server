package com.zickat.shopifymcpserver.seo.domain

import arrow.core.right
import com.zickat.shopifymcpserver.seo.SeoFakeRepository
import com.zickat.shopifymcpserver.seo.domain.models.SeoSnapshot
import com.zickat.shopifymcpserver.seo.exposed_interface.model.GetSeoResult
import com.zickat.shopifymcpserver.seo.exposed_interface.model.UpdateSeoOutcome
import com.zickat.shopifymcpserver.shared_kernel.TechnicalError
import io.kotest.assertions.arrow.core.shouldBeLeft
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import org.junit.jupiter.api.Test

class SeoUseCaseTest {

    private fun service(repository: SeoFakeRepository) = SeoExposedServiceImpl(GetSeoUseCase(repository), UpdateSeoUseCase(repository))

    @Test
    fun `getSeo should delegate to GetSeoUseCase once the resource_type string is parsed`() {
        val repository = SeoFakeRepository().apply {
            getResponse = SeoSnapshot("T", null, null).right()
        }

        val result = service(repository).getSeo("store-1", "collection", "gid://shopify/Collection/1").shouldBeRight()

        result shouldBe GetSeoResult.found("T", null, null)
    }

    @Test
    fun `getSeo should reject an unknown resource_type before reaching the repository`() {
        val repository = SeoFakeRepository()

        val error = service(repository).getSeo("store-1", "not-a-real-type", "gid://shopify/Product/1").shouldBeLeft()

        error.shouldBeInstanceOf<TechnicalError>()
        error.messageKey shouldBe "seo.resource_type.unexpected"
        repository.getCalls shouldBe emptyList()
    }

    @Test
    fun `updateSeo should delegate to UpdateSeoUseCase once the resource_type string is parsed`() {
        val repository = SeoFakeRepository()

        val result = service(repository).updateSeo("store-1", "collection", "gid://shopify/Collection/1", null, null).shouldBeRight()

        result.outcome shouldBe UpdateSeoOutcome.NO_OP
    }

    @Test
    fun `updateSeo should reject an unknown resource_type before reaching the repository`() {
        val repository = SeoFakeRepository()

        val error = service(repository).updateSeo("store-1", "not-a-real-type", "gid://shopify/Product/1", "New title", null).shouldBeLeft()

        error.shouldBeInstanceOf<TechnicalError>()
        error.messageKey shouldBe "seo.resource_type.unexpected"
        repository.updateCalls shouldBe emptyList()
    }
}
