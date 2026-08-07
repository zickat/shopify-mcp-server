package com.zickat.shopifymcpserver.tenancy.exposed_interface

import org.springframework.modulith.NamedInterface

@NamedInterface("exposed_interface")
interface StoreExposedService {
    fun exists(storeId: String): Boolean
    fun existsAndNotArchived(storeId: String): Boolean
}
