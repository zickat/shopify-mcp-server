package com.zickat.shopifymcpserver.tenancy

import arrow.core.Either
import com.zickat.shopifymcpserver.identity.IdentityExposedServiceFake
import com.zickat.shopifymcpserver.shared_kernel.UseCaseError
import com.zickat.shopifymcpserver.tenancy.domain.AccessResolutionUseCase
import com.zickat.shopifymcpserver.tenancy.domain.models.Grant
import com.zickat.shopifymcpserver.tenancy.domain.models.GrantId
import com.zickat.shopifymcpserver.tenancy.domain.models.GrantRole
import com.zickat.shopifymcpserver.tenancy.domain.models.StoreId
import com.zickat.shopifymcpserver.tenancy.domain.repositories.GrantRepository
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class AccessResolutionUseCaseGrantIsReReadEveryCallTest {

    private class InvocationCountingGrantRepository(private val delegate: GrantRepository) : GrantRepository {
        var findActiveByIdentityAndStoreInvocationCount = 0
            private set

        override fun save(grant: Grant): Either<UseCaseError, Grant> = delegate.save(grant)

        override fun findById(id: GrantId): Either<UseCaseError, Grant> = delegate.findById(id)

        override fun findActiveByIdentityAndStore(identityId: String, storeId: StoreId): Either<UseCaseError, Grant> {
            findActiveByIdentityAndStoreInvocationCount++
            return delegate.findActiveByIdentityAndStore(identityId, storeId)
        }
    }

    private val identityExposedService = IdentityExposedServiceFake()
    private val storeRepository = StoreFakeRepository()
    private val delegateGrantRepository = GrantFakeRepository(identityExposedService, storeRepository)
    private val countingGrantRepository = InvocationCountingGrantRepository(delegateGrantRepository)
    private val useCase = AccessResolutionUseCase(identityExposedService, countingGrantRepository, storeRepository)

    private val issuer = "https://idp.test/"
    private val subject = "operator-1"

    @Test
    fun `resolve should hit the grant repository on every single call — no caching layer may ever intercept it`() {
        val store = StoreFixtures().build()
        storeRepository.store[store.id.value] = store
        val identityId = identityExposedService.resolve(issuer, subject).shouldBeRight()
        delegateGrantRepository.save(
            GrantFixtures()
                .withIdentityId(identityId)
                .withStoreId(store.id)
                .withRole(GrantRole.VIEWER)
                .withGrantedBy(identityId)
                .build(),
        )

        val callCount = 5
        repeat(callCount) {
            useCase.resolve(issuer, subject, store.id.value).shouldBeRight()
        }

        countingGrantRepository.findActiveByIdentityAndStoreInvocationCount shouldBe callCount
    }
}
