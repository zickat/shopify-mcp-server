package com.zickat.shopifymcpserver.shared_kernel

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus

class GlobalExceptionHandlerTest {

    private val handler = GlobalExceptionHandler()

    @Test
    fun `should map NotFoundError to 404`() {
        val response = handler.handle(UseCaseErrorException(NotFoundError("thing.not.found")))
        response.statusCode shouldBe HttpStatus.NOT_FOUND
        response.body?.message shouldBe "thing.not.found"
    }

    @Test
    fun `should map NotAuthorizedError to 401`() {
        val response = handler.handle(UseCaseErrorException(NotAuthorizedError("auth.invalid.token")))
        response.statusCode shouldBe HttpStatus.UNAUTHORIZED
    }

    @Test
    fun `should map ForbiddenError to 403`() {
        val response = handler.handle(UseCaseErrorException(ForbiddenError("grant.missing")))
        response.statusCode shouldBe HttpStatus.FORBIDDEN
    }

    @Test
    fun `should map TechnicalError to 500`() {
        val response = handler.handle(UseCaseErrorException(TechnicalError("mongo.unreachable")))
        response.statusCode shouldBe HttpStatus.INTERNAL_SERVER_ERROR
    }

    @Test
    fun `should map plain DomainError to 400`() {
        val response = handler.handle(UseCaseErrorException(DomainError("request.invalid")))
        response.statusCode shouldBe HttpStatus.BAD_REQUEST
    }

    @Test
    fun `should map ManyUseCaseError to the worst status among its errors`() {
        val response = handler.handle(
            UseCaseErrorException(
                ManyUseCaseError(listOf(DomainError("field.a.invalid"), ForbiddenError("field.b.forbidden"))),
            ),
        )
        response.statusCode shouldBe HttpStatus.FORBIDDEN
    }
}
