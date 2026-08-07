package com.zickat.shopifymcpserver.tenancy.domain.models

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import com.zickat.shopifymcpserver.shared_kernel.AccessRole
import com.zickat.shopifymcpserver.shared_kernel.DomainError
import com.zickat.shopifymcpserver.shared_kernel.UseCaseError

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

fun GrantRole.toAccessRole(): AccessRole = when (this) {
    GrantRole.VIEWER -> AccessRole.VIEWER
    GrantRole.OPERATOR -> AccessRole.OPERATOR
}
