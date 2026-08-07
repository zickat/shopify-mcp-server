package com.zickat.shopifymcpserver.vault.domain.repositories

import arrow.core.Either
import com.zickat.shopifymcpserver.shared_kernel.UseCaseError
import com.zickat.shopifymcpserver.vault.domain.models.StoreCredential
import com.zickat.shopifymcpserver.vault.domain.models.StoreCredentialId

interface StoreCredentialRepository {
    fun save(credential: StoreCredential): Either<UseCaseError, StoreCredential>
    fun findById(id: StoreCredentialId): Either<UseCaseError, StoreCredential>
    fun findActiveByStore(storeId: String): Either<UseCaseError, StoreCredential>
}
