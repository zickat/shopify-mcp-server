package com.zickat.shopifymcpserver.shared_kernel

import arrow.core.Either

class UseCaseErrorException(val error: UseCaseError) : RuntimeException(error.toDisplayMessage())

fun <T> Either<UseCaseError, T>.orThrow(): T =
    fold({ throw UseCaseErrorException(it) }, { it })

private fun UseCaseError.toDisplayMessage(): String = when (this) {
    is ManyUseCaseError -> "many.errors (${errors.size})"
    is DomainError -> if (parameters.isNullOrEmpty()) messageKey else "$messageKey $parameters"
    else -> "technical.error"
}
