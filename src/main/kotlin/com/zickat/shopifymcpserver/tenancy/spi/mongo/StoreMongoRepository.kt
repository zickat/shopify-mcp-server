package com.zickat.shopifymcpserver.tenancy.spi.mongo

import arrow.core.Either
import arrow.core.left
import com.zickat.shopifymcpserver.shared_kernel.DomainError
import com.zickat.shopifymcpserver.shared_kernel.NotFoundError
import com.zickat.shopifymcpserver.shared_kernel.UseCaseError
import com.zickat.shopifymcpserver.tenancy.domain.models.Store
import com.zickat.shopifymcpserver.tenancy.domain.models.StoreId
import com.zickat.shopifymcpserver.tenancy.domain.repositories.StoreRepository
import org.bson.types.ObjectId
import org.springframework.dao.DuplicateKeyException
import org.springframework.stereotype.Repository

@Repository
class StoreMongoRepository(
    private val springDataRepository: StoreSpringDataMongoRepository,
) : StoreRepository {

    override fun save(store: Store): Either<UseCaseError, Store> = try {
        springDataRepository.save(StoreEntity.fromDomain(store)).toDomain()
    } catch (e: DuplicateKeyException) {
        DomainError("store.duplicate.slug", mapOf("slug" to store.slug)).left()
    }

    override fun findById(id: StoreId): Either<UseCaseError, Store> =
        springDataRepository.findById(ObjectId(id.value))
            .map { it.toDomain() }
            .orElse(NotFoundError("store.not.found").left())

    override fun findBySlug(slug: String): Either<UseCaseError, Store> =
        springDataRepository.findBySlug(slug)?.toDomain() ?: NotFoundError("store.not.found").left()
}
