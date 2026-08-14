package com.zickat.shopifymcpserver.identity.domain

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import com.zickat.shopifymcpserver.identity.domain.models.Identity
import com.zickat.shopifymcpserver.identity.domain.models.IdentityId
import com.zickat.shopifymcpserver.identity.domain.repositories.IdentityRepository
import com.zickat.shopifymcpserver.shared_kernel.DomainError
import com.zickat.shopifymcpserver.shared_kernel.NotFoundError
import com.zickat.shopifymcpserver.shared_kernel.UseCaseError
import com.zickat.shopifymcpserver.shared_kernel.newObjectIdHex
import kotlin.time.Clock

class IdentityUseCase(
    private val identityRepository: IdentityRepository,
    private val clock: Clock = Clock.System,
) {
    fun findOrCreate(issuer: String, subject: String, displayName: String): Either<UseCaseError, Identity> =
        identityRepository.findByIssuerAndSubject(issuer, subject).fold(
            ifLeft = { error ->
                if (error is NotFoundError) createOrRecoverFromRace(issuer, subject, displayName) else error.left()
            },
            ifRight = { it.right() },
        )

    private fun createOrRecoverFromRace(issuer: String, subject: String, displayName: String): Either<UseCaseError, Identity> =
        create(issuer, subject, displayName).fold(
            ifLeft = { error ->
                if (error is DomainError && error.messageKey == DUPLICATE_ISSUER_SUBJECT) {
                    recoverFromConcurrentDuplicateCreation(issuer, subject)
                } else {
                    error.left()
                }
            },
            ifRight = { it.right() },
        )

    private fun recoverFromConcurrentDuplicateCreation(issuer: String, subject: String): Either<UseCaseError, Identity> =
        identityRepository.findByIssuerAndSubject(issuer, subject)

    private fun create(issuer: String, subject: String, displayName: String): Either<UseCaseError, Identity> =
        identityRepository.save(
            Identity(
                id = IdentityId(newObjectIdHex()),
                issuer = issuer,
                subject = subject,
                displayName = displayName,
                createdAt = clock.now(),
                revokedAt = null,
            ),
        )

    companion object {
        const val DUPLICATE_ISSUER_SUBJECT = "identity.duplicate.issuer.subject"
    }
}
