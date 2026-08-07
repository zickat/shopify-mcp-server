package com.zickat.shopifymcpserver.tenancy

import com.zickat.shopifymcpserver.identity.IdentityExposedServiceFake
import com.zickat.shopifymcpserver.tenancy.domain.models.StoreId
import org.bson.types.ObjectId

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
}
