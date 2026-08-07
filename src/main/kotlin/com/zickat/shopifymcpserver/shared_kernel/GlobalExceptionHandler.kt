package com.zickat.shopifymcpserver.shared_kernel

import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

@RestControllerAdvice
class GlobalExceptionHandler {

    private val log = LoggerFactory.getLogger(GlobalExceptionHandler::class.java)

    @ExceptionHandler(UseCaseErrorException::class)
    fun handle(ex: UseCaseErrorException): ResponseEntity<ErrorResponse> {
        val status = statusOf(ex.error)
        if (status.is5xxServerError) {
            log.error("Technical error: {}", (ex.error as? DomainError)?.messageKey ?: ex.error::class.simpleName)
        } else {
            log.warn("Domain error: {}", (ex.error as? DomainError)?.messageKey ?: ex.error::class.simpleName)
        }
        return ResponseEntity.status(status).body(ErrorResponse.from(ex.error))
    }

    private fun statusOf(error: UseCaseError): HttpStatus = when (error) {
        is NotFoundError -> HttpStatus.NOT_FOUND
        is NotAuthorizedError -> HttpStatus.UNAUTHORIZED
        is ForbiddenError -> HttpStatus.FORBIDDEN
        is TechnicalError -> HttpStatus.INTERNAL_SERVER_ERROR
        is ManyUseCaseError -> error.errors.maxOfOrNull { statusOf(it).value() }
            ?.let { HttpStatus.valueOf(it) } ?: HttpStatus.BAD_REQUEST
        is DomainError -> HttpStatus.BAD_REQUEST
        else -> HttpStatus.INTERNAL_SERVER_ERROR
    }
}

data class ErrorResponse(
    val message: String,
    val parameters: Map<String, String>,
) {
    companion object {
        fun from(error: UseCaseError): ErrorResponse = when (error) {
            is DomainError -> ErrorResponse(error.messageKey, error.parameters ?: mapOf())
            is ManyUseCaseError -> ErrorResponse(
                "many.errors",
                mapOf("count" to error.errors.size.toString()),
            )
            else -> ErrorResponse("technical.error", mapOf())
        }
    }
}
