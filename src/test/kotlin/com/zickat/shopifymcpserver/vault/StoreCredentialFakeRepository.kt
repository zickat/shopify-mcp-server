package com.zickat.shopifymcpserver.vault

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import com.zickat.shopifymcpserver.shared_kernel.DomainError
import com.zickat.shopifymcpserver.shared_kernel.NotFoundError
import com.zickat.shopifymcpserver.shared_kernel.UseCaseError
import com.zickat.shopifymcpserver.tenancy.exposed_interface.StoreExposedService
import com.zickat.shopifymcpserver.vault.domain.models.StoreCredential
import com.zickat.shopifymcpserver.vault.domain.models.StoreCredentialId
import com.zickat.shopifymcpserver.vault.domain.repositories.StoreCredentialRepository

class StoreCredentialFakeRepository(
    private val storeExposedService: StoreExposedService,
) : StoreCredentialRepository {
    val store = mutableMapOf<String, StoreCredential>()

    override fun save(credential: StoreCredential): Either<UseCaseError, StoreCredential> {
        if (!storeExposedService.existsAndNotArchived(credential.storeId)) {
            return NotFoundError("storeCredential.store.not.found").left()
        }
        if (credential.revokedAt == null) {
            val duplicate = store.values.any {
                it.storeId == credential.storeId && it.revokedAt == null && it.id != credential.id
            }
            if (duplicate) {
                return DomainError("storeCredential.duplicate.active", mapOf("storeId" to credential.storeId)).left()
            }
        }
        store[credential.id.value] = credential
        return credential.right()
    }

    override fun findById(id: StoreCredentialId): Either<UseCaseError, StoreCredential> =
        store[id.value]?.right() ?: NotFoundError("storeCredential.not.found").left()

    override fun findActiveByStore(storeId: String): Either<UseCaseError, StoreCredential> =
        store.values.find { it.storeId == storeId && it.revokedAt == null }?.right()
            ?: NotFoundError("storeCredential.not.found").left()
}
