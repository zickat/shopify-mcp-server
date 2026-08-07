package com.zickat.shopifymcpserver.identity

import com.zickat.shopifymcpserver.identity.exposed_interface.IdentityExposedService

class IdentityExposedServiceFake : IdentityExposedService {
    val existingIds = mutableSetOf<String>()

    override fun exists(identityId: String): Boolean = identityId in existingIds
}
