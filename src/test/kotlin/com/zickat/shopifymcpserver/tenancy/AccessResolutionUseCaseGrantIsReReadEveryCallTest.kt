package com.zickat.shopifymcpserver.tenancy

import arrow.core.Either
import com.zickat.shopifymcpserver.identity.IdentityExposedServiceFake
import com.zickat.shopifymcpserver.identity.exposed_interface.IdentityExposedService
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

        override fun findAllActiveByIdentity(identityId: String): Either<UseCaseError, List<Grant>> =
            delegate.findAllActiveByIdentity(identityId)
    }

    private class InvocationCountingIdentityExposedService(private val delegate: IdentityExposedService) : IdentityExposedService {
        var isActiveInvocationCount = 0
            private set

        override fun exists(identityId: String): Boolean = delegate.exists(identityId)

        override fun isActive(identityId: String): Boolean {
            isActiveInvocationCount++
            return delegate.isActive(identityId)
        }

        override fun resolve(issuer: String, subject: String): Either<UseCaseError, String> = delegate.resolve(issuer, subject)
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

    @Test
    fun `resolve should hit the identity's active status on every single call — a revocation applies on the very next call, no cache may delay it`() {
        val delegateIdentityExposedService = IdentityExposedServiceFake()
        val countingIdentityExposedService = InvocationCountingIdentityExposedService(delegateIdentityExposedService)
        val storeRepositoryForThisTest = StoreFakeRepository()
        val grantRepositoryForThisTest = GrantFakeRepository(countingIdentityExposedService, storeRepositoryForThisTest)
        val useCaseWithCountingIdentityExposedService =
            AccessResolutionUseCase(countingIdentityExposedService, grantRepositoryForThisTest, storeRepositoryForThisTest)
        val store = StoreFixtures().build()
        storeRepositoryForThisTest.store[store.id.value] = store
        val identityId = delegateIdentityExposedService.resolve(issuer, subject).shouldBeRight()
        grantRepositoryForThisTest.save(
            GrantFixtures()
                .withIdentityId(identityId)
                .withStoreId(store.id)
                .withRole(GrantRole.VIEWER)
                .withGrantedBy(identityId)
                .build(),
        )

        val callCount = 5
        repeat(callCount) {
            useCaseWithCountingIdentityExposedService.resolve(issuer, subject, store.id.value).shouldBeRight()
        }

        countingIdentityExposedService.isActiveInvocationCount shouldBe callCount
    }
}
