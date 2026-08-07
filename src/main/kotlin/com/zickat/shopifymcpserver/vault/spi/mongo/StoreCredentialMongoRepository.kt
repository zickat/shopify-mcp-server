package com.zickat.shopifymcpserver.vault.spi.mongo

import arrow.core.Either
import arrow.core.left
import com.zickat.shopifymcpserver.shared_kernel.DomainError
import com.zickat.shopifymcpserver.shared_kernel.NotFoundError
import com.zickat.shopifymcpserver.shared_kernel.UseCaseError
import com.zickat.shopifymcpserver.tenancy.exposed_interface.StoreExposedService
import com.zickat.shopifymcpserver.vault.domain.models.StoreCredential
import com.zickat.shopifymcpserver.vault.domain.models.StoreCredentialId
import com.zickat.shopifymcpserver.vault.domain.repositories.StoreCredentialRepository
import org.bson.types.ObjectId
import org.springframework.dao.DuplicateKeyException
import org.springframework.stereotype.Repository

/** `schema.md` §3 — `storeCredential → store` : intégrité référentielle inter-modules via l'`exposed_interface` de `tenancy`, jamais un import direct. */
@Repository
class StoreCredentialMongoRepository(
    private val springDataRepository: StoreCredentialSpringDataMongoRepository,
    private val storeExposedService: StoreExposedService,
) : StoreCredentialRepository {

    override fun save(credential: StoreCredential): Either<UseCaseError, StoreCredential> {
        if (!storeExposedService.existsAndNotArchived(credential.storeId)) {
            return NotFoundError("storeCredential.store.not.found").left()
        }
        return try {
            springDataRepository.save(StoreCredentialEntity.fromDomain(credential)).toDomain()
        } catch (e: DuplicateKeyException) {
            DomainError("storeCredential.duplicate.active", mapOf("storeId" to credential.storeId)).left()
        }
    }

    override fun findById(id: StoreCredentialId): Either<UseCaseError, StoreCredential> =
        springDataRepository.findById(ObjectId(id.value))
            .map { it.toDomain() }
            .orElse(NotFoundError("storeCredential.not.found").left())

    override fun findActiveByStore(storeId: String): Either<UseCaseError, StoreCredential> =
        springDataRepository.findActiveByStoreId(ObjectId(storeId))?.toDomain()
            ?: NotFoundError("storeCredential.not.found").left()
}
