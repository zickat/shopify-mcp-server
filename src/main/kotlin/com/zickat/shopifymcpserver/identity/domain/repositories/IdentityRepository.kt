package com.zickat.shopifymcpserver.identity.domain.repositories

import arrow.core.Either
import com.zickat.shopifymcpserver.identity.domain.models.Identity
import com.zickat.shopifymcpserver.identity.domain.models.IdentityId
import com.zickat.shopifymcpserver.shared_kernel.UseCaseError

interface IdentityRepository {
    fun save(identity: Identity): Either<UseCaseError, Identity>
    fun findById(id: IdentityId): Either<UseCaseError, Identity>
    fun findByIssuerAndSubject(issuer: String, subject: String): Either<UseCaseError, Identity>
}
