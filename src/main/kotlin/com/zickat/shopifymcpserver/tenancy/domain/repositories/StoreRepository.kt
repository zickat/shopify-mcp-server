package com.zickat.shopifymcpserver.tenancy.domain.repositories

import arrow.core.Either
import com.zickat.shopifymcpserver.shared_kernel.UseCaseError
import com.zickat.shopifymcpserver.tenancy.domain.models.Store
import com.zickat.shopifymcpserver.tenancy.domain.models.StoreId

interface StoreRepository {
    fun save(store: Store): Either<UseCaseError, Store>
    fun findById(id: StoreId): Either<UseCaseError, Store>
    fun findBySlug(slug: String): Either<UseCaseError, Store>
}
