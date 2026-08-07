package com.zickat.shopifymcpserver.tenancy

import com.zickat.shopifymcpserver.identity.IdentityExposedServiceFake
import com.zickat.shopifymcpserver.tenancy.domain.models.StoreId
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.matchers.shouldBe
import org.bson.types.ObjectId
import org.junit.jupiter.api.Test

class GrantFakeRepositoryTest : GrantRepositoryReferentialIntegrityTest {

    private val identityExposedService = IdentityExposedServiceFake()
    private val storeRepository = StoreFakeRepository()
    override val repository = GrantFakeRepository(identityExposedService, storeRepository)

    override fun registerExistingIdentity(): String {
        val id = ObjectId().toHexString()
        identityExposedService.existingIds.add(id)
        return id
    }

    override fun registerStore(archived: Boolean): StoreId {
        val store = StoreFixtures().let { if (archived) it.archived() else it }.build()
        storeRepository.store[store.id.value] = store
        return store.id
    }

    @Test
    fun `findAllActiveByIdentity should return an empty list, not an error, for an identity with no grant`() {
        val identityId = registerExistingIdentity()

        repository.findAllActiveByIdentity(identityId).shouldBeRight() shouldBe emptyList()
    }

    @Test
    fun `findAllActiveByIdentity should return every active grant of the identity and exclude revoked ones`() {
        val identityId = registerExistingIdentity()
        val velotrip = registerStore(archived = false)
        val lurelab = registerStore(archived = false)
        val active = GrantFixtures().withIdentityId(identityId).withStoreId(velotrip).withGrantedBy(identityId).build()
        val revoked = GrantFixtures().withIdentityId(identityId).withStoreId(lurelab).withGrantedBy(identityId).revoked().build()
        repository.save(active).isRight() shouldBe true
        repository.save(revoked).isRight() shouldBe true

        val result = repository.findAllActiveByIdentity(identityId).shouldBeRight()

        result.map { it.storeId } shouldBe listOf(velotrip)
    }
}
