package com.zickat.shopifymcpserver.audit

import com.zickat.shopifymcpserver.identity.IdentityExposedServiceFake
import com.zickat.shopifymcpserver.tenancy.StoreExposedServiceFake
import org.bson.types.ObjectId

class AuditLogFakeRepositoryTest : AuditLogRepositoryReferentialIntegrityTest {

    private val identityExposedService = IdentityExposedServiceFake()
    private val storeExposedService = StoreExposedServiceFake()
    override val repository = AuditLogFakeRepository(identityExposedService, storeExposedService)

    override fun registerExistingIdentity(): String {
        val id = ObjectId().toHexString()
        identityExposedService.existingIds.add(id)
        return id
    }

    override fun registerExistingStore(): String {
        val id = ObjectId().toHexString()
        storeExposedService.existing[id] = false
        return id
    }
}
