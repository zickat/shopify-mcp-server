package com.zickat.shopifymcpserver.shared_kernel

import arrow.core.Either

/**
 * Pont entre le domaine (qui ne lève jamais d'exception, Either uniquement — backend.md) et un
 * adaptateur primaire, qui a besoin d'un mécanisme centralisé pour transformer un `Either.Left` en
 * réponse mappée. Un controller REST qui reçoit un `Either<UseCaseError, T>` l'obtient avec
 * [orThrow] ; [GlobalExceptionHandler] intercepte cette exception et ne remonte jamais plus haut
 * que l'adaptateur — le domaine, lui, ne l'a jamais levée.
 *
 * **`LOT0-08`** : réutilisé tel quel par le transport MCP (`api/mcp`), qui n'a pas d'équivalent
 * `@RestControllerAdvice` — le framework MCP catche l'exception à la frontière de l'outil et la
 * restitue comme `CallToolResult(isError = true)`, en lisant [message]. D'où [message] porté par
 * `super(...)` (calculé une fois, jamais recalculé) : un client MCP n'a que ce texte pour
 * comprendre le refus, contrairement au controller REST qui, lui, lit `.error` directement.
 */
class UseCaseErrorException(val error: UseCaseError) : RuntimeException(error.toDisplayMessage())

fun <T> Either<UseCaseError, T>.orThrow(): T =
    fold({ throw UseCaseErrorException(it) }, { it })

/**
 * `messageKey`/`parameters` ne sont jamais des secrets (`security.md` : ce sont des clés i18n et
 * des identifiants métier) — sûrs à restituer tels quels à un appelant, REST ou MCP.
 */
private fun UseCaseError.toDisplayMessage(): String = when (this) {
    is ManyUseCaseError -> "many.errors (${errors.size})"
    is DomainError -> if (parameters.isNullOrEmpty()) messageKey else "$messageKey $parameters"
    else -> "technical.error"
}
