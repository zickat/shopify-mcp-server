package com.zickat.shopifymcpserver.tenancy.exposed_interface

import arrow.core.Either
import com.zickat.shopifymcpserver.shared_kernel.TenantContext
import com.zickat.shopifymcpserver.shared_kernel.UseCaseError
import com.zickat.shopifymcpserver.shared_kernel.UserContext
import org.springframework.modulith.NamedInterface

/**
 * API publique du module `tenancy` vers les autres modules — `LOT0-08`, câblage bout en bout.
 *
 * Délègue intégralement à
 * [com.zickat.shopifymcpserver.tenancy.domain.AccessResolutionUseCase] — le **seul** point de
 * construction de [TenantContext]/[UserContext] du dépôt (voir sa KDoc,
 * `TenantUserContextConstructionTest`). Cette interface ne réimplémente rien : elle existe
 * uniquement parce que `AccessResolutionUseCase` vit dans `tenancy.domain`, un sous-package non
 * exposé par défaut (garde-fou Modulith vérifié empiriquement en `LOT0-02` — voir `progress.md`,
 * « Module 'audit' depends on non-exposed type… »), alors que le module `api` (`LOT0-08`) a besoin
 * d'appeler exactement ce use case pour câbler un outil MCP réel.
 *
 * Le type de retour reste sûr à la frontière : [TenantContext] et [UserContext] vivent tous les
 * deux dans `shared_kernel` (racine, exposée par défaut) — aucun type interne à `tenancy`
 * (`AccessContext`, `Grant`, `Store`…) ne traverse cette interface, même pas `AccessContext` qui se
 * contente pourtant de les regrouper.
 */
@NamedInterface("exposed_interface")
interface AccessExposedService {
    /** @return `(TenantContext, UserContext)` si un grant actif autorise cette identité sur cette boutique. */
    fun resolveAccess(issuer: String, subject: String, storeId: String): Either<UseCaseError, Pair<TenantContext, UserContext>>
}
