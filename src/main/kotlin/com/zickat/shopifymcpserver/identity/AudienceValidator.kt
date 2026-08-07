package com.zickat.shopifymcpserver.identity

import org.springframework.security.oauth2.core.OAuth2Error
import org.springframework.security.oauth2.core.OAuth2TokenValidator
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult
import org.springframework.security.oauth2.jwt.Jwt

class AudienceValidator(private val expectedAudience: String) : OAuth2TokenValidator<Jwt> {

    private val error = OAuth2Error(
        "invalid_token",
        "The required audience '$expectedAudience' is missing",
        null,
    )

    override fun validate(token: Jwt): OAuth2TokenValidatorResult =
        if (token.audience?.contains(expectedAudience) == true) {
            OAuth2TokenValidatorResult.success()
        } else {
            OAuth2TokenValidatorResult.failure(error)
        }
}
