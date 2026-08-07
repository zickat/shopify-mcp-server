package com.zickat.shopifymcpserver.shared_kernel

import arrow.core.Either

/**
 * Pont entre le domaine (qui ne lève jamais d'exception, Either uniquement — backend.md) et la
 * frontière HTTP (adaptateur primaire), qui a besoin d'un mécanisme centralisé pour transformer un
 * `Either.Left` en réponse HTTP mappée. Un controller qui reçoit un `Either<UseCaseError, T>`
 * l'obtient avec [orThrow] ; [GlobalExceptionHandler] intercepte cette exception et ne remonte
 * jamais plus haut que l'adaptateur — le domaine, lui, ne l'a jamais levée.
 */
class UseCaseErrorException(val error: UseCaseError) : RuntimeException()

fun <T> Either<UseCaseError, T>.orThrow(): T =
    fold({ throw UseCaseErrorException(it) }, { it })
