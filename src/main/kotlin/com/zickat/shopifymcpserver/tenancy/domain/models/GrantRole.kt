package com.zickat.shopifymcpserver.tenancy.domain.models

import arrow.core.Either
import arrow.core.left
import arrow.core.right
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
