package com.zickat.shopifymcpserver.vault.exposed_interface

import arrow.core.Either
import com.zickat.shopifymcpserver.shared_kernel.UseCaseError
import org.springframework.modulith.NamedInterface

@NamedInterface("exposed_interface")
interface VaultExposedService {
    fun hasActiveCredential(storeId: String): Boolean
    fun resolveCredential(storeId: String): Either<UseCaseError, ByteArray>
}
