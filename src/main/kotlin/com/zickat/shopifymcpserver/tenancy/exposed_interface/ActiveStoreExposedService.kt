package com.zickat.shopifymcpserver.tenancy.exposed_interface

import org.springframework.modulith.NamedInterface

@NamedInterface("exposed_interface")
interface ActiveStoreExposedService {
    fun activeStoreFor(identityId: String, sessionId: String): String?
    fun select(identityId: String, sessionId: String, storeId: String)
}
