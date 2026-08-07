package com.zickat.shopifymcpserver.shared_kernel

import arrow.core.Either
import arrow.core.left
import arrow.core.right

object ToolAccessControl {

    fun isVisible(role: AccessRole, useCase: ToolUseCase): Boolean =
        role == AccessRole.OPERATOR || useCase.kind == UseCaseKind.READ

    fun authorizeCall(role: AccessRole, useCase: ToolUseCase): Either<UseCaseError, Unit> =
        if (isVisible(role, useCase)) {
            Unit.right()
        } else {
            ForbiddenError(
                "access.role.insufficient",
                mapOf("requiredRole" to AccessRole.OPERATOR.name, "actualRole" to role.name),
            ).left()
        }

    fun filterForList(role: AccessRole, useCases: List<ToolUseCase>): List<ToolUseCase> =
        useCases.filter { isVisible(role, it) }
}
