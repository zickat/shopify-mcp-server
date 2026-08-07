package com.zickat.shopifymcpserver.shared_kernel

import arrow.core.Either
import arrow.core.left
import arrow.core.right

/**
 * Les deux barrières de schema.md §6, vérifiées indépendamment :
 * - [filterForList] — ce que `tools/list` montre. Bonne ergonomie côté client, **pas** la sécurité.
 * - [authorizeCall] — le vrai contrôle serveur, appliqué à **chaque** appel, y compris un appel
 *   direct d'un outil que `tools/list` n'aurait pas montré. « Le filtre au `tools/list` n'est pas
 *   le contrôle d'accès — il le double. »
 *
 * Un `operator` voit et appelle tout ; un `viewer` seulement les [UseCaseKind.READ].
 */
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
