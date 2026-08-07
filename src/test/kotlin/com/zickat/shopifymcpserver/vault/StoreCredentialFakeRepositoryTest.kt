package com.zickat.shopifymcpserver.vault

import com.zickat.shopifymcpserver.tenancy.StoreExposedServiceFake
import org.bson.types.ObjectId

class StoreCredentialFakeRepositoryTest : StoreCredentialRepositoryReferentialIntegrityTest {

    private val storeExposedService = StoreExposedServiceFake()
    override val repository = StoreCredentialFakeRepository(storeExposedService)

    override fun registerStore(archived: Boolean): String {
        val id = ObjectId().toHexString()
        storeExposedService.existing[id] = archived
        return id
    }
}
