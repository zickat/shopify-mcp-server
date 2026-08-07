package com.zickat.shopifymcpserver.identity.exposed_interface

import arrow.core.Either
import com.zickat.shopifymcpserver.shared_kernel.UseCaseError
import org.springframework.modulith.NamedInterface

/**
 * API publique du module `identity` vers les autres modules — `backend.md` : « deux modules ne
 * communiquent que via `exposed_interface/` ». `@NamedInterface` rend ce type visible en dehors du
 * module (vérifié empiriquement, voir `progress.md` LOT0-03 : Modulith n'expose par défaut que le
 * package racine d'un module, un sous-package nommé `exposed_interface` n'a, seul, aucun statut
 * particulier pour l'outil — c'est cette annotation qui fait le travail).
 *
 * Utilisé pour l'intégrité référentielle inter-modules (`grant → identity`, `grant → grantedBy`,
 * `auditLog → identity`, `schema.md` §3) : `tenancy` et `audit` vérifient l'existence d'une
 * identité sans jamais importer son repository ou son entité Mongo.
 */
@NamedInterface("exposed_interface")
interface IdentityExposedService {
    /** Vrai si une identité avec cet id existe (peu importe son statut actif/révoqué). */
    fun exists(identityId: String): Boolean

    /**
     * Retrouve l'identité `(issuer, subject)` validée par le resource server (`LOT0-05`), ou la
     * crée si c'est sa première présentation — schema.md §2, `LOT0-06`. Retourne l'`identityId`
     * (`String`, même convention que `Grant.identityId`) : `tenancy` n'a besoin que de cet id,
     * jamais du type `Identity` ni `IdentityId`, réservés au module `identity`.
     */
    fun resolve(issuer: String, subject: String): Either<UseCaseError, String>
}
