package com.zickat.shopifymcpserver.tenancy

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import com.zickat.shopifymcpserver.shared_kernel.DomainError
import com.zickat.shopifymcpserver.shared_kernel.NotFoundError
import com.zickat.shopifymcpserver.shared_kernel.UseCaseError
import com.zickat.shopifymcpserver.tenancy.domain.models.Store
import com.zickat.shopifymcpserver.tenancy.domain.models.StoreId
import com.zickat.shopifymcpserver.tenancy.domain.repositories.StoreRepository

class StoreFakeRepository : StoreRepository {
    val store = mutableMapOf<String, Store>()

    override fun save(store: Store): Either<UseCaseError, Store> {
        val duplicate = this.store.values.any { it.slug == store.slug && it.id != store.id }
        if (duplicate) return DomainError("store.duplicate.slug").left()
        this.store[store.id.value] = store
        return store.right()
    }

    override fun findById(id: StoreId): Either<UseCaseError, Store> =
        store[id.value]?.right() ?: NotFoundError("store.not.found").left()

    override fun findBySlug(slug: String): Either<UseCaseError, Store> =
        store.values.find { it.slug == slug }?.right() ?: NotFoundError("store.not.found").left()
}
