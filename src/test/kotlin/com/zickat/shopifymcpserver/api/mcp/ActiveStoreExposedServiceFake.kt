package com.zickat.shopifymcpserver.api.mcp

import com.zickat.shopifymcpserver.tenancy.exposed_interface.ActiveStoreExposedService

class ActiveStoreExposedServiceFake : ActiveStoreExposedService {
    private val selections = mutableMapOf<Pair<String, String>, String>()

    override fun activeStoreFor(identityId: String, sessionId: String): String? = selections[identityId to sessionId]

    override fun select(identityId: String, sessionId: String, storeId: String) {
        selections[identityId to sessionId] = storeId
    }
}
