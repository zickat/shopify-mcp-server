package com.zickat.shopifymcpserver.identity

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import com.zickat.shopifymcpserver.identity.domain.models.Identity
import com.zickat.shopifymcpserver.identity.domain.models.IdentityId
import com.zickat.shopifymcpserver.identity.domain.repositories.IdentityRepository
import com.zickat.shopifymcpserver.shared_kernel.DomainError
import com.zickat.shopifymcpserver.shared_kernel.NotFoundError
import com.zickat.shopifymcpserver.shared_kernel.UseCaseError

class IdentityFakeRepository : IdentityRepository {
    val store = mutableMapOf<String, Identity>()

    override fun save(identity: Identity): Either<UseCaseError, Identity> {
        val duplicate = store.values.any {
            it.issuer == identity.issuer && it.subject == identity.subject && it.id != identity.id
        }
        if (duplicate) return DomainError("identity.duplicate.issuer.subject").left()
        store[identity.id.value] = identity
        return identity.right()
    }

    override fun findById(id: IdentityId): Either<UseCaseError, Identity> =
        store[id.value]?.right() ?: NotFoundError("identity.not.found").left()

    override fun findByIssuerAndSubject(issuer: String, subject: String): Either<UseCaseError, Identity> =
        store.values.find { it.issuer == issuer && it.subject == subject }?.right()
            ?: NotFoundError("identity.not.found").left()
}
