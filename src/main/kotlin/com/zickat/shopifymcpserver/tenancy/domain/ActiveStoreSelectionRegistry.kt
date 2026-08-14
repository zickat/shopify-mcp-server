package com.zickat.shopifymcpserver.tenancy.domain

import com.zickat.shopifymcpserver.tenancy.domain.models.StoreId
import java.util.concurrent.ConcurrentHashMap

class ActiveStoreSelectionRegistry {

    private data class SessionKey(val identityId: String, val sessionId: String)

    private val selections = ConcurrentHashMap<SessionKey, StoreId>()

    fun activeStoreFor(identityId: String, sessionId: String): StoreId? =
        selections[SessionKey(identityId, sessionId)]

    fun select(identityId: String, sessionId: String, storeId: StoreId) {
        selections[SessionKey(identityId, sessionId)] = storeId
    }
}
