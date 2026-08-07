package com.zickat.shopifymcpserver.identity.spi.mongo

import arrow.core.Either
import com.zickat.shopifymcpserver.identity.domain.IdentityUseCase
import com.zickat.shopifymcpserver.identity.domain.models.IdentityId
import com.zickat.shopifymcpserver.identity.domain.repositories.IdentityRepository
import com.zickat.shopifymcpserver.identity.exposed_interface.IdentityExposedService
import com.zickat.shopifymcpserver.shared_kernel.UseCaseError
import org.springframework.stereotype.Service

@Service
class IdentityExposedServiceImpl(
    private val identityRepository: IdentityRepository,
    private val identityUseCase: IdentityUseCase,
) : IdentityExposedService {

    override fun exists(identityId: String): Boolean =
        identityRepository.findById(IdentityId(identityId)).isRight()

    /**
     * `displayName` par défaut = `subject` : l'IdP (Q1, non tranchée) ne garantit aucun claim
     * `name`/`email` exploitable au lot 0, et `subject` est déjà documenté comme opaque, jamais
     * utilisé comme identifiant d'affichage réel ailleurs dans le domaine (voir `Identity`, KDoc).
     * Choix temporaire, à enrichir au lot 2 si l'IdP retenu fournit un claim de nom exploitable.
     */
    override fun resolve(issuer: String, subject: String): Either<UseCaseError, String> =
        identityUseCase.findOrCreate(issuer, subject, displayName = subject).map { it.id.value }
}
