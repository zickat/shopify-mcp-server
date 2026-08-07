package com.zickat.shopifymcpserver.vault.domain.repositories

import arrow.core.Either
import com.zickat.shopifymcpserver.shared_kernel.UseCaseError
import com.zickat.shopifymcpserver.vault.domain.models.StoreCredential
import com.zickat.shopifymcpserver.vault.domain.models.StoreCredentialId

interface StoreCredentialRepository {
    /** Doit échouer si `storeId` référence une boutique inexistante ou archivée (`schema.md` §3). */
    fun save(credential: StoreCredential): Either<UseCaseError, StoreCredential>
    fun findById(id: StoreCredentialId): Either<UseCaseError, StoreCredential>
    fun findActiveByStore(storeId: String): Either<UseCaseError, StoreCredential>
}
