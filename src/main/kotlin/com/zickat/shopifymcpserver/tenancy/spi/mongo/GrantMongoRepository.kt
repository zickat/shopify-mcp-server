package com.zickat.shopifymcpserver.tenancy.spi.mongo

import arrow.core.Either
import arrow.core.left
import arrow.core.raise.either
import com.zickat.shopifymcpserver.identity.exposed_interface.IdentityExposedService
import com.zickat.shopifymcpserver.shared_kernel.DomainError
import com.zickat.shopifymcpserver.shared_kernel.NotFoundError
import com.zickat.shopifymcpserver.shared_kernel.UseCaseError
import com.zickat.shopifymcpserver.shared_kernel.toObjectIdOrNull
import com.zickat.shopifymcpserver.tenancy.domain.models.Grant
import com.zickat.shopifymcpserver.tenancy.domain.models.GrantId
import com.zickat.shopifymcpserver.tenancy.domain.models.StoreId
import com.zickat.shopifymcpserver.tenancy.domain.repositories.GrantRepository
import com.zickat.shopifymcpserver.tenancy.domain.repositories.StoreRepository
import org.bson.types.ObjectId
import org.springframework.dao.DuplicateKeyException
import org.springframework.stereotype.Repository

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

    override fun findActiveByIdentityAndStore(identityId: String, storeId: StoreId): Either<UseCaseError, Grant> {
        val storeObjectId = storeId.value.toObjectIdOrNull() ?: return NotFoundError("grant.not.found").left()
        return springDataRepository.findActiveByIdentityIdAndStoreId(ObjectId(identityId), storeObjectId)
            ?.toDomain()
            ?: NotFoundError("grant.not.found").left()
    }

    override fun findAllActiveByIdentity(identityId: String): Either<UseCaseError, List<Grant>> = either {
        springDataRepository.findAllActiveByIdentityId(ObjectId(identityId)).map { it.toDomain().bind() }
    }
}
