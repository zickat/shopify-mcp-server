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
import kotlin.time.Clock
import org.bson.types.ObjectId
import org.springframework.stereotype.Component

/**
 * `LOT0-06`, schema.md §2 étape « identity WHERE issuer+subject » : retrouve l'identité d'un
 * principal validé par le resource server (`LOT0-05`), ou la crée si c'est sa première présentation
 * — le seul geste que cette classe fait au-delà d'une lecture, et il ne confère **aucun** accès :
 * `AccessResolutionUseCase` (module `tenancy`) refuse toujours une identité sans grant, qu'elle
 * soit neuve ou non (schema.md §5, « s'authentifier ne donne aucun accès »).
 */
@Component
class IdentityUseCase(
    private val identityRepository: IdentityRepository,
    private val clock: Clock = Clock.System,
) {
    /**
     * `displayName` n'est utilisé qu'à la création — une identité déjà connue garde le nom qu'elle
     * avait, il n'est jamais réécrit par une reconnexion.
     */
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
                    // Concurrence : deux requêtes simultanées ont découvert l'absence de l'identité
                    // avant que l'une des deux ne l'ait créée — l'index unique (issuer, subject),
                    // LOT0-03, fait échouer la seconde écriture. On relit au lieu de faire échouer
                    // une requête par ailleurs parfaitement légitime.
                    identityRepository.findByIssuerAndSubject(issuer, subject)
                } else {
                    error.left()
                }
            },
            ifRight = { it.right() },
        )

    private fun create(issuer: String, subject: String, displayName: String): Either<UseCaseError, Identity> =
        identityRepository.save(
            Identity(
                id = IdentityId(ObjectId().toHexString()),
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
