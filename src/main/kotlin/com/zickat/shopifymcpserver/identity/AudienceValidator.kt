package com.zickat.shopifymcpserver.identity

import org.springframework.security.oauth2.core.OAuth2Error
import org.springframework.security.oauth2.core.OAuth2TokenValidator
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult
import org.springframework.security.oauth2.jwt.Jwt

/**
 * RFC 8707 — validation d'audience. `architecture.md` §5 : « le contrôle le plus important du
 * serveur : sans lui, tout jeton du même IdP ouvre nos boutiques. »
 *
 * [expectedAudience] est une **constante de configuration** (`mcp.security.expected-audience`),
 * jamais dérivée de la requête entrante. C'est délibéré et documenté : `mcp-server-security`
 * fournit sa propre validation d'audience (`AudienceValidationJwtDecoder`), mais elle calcule
 * l'audience attendue à partir de l'URL de la requête HTTP courante (`ResourceIdentifier.
 * getResource()` → `UrlUtils.buildFullRequestUrl(request)`), donc du `Host` reçu — exactement ce
 * que la tâche interdit (« une constante de configuration, pas une valeur devinée »), et une
 * surface où un en-tête `Host` falsifié changerait la valeur attendue. C'est pourquoi
 * `ResourceServerSecurityConfiguration` n'active pas `validateAudienceClaim` du configurateur
 * amont et fait ce contrôle elle-même, avec cette classe. « Le test d'audience valide notre
 * configuration, jamais la lib » (tâche LOT0-05, point 2) — cette classe **est** cette
 * configuration.
 */
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
