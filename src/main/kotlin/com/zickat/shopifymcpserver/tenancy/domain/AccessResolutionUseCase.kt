package com.zickat.shopifymcpserver.tenancy.domain

import arrow.core.Either
import arrow.core.raise.either
import com.zickat.shopifymcpserver.identity.exposed_interface.IdentityExposedService
import com.zickat.shopifymcpserver.shared_kernel.ForbiddenError
import com.zickat.shopifymcpserver.shared_kernel.NotFoundError
import com.zickat.shopifymcpserver.shared_kernel.TenantContext
import com.zickat.shopifymcpserver.shared_kernel.UseCaseError
import com.zickat.shopifymcpserver.shared_kernel.UserContext
import com.zickat.shopifymcpserver.tenancy.domain.models.AccessContext
import com.zickat.shopifymcpserver.tenancy.domain.models.StoreId
import com.zickat.shopifymcpserver.tenancy.domain.models.toAccessRole
import com.zickat.shopifymcpserver.tenancy.domain.repositories.GrantRepository
import com.zickat.shopifymcpserver.tenancy.domain.repositories.StoreRepository
import org.springframework.stereotype.Component

/**
 * Le SEUL point de construction de [TenantContext]/[UserContext] dans tout le dépôt — schema.md §2
 * invariant 1, backend.md §Multi-tenancy (« jamais accepté en paramètre HTTP »). Vérifié
 * structurellement par `TenantUserContextConstructionTest` (`shared_kernel`) : un futur
 * développeur qui construirait l'un ou l'autre ailleurs (par ex. depuis un `storeId` lu dans le
 * corps JSON d'un appel d'outil) ferait échouer ce test avant même d'écrire un test métier.
 *
 * **Refus par défaut** — schema.md §5 : « un compte valide chez l'IdP et zéro grant = zéro
 * boutique visible », hérité de `store-context.ts` (« la meilleure idée du code actuel »,
 * `architecture.md`). Trois causes de refus, **toutes rendues indistinguables côté client** (même
 * `messageKey` `access.denied`, même paramètre `storeId`) pour ne jamais laisser un appelant
 * déduire par sondage si une boutique existe : boutique absente, boutique archivée, aucun grant
 * actif. Seul l'`auditLog` (lot 2) porte la distinction en clair, jamais la réponse HTTP.
 *
 * **Lu sans cache, à chaque appel** — schema.md §5 : « effet immédiat [de la révocation] ». Ni
 * `grantRepository.findActiveByIdentityAndStore` ni `storeRepository.findById` ne sont mis en
 * cache ici : un grant révoqué ou une boutique archivée entre deux appels fait échouer le second
 * immédiatement. **Ne jamais ajouter de cache à cette classe sans remonter au Tech Lead** —
 * explicitement proscrit par `LOT0-06.md`.
 */
@Component
class AccessResolutionUseCase(
    private val identityExposedService: IdentityExposedService,
    private val grantRepository: GrantRepository,
    private val storeRepository: StoreRepository,
) {
    fun resolve(issuer: String, subject: String, storeId: String): Either<UseCaseError, AccessContext> = either {
        val identityId = identityExposedService.resolve(issuer, subject).bind()

        val store = storeRepository.findById(StoreId(storeId)).orNullIfNotFound().bind()
        if (store == null || store.isArchived) {
            raise(accessDenied(storeId))
        }

        val grant = grantRepository.findActiveByIdentityAndStore(identityId, StoreId(storeId)).orNullIfNotFound().bind()
        if (grant == null) {
            raise(accessDenied(storeId))
        }

        AccessContext(
            tenant = TenantContext(storeId = storeId),
            user = UserContext(identityId = identityId, role = grant.role.toAccessRole()),
        )
    }

    private fun accessDenied(storeId: String) = ForbiddenError("access.denied", mapOf("storeId" to storeId))

    /**
     * `NotFoundError` (boutique/grant inexistant) devient `null` — un cas métier normal ici, pas
     * une erreur technique. Toute autre erreur (ex. panne Mongo) continue de se propager telle
     * quelle : un problème technique ne doit jamais se déguiser en 403.
     */
    private fun <T> Either<UseCaseError, T>.orNullIfNotFound(): Either<UseCaseError, T?> =
        fold(
            ifLeft = { error -> if (error is NotFoundError) Either.Right(null) else Either.Left(error) },
            ifRight = { Either.Right(it) },
        )
}
