package com.zickat.shopifymcpserver.shared_kernel

interface UseCaseError

open class DomainError(
    open val messageKey: String,
    open val parameters: Map<String, String>? = mapOf(),
) : UseCaseError

open class NotFoundError(
    messageKey: String,
    parameters: Map<String, String>? = mapOf(),
) : DomainError(messageKey, parameters)

open class NotAuthorizedError(
    messageKey: String,
    parameters: Map<String, String>? = mapOf(),
) : DomainError(messageKey, parameters)

open class ForbiddenError(
    messageKey: String,
    parameters: Map<String, String>? = mapOf(),
) : DomainError(messageKey, parameters)

open class TechnicalError(
    messageKey: String,
    parameters: Map<String, String>? = mapOf(),
) : DomainError(messageKey, parameters)

open class ManyUseCaseError(
    val errors: List<UseCaseError>,
) : UseCaseError
