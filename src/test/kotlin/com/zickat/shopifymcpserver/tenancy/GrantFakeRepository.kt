package com.zickat.shopifymcpserver.tenancy

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import com.zickat.shopifymcpserver.identity.exposed_interface.IdentityExposedService
import com.zickat.shopifymcpserver.shared_kernel.DomainError
import com.zickat.shopifymcpserver.shared_kernel.NotFoundError
import com.zickat.shopifymcpserver.shared_kernel.UseCaseError
import com.zickat.shopifymcpserver.tenancy.domain.models.Grant
import com.zickat.shopifymcpserver.tenancy.domain.models.GrantId
import com.zickat.shopifymcpserver.tenancy.domain.models.StoreId
import com.zickat.shopifymcpserver.tenancy.domain.repositories.GrantRepository
import com.zickat.shopifymcpserver.tenancy.domain.repositories.StoreRepository

/** Même logique référentielle que `GrantMongoRepository`, en mémoire — patron `testing.md` (contract interface). */
class GrantFakeRepository(
    private val identityExposedService: IdentityExposedService,
    private val storeRepository: StoreRepository,
) : GrantRepository {
    val store = mutableMapOf<String, Grant>()

    override fun save(grant: Grant): Either<UseCaseError, Grant> {
        if (!identityExposedService.exists(grant.identityId)) {
            return NotFoundError("grant.identity.not.found").left()
        }
        if (!identityExposedService.exists(grant.grantedBy)) {
            return NotFoundError("grant.granted.by.not.found").left()
        }
        val storeEntity = storeRepository.findById(grant.storeId).fold({ null }, { it })
            ?: return NotFoundError("grant.store.not.found").left()
        if (storeEntity.isArchived) {
            return DomainError("grant.store.archived", mapOf("storeId" to grant.storeId.value)).left()
        }
        if (grant.revokedAt == null) {
            val duplicate = store.values.any {
                it.identityId == grant.identityId && it.storeId == grant.storeId && it.revokedAt == null && it.id != grant.id
            }
            if (duplicate) {
                return DomainError(
                    "grant.duplicate.active",
                    mapOf("identityId" to grant.identityId, "storeId" to grant.storeId.value),
                ).left()
            }
        }
        store[grant.id.value] = grant
        return grant.right()
    }

    override fun findById(id: GrantId): Either<UseCaseError, Grant> =
        store[id.value]?.right() ?: NotFoundError("grant.not.found").left()

    override fun findActiveByIdentityAndStore(identityId: String, storeId: StoreId): Either<UseCaseError, Grant> =
        store.values.find { it.identityId == identityId && it.storeId == storeId && it.revokedAt == null }
            ?.right()
            ?: NotFoundError("grant.not.found").left()
}
