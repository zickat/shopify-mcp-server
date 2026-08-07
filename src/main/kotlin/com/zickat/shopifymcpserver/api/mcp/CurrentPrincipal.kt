package com.zickat.shopifymcpserver.api.mcp

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import com.zickat.shopifymcpserver.shared_kernel.NotAuthorizedError
import com.zickat.shopifymcpserver.shared_kernel.UseCaseError
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken

/** `(issuer, subject)` du jeton validé — schema.md §2, ce qui identifie une [Identity] naissante ou existante. */
data class JwtPrincipal(val issuer: String, val subject: String)

/**
 * `LOT0-08` : le seul endroit du dépôt qui lit le principal authentifié depuis le contexte de
 * sécurité pour l'exposer à un outil MCP. `identity`/`AudienceValidator` (`LOT0-05`) ont déjà
 * validé signature, expiration et audience avant que ce code s'exécute — `anyRequest()
 * .authenticated()` (`ResourceServerSecurityConfiguration`) garantit qu'aucune requête sans jeton
 * valide n'atteint un `@McpTool`. `type: SYNC` (transport WebMvc synchrone, `application.yml`) :
 * pas de saut de thread entre le filtre de sécurité et l'exécution de l'outil, donc
 * `SecurityContextHolder` — stratégie `ThreadLocal` par défaut — porte encore l'authentification
 * ici. Vérifié au runtime par `WhoAmIToolIntegrationTest` (`LOT0-08`), pas seulement supposé.
 *
 * Retourne un [Either] plutôt que de supposer l'authentification acquise : si ce code s'exécutait
 * un jour sans filtre de sécurité en amont (régression de configuration), l'appel échoue proprement
 * ([NotAuthorizedError], donc audité et refusé) au lieu de lever une `ClassCastException` non
 * journalisée.
 */
fun currentJwtPrincipal(): Either<UseCaseError, JwtPrincipal> {
    val jwt = (SecurityContextHolder.getContext().authentication as? JwtAuthenticationToken)?.token
        ?: return NotAuthorizedError("identity.principal.missing").left()
    val issuer = jwt.issuer?.toString() ?: return NotAuthorizedError("identity.principal.missing").left()
    val subject = jwt.subject ?: return NotAuthorizedError("identity.principal.missing").left()
    return JwtPrincipal(issuer = issuer, subject = subject).right()
}
