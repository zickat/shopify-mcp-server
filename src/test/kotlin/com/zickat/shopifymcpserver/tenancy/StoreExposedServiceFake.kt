package com.zickat.shopifymcpserver.tenancy

import com.zickat.shopifymcpserver.tenancy.exposed_interface.StoreExposedService

class StoreExposedServiceFake : StoreExposedService {
    val archivedByStoreId = mutableMapOf<String, Boolean>()

    override fun exists(storeId: String): Boolean = archivedByStoreId.containsKey(storeId)

    override fun existsAndNotArchived(storeId: String): Boolean = archivedByStoreId[storeId] == false
}
