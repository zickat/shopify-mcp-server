package com.zickat.shopifymcpserver.identity

import arrow.core.Either
import com.zickat.shopifymcpserver.identity.domain.IdentityUseCase
import com.zickat.shopifymcpserver.identity.domain.models.Identity
import com.zickat.shopifymcpserver.identity.domain.models.IdentityId
import com.zickat.shopifymcpserver.identity.domain.repositories.IdentityRepository
import com.zickat.shopifymcpserver.shared_kernel.TechnicalError
import com.zickat.shopifymcpserver.shared_kernel.UseCaseError
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

/** `LOT0-06` — retrouver ou créer une identité `(issuer, subject)`, en mémoire (fakes uniquement). */
class IdentityUseCaseTest {

    private val repository = IdentityFakeRepository()
    private val useCase = IdentityUseCase(repository)

    @Test
    fun `findOrCreate should create a new identity the first time it presents itself`() {
        val result = useCase.findOrCreate("https://idp.test/", "operator-1", "Operator One")

        result.fold(
            { error("expected creation, got $it") },
            { identity ->
                identity.issuer shouldBe "https://idp.test/"
                identity.subject shouldBe "operator-1"
                identity.displayName shouldBe "Operator One"
                repository.store shouldBe mapOf(identity.id.value to identity)
            },
        )
    }

    @Test
    fun `findOrCreate should return the existing identity on a second presentation, without creating a duplicate`() {
        val first = useCase.findOrCreate("https://idp.test/", "operator-1", "Operator One")
            .fold({ error("first call failed: $it") }, { it })

        val second = useCase.findOrCreate("https://idp.test/", "operator-1", "Ignored On Reconnect")

        second.fold(
            { error("expected the existing identity, got $it") },
            { identity ->
                identity.id shouldBe first.id
                identity.displayName shouldBe "Operator One" // pas réécrit par la reconnexion
            },
        )
        repository.store.size shouldBe 1
    }

    @Test
    fun `findOrCreate should recover the existing identity when creation races into a duplicate key`() {
        // Simule la course : deux appels sur la même paire (issuer, subject) où le second se heurte
        // à l'unicité que IdentityFakeRepository fait respecter exactement comme
        // IdentityMongoRepository face à l'index unique de LOT0-03 (DuplicateKeyException →
        // "identity.duplicate.issuer.subject").
        val existing = useCase.findOrCreate("https://idp.test/", "operator-2", "Operator Two")
            .fold({ error("setup failed: $it") }, { it })

        val recovered = useCase.findOrCreate("https://idp.test/", "operator-2", "Ignored")

        recovered.fold(
            { error("expected recovery from the race, got $it") },
            { identity -> identity.id shouldBe existing.id },
        )
    }

    @Test
    fun `findOrCreate should propagate a technical error instead of masking it as a creation failure`() {
        val failingRepository = object : IdentityRepository {
            override fun save(identity: Identity) = repository.save(identity)
            override fun findById(id: IdentityId) = repository.findById(id)
            override fun findByIssuerAndSubject(issuer: String, subject: String): Either<UseCaseError, Identity> =
                Either.Left(TechnicalError("identity.lookup.failed"))
        }
        val useCaseWithFailingRepository = IdentityUseCase(failingRepository)

        val result = useCaseWithFailingRepository.findOrCreate("https://idp.test/", "operator-3", "Operator Three")

        result.fold(
            { error ->
                // `TechnicalError` EST un `DomainError` (backend.md hiérarchie) : on vérifie le
                // type précis, pas seulement "une erreur métier" — sinon ce test passerait même si
                // le use case masquait la panne technique en "identity.duplicate.issuer.subject".
                (error is TechnicalError) shouldBe true
                (error as TechnicalError).messageKey shouldBe "identity.lookup.failed"
            },
            { error("expected the technical error to propagate, got a success") },
        )
    }
}
