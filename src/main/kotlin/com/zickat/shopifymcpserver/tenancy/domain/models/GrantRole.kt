package com.zickat.shopifymcpserver.tenancy.domain.models

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import com.zickat.shopifymcpserver.shared_kernel.AccessRole
import com.zickat.shopifymcpserver.shared_kernel.DomainError
import com.zickat.shopifymcpserver.shared_kernel.UseCaseError

/**
 * `schema.md` §3 : `enum ['viewer','operator']`, vérifié en base par un validateur de schéma
 * MongoDB (`GrantChangeUnit`) — pas seulement ici. La représentation câblée (minuscules) doit
 * correspondre exactement à ce que le validateur accepte.
 */
enum class GrantRole(val wireValue: String) {
    VIEWER("viewer"),
    OPERATOR("operator"),
    ;

    companion object {
        fun fromWireValue(value: String): Either<UseCaseError, GrantRole> =
            entries.find { it.wireValue == value }?.right()
                ?: DomainError("grant.role.invalid", mapOf("value" to value)).left()
    }
}

/**
 * Frontière vers `shared_kernel` — `LOT0-06` : `UserContext.role` porte [AccessRole], pas
 * [GrantRole] (voir KDoc `AccessRole` : `shared_kernel` ne dépend jamais de `tenancy`, seule
 * cette conversion, faite ici, franchit la frontière). `when` exhaustif sans `else` : l'ajout d'un
 * rôle d'un côté sans l'autre casse la compilation au lieu de se découvrir en production.
 */
fun GrantRole.toAccessRole(): AccessRole = when (this) {
    GrantRole.VIEWER -> AccessRole.VIEWER
    GrantRole.OPERATOR -> AccessRole.OPERATOR
}
