package com.zickat.shopifymcpserver.audit

import com.zickat.shopifymcpserver.identity.IdentityExposedServiceFake
import org.bson.types.ObjectId

class AuditLogFakeRepositoryTest : AuditLogRepositoryReferentialIntegrityTest {

    private val identityExposedService = IdentityExposedServiceFake()
    override val repository = AuditLogFakeRepository(identityExposedService)

    override fun registerExistingIdentity(): String {
        val id = ObjectId().toHexString()
        identityExposedService.existingIds.add(id)
        return id
    }

    override fun registerExistingStore(): String = ObjectId().toHexString()
}
