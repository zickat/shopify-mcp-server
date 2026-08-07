package com.zickat.shopifymcpserver.vault.domain.repositories

import arrow.core.Either
import com.zickat.shopifymcpserver.shared_kernel.UseCaseError

const val ACTIVE_MASTER_KEY_REF = "v1"

interface MasterKeyProvider {
    fun resolve(keyRef: String): Either<UseCaseError, ByteArray>
}
