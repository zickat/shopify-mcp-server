package com.zickat.shopifymcpserver.identity

import arrow.core.Either
import com.zickat.shopifymcpserver.identity.domain.IdentityUseCase
import com.zickat.shopifymcpserver.identity.domain.models.Identity
import com.zickat.shopifymcpserver.identity.domain.models.IdentityId
import com.zickat.shopifymcpserver.identity.domain.repositories.IdentityRepository
import com.zickat.shopifymcpserver.shared_kernel.TechnicalError
import com.zickat.shopifymcpserver.shared_kernel.UseCaseError
import io.kotest.assertions.arrow.core.shouldBeLeft
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import org.junit.jupiter.api.Test

class IdentityUseCaseTest {

    private val repository = IdentityFakeRepository()
    private val useCase = IdentityUseCase(repository)

    @Test
    fun `findOrCreate should create a new identity the first time it presents itself`() {
        val result = useCase.findOrCreate("https://idp.test/", "operator-1", "Operator One")

        val identity = result.shouldBeRight()
        identity.issuer shouldBe "https://idp.test/"
        identity.subject shouldBe "operator-1"
        identity.displayName shouldBe "Operator One"
        repository.store shouldBe mapOf(identity.id.value to identity)
    }

    @Test
    fun `findOrCreate should return the existing identity on a second presentation, keeping its original displayName instead of the new one`() {
        val first = useCase.findOrCreate("https://idp.test/", "operator-1", "Operator One").shouldBeRight()

        val second = useCase.findOrCreate("https://idp.test/", "operator-1", "Ignored On Reconnect")

        val identity = second.shouldBeRight()
        identity.id shouldBe first.id
        identity.displayName shouldBe "Operator One"
        repository.store.size shouldBe 1
    }

    @Test
    fun `findOrCreate should recover the existing identity when two concurrent calls race into the same duplicate key`() {
        val existing = useCase.findOrCreate("https://idp.test/", "operator-2", "Operator Two").shouldBeRight()

        val recovered = useCase.findOrCreate("https://idp.test/", "operator-2", "Ignored")

        recovered.shouldBeRight().id shouldBe existing.id
    }

    @Test
    fun `findOrCreate should propagate a technical error as TechnicalError specifically, not mask it as a duplicate-key creation failure`() {
        val failingRepository = object : IdentityRepository {
            override fun save(identity: Identity) = repository.save(identity)
            override fun findById(id: IdentityId) = repository.findById(id)
            override fun findByIssuerAndSubject(issuer: String, subject: String): Either<UseCaseError, Identity> =
                Either.Left(TechnicalError("identity.lookup.failed"))
        }
        val useCaseWithFailingRepository = IdentityUseCase(failingRepository)

        val result = useCaseWithFailingRepository.findOrCreate("https://idp.test/", "operator-3", "Operator Three")

        result.shouldBeLeft().shouldBeInstanceOf<TechnicalError>().messageKey shouldBe "identity.lookup.failed"
    }
}
