package com.zickat.shopifymcpserver.vault

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import com.zickat.shopifymcpserver.shared_kernel.NotFoundError
import com.zickat.shopifymcpserver.shared_kernel.UseCaseError
import com.zickat.shopifymcpserver.vault.exposed_interface.VaultExposedService

class VaultExposedServiceFake : VaultExposedService {
    val plaintextByStoreId = mutableMapOf<String, ByteArray>()

    override fun hasActiveCredential(storeId: String): Boolean = storeId in plaintextByStoreId

    override fun resolveCredential(storeId: String): Either<UseCaseError, ByteArray> =
        plaintextByStoreId[storeId]?.right() ?: NotFoundError("storeCredential.not.found").left()
}
