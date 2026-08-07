package com.zickat.shopifymcpserver.tenancy

import com.zickat.shopifymcpserver.tenancy.exposed_interface.StoreExposedService

class StoreExposedServiceFake : StoreExposedService {
    /** id -> archivé ? */
    val existing = mutableMapOf<String, Boolean>()

    override fun exists(storeId: String): Boolean = existing.containsKey(storeId)

    override fun existsAndNotArchived(storeId: String): Boolean = existing[storeId] == false
}
