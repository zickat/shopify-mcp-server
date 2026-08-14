package com.zickat.shopifymcpserver.tenancy.exposed_interface

import com.zickat.shopifymcpserver.tenancy.domain.ActiveStoreSelectionRegistry
import com.zickat.shopifymcpserver.tenancy.domain.models.StoreId
import org.springframework.stereotype.Service

@Service
class ActiveStoreExposedServiceImpl(
    private val registry: ActiveStoreSelectionRegistry,
) : ActiveStoreExposedService {

    override fun activeStoreFor(identityId: String, sessionId: String): String? =
        registry.activeStoreFor(identityId, sessionId)?.value

    override fun select(identityId: String, sessionId: String, storeId: String) {
        registry.select(identityId, sessionId, StoreId(storeId))
    }
}
