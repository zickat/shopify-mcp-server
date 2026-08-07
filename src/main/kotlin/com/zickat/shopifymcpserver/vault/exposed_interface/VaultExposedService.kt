package com.zickat.shopifymcpserver.vault.exposed_interface

import org.springframework.modulith.NamedInterface

@NamedInterface("exposed_interface")
interface VaultExposedService {
    fun hasActiveCredential(storeId: String): Boolean
}
