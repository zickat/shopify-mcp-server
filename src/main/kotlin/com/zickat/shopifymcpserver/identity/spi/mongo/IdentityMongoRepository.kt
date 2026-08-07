package com.zickat.shopifymcpserver.identity.spi.mongo

import arrow.core.Either
import arrow.core.left
import com.zickat.shopifymcpserver.identity.domain.models.Identity
import com.zickat.shopifymcpserver.identity.domain.models.IdentityId
import com.zickat.shopifymcpserver.identity.domain.repositories.IdentityRepository
import com.zickat.shopifymcpserver.shared_kernel.DomainError
import com.zickat.shopifymcpserver.shared_kernel.NotFoundError
import com.zickat.shopifymcpserver.shared_kernel.UseCaseError
import org.bson.types.ObjectId
import org.springframework.dao.DuplicateKeyException
import org.springframework.stereotype.Repository

@Repository
class IdentityMongoRepository(
    private val springDataRepository: IdentitySpringDataMongoRepository,
) : IdentityRepository {

    override fun save(identity: Identity): Either<UseCaseError, Identity> = try {
        springDataRepository.save(IdentityEntity.fromDomain(identity)).toDomain()
    } catch (e: DuplicateKeyException) {
        DomainError(
            "identity.duplicate.issuer.subject",
            mapOf("issuer" to identity.issuer, "subject" to identity.subject),
        ).left()
    }

    override fun findById(id: IdentityId): Either<UseCaseError, Identity> =
        springDataRepository.findById(ObjectId(id.value))
            .map { it.toDomain() }
            .orElse(NotFoundError("identity.not.found").left())

    override fun findByIssuerAndSubject(issuer: String, subject: String): Either<UseCaseError, Identity> =
        springDataRepository.findByIssuerAndSubject(issuer, subject)
            ?.toDomain()
            ?: NotFoundError("identity.not.found").left()
}
