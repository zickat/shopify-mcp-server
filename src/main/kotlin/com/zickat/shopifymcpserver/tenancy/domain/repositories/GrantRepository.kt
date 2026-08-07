package com.zickat.shopifymcpserver.tenancy.domain.repositories

import arrow.core.Either
import com.zickat.shopifymcpserver.shared_kernel.UseCaseError
import com.zickat.shopifymcpserver.tenancy.domain.models.Grant
import com.zickat.shopifymcpserver.tenancy.domain.models.GrantId
import com.zickat.shopifymcpserver.tenancy.domain.models.StoreId

interface GrantRepository {
    fun save(grant: Grant): Either<UseCaseError, Grant>
    fun findById(id: GrantId): Either<UseCaseError, Grant>
    fun findActiveByIdentityAndStore(identityId: String, storeId: StoreId): Either<UseCaseError, Grant>
}
