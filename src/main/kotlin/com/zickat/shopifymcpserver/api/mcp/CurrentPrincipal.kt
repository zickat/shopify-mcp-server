package com.zickat.shopifymcpserver.api.mcp

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import com.zickat.shopifymcpserver.shared_kernel.NotAuthorizedError
import com.zickat.shopifymcpserver.shared_kernel.UseCaseError
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken

data class JwtPrincipal(val issuer: String, val subject: String)

fun currentJwtPrincipal(): Either<UseCaseError, JwtPrincipal> {
    val jwt = (SecurityContextHolder.getContext().authentication as? JwtAuthenticationToken)?.token
        ?: return NotAuthorizedError("identity.principal.missing").left()
    val issuer = jwt.issuer?.toString() ?: return NotAuthorizedError("identity.principal.missing").left()
    val subject = jwt.subject ?: return NotAuthorizedError("identity.principal.missing").left()
    return JwtPrincipal(issuer = issuer, subject = subject).right()
}
