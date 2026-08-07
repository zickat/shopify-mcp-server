package com.zickat.shopifymcpserver.tenancy.spi.mongo

import arrow.core.Either
import arrow.core.left
import com.zickat.shopifymcpserver.identity.exposed_interface.IdentityExposedService
import com.zickat.shopifymcpserver.shared_kernel.DomainError
import com.zickat.shopifymcpserver.shared_kernel.NotFoundError
import com.zickat.shopifymcpserver.shared_kernel.UseCaseError
import com.zickat.shopifymcpserver.tenancy.domain.models.Grant
import com.zickat.shopifymcpserver.tenancy.domain.models.GrantId
import com.zickat.shopifymcpserver.tenancy.domain.models.StoreId
import com.zickat.shopifymcpserver.tenancy.domain.repositories.GrantRepository
import com.zickat.shopifymcpserver.tenancy.domain.repositories.StoreRepository
import org.bson.types.ObjectId
import org.springframework.dao.DuplicateKeyException
import org.springframework.stereotype.Repository

/**
 * `schema.md` §3 — intégrité référentielle (« invariant applicatif, donc un test ») :
 * `grant → identity` et `grant → grantedBy` sont vérifiés via l'`exposed_interface` du module
 * `identity` (jamais un import direct de son repository/entité — frontière Modulith).
 * `grant → store` est intra-module (`Store` vit dans `tenancy` aussi) : accès direct au
 * repository, ce n'est pas une frontière. Une boutique **archivée** est refusée, pas seulement
 * une boutique inexistante — c'est le cas explicitement nommé par la tâche.
 */
@Repository
class GrantMongoRepository(
    private val springDataRepository: GrantSpringDataMongoRepository,
    private val storeRepository: StoreRepository,
    private val identityExposedService: IdentityExposedService,
) : GrantRepository {

    override fun save(grant: Grant): Either<UseCaseError, Grant> {
        if (!identityExposedService.exists(grant.identityId)) {
            return NotFoundError("grant.identity.not.found").left()
        }
        if (!identityExposedService.exists(grant.grantedBy)) {
            return NotFoundError("grant.granted.by.not.found").left()
        }
        val store = storeRepository.findById(grant.storeId).fold({ null }, { it })
            ?: return NotFoundError("grant.store.not.found").left()
        if (store.isArchived) {
            return DomainError("grant.store.archived", mapOf("storeId" to grant.storeId.value)).left()
        }

        return try {
            springDataRepository.save(GrantEntity.fromDomain(grant)).toDomain()
        } catch (e: DuplicateKeyException) {
            DomainError(
                "grant.duplicate.active",
                mapOf("identityId" to grant.identityId, "storeId" to grant.storeId.value),
            ).left()
        }
    }

    override fun findById(id: GrantId): Either<UseCaseError, Grant> =
        springDataRepository.findById(ObjectId(id.value))
            .map { it.toDomain() }
            .orElse(NotFoundError("grant.not.found").left())

    override fun findActiveByIdentityAndStore(identityId: String, storeId: StoreId): Either<UseCaseError, Grant> =
        springDataRepository.findActiveByIdentityIdAndStoreId(ObjectId(identityId), ObjectId(storeId.value))
            ?.toDomain()
            ?: NotFoundError("grant.not.found").left()
}
