package com.zickat.shopifymcpserver.redirects.domain

import arrow.core.left
import arrow.core.right
import com.zickat.shopifymcpserver.redirects.RedirectsFakeRepository
import com.zickat.shopifymcpserver.redirects.domain.models.CreateRedirectOutcome
import com.zickat.shopifymcpserver.redirects.domain.models.RequiredRedirectField
import com.zickat.shopifymcpserver.shared_kernel.TechnicalError
import io.kotest.assertions.arrow.core.shouldBeLeft
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import org.junit.jupiter.api.Test

class CreateRedirectUseCaseTest {

    private fun useCase(repository: RedirectsFakeRepository = RedirectsFakeRepository()) = CreateRedirectUseCase(repository)

    @Test
    fun `execute should refuse a blank from_path before calling the repository`() {
        val repository = RedirectsFakeRepository()

        val outcome = useCase(repository).execute("store-1", "  ", "/collections/target").shouldBeRight()

        outcome shouldBe CreateRedirectOutcome.invalidInput(RequiredRedirectField.FROM_PATH)
        repository.createCalls shouldHaveSize 0
    }

    @Test
    fun `execute should refuse a blank to_path before calling the repository`() {
        val repository = RedirectsFakeRepository()

        val outcome = useCase(repository).execute("store-1", "/collections/old", " ").shouldBeRight()

        outcome shouldBe CreateRedirectOutcome.invalidInput(RequiredRedirectField.TO_PATH)
        repository.createCalls shouldHaveSize 0
    }

    @Test
    fun `execute should delegate to the repository and return its outcome untouched when both paths are provided`() {
        val repository = RedirectsFakeRepository().apply {
            createResponse = CreateRedirectOutcome.Created.right()
        }

        val outcome = useCase(repository).execute("store-1", "/collections/old", "/collections/new").shouldBeRight()

        outcome shouldBe CreateRedirectOutcome.Created
        repository.createCalls shouldBe listOf(RedirectsFakeRepository.CreateCall("store-1", "/collections/old", "/collections/new"))
    }

    @Test
    fun `execute should propagate a Left when the repository fails technically`() {
        val repository = RedirectsFakeRepository().apply {
            createResponse = TechnicalError("shopify.graphql.response.malformed").left()
        }

        useCase(repository).execute("store-1", "/collections/old", "/collections/new").shouldBeLeft()
            .shouldBeInstanceOf<TechnicalError>().messageKey shouldBe "shopify.graphql.response.malformed"
    }
}
