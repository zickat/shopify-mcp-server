package com.zickat.shopifymcpserver.identity

import org.springaicommunity.mcp.security.server.config.McpServerOAuth2Configurer
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.oauth2.jwt.JwtValidators
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder
import org.springframework.security.web.SecurityFilterChain

/**
 * Resource server OAuth 2.1 — `LOT0-05`. D2 : ce serveur **valide** des jetons, il n'en émet pas.
 *
 * Construite sur le support OAuth2 Resource Server standard de Spring Security
 * (`backend.md` : « JWT et OAuth2 peuvent être ajoutés sans modifier le filtre » — ce point
 * d'extension), configurée par `issuer-uri`/`jwk-set-uri` externalisés — **jamais** par un SDK
 * propre à un fournisseur. Q1 (choix de l'IdP) reste ouverte : brancher un vrai IdP au lot 2 est un
 * changement de configuration, pas de code.
 *
 * **Décision `mcp-security`** (voir `progress.md`, entrée LOT0-05) : adoption **partielle** de
 * `org.springaicommunity:mcp-server-security:0.1.14`. On réutilise sa plomberie RFC 9728
 * (`McpServerOAuth2Configurer` — endpoint Protected Resource Metadata, `AuthenticationEntryPoint`
 * qui pose `WWW-Authenticate: ... resource_metadata="..."` sur 401), mais **pas** sa validation
 * d'audience native (`validateAudienceClaim`) : elle dérive l'audience attendue du `Host` de la
 * requête, pas d'une constante de configuration (voir [AudienceValidator]). On fournit notre propre
 * [JwtDecoder] via `.jwtDecoder(...)`, ce qui désactive cette dérivation côté lib sans renoncer au
 * reste de sa plomberie.
 */
@EnableWebSecurity
@Configuration
class ResourceServerSecurityConfiguration {

    /**
     * Décodeur JWT : signature contre le JWKS configuré (aucun appel réseau vers un IdP réel dans
     * les tests — JWKS auto-émis, servi en HTTP local par MockWebServer), expiration
     * ([JwtValidators.createDefault], exp/nbf — pas de vérification d'issuer ici : Q1 n'est pas
     * tranchée), puis **audience** via [AudienceValidator], notre propre validateur, jamais celui
     * de `mcp-server-security`.
     */
    @Bean
    fun jwtDecoder(
        @Value("\${spring.security.oauth2.resourceserver.jwt.jwk-set-uri}") jwkSetUri: String,
        @Value("\${mcp.security.expected-audience}") expectedAudience: String,
    ): JwtDecoder {
        val decoder = NimbusJwtDecoder.withJwkSetUri(jwkSetUri).build()
        decoder.setJwtValidator(
            DelegatingOAuth2TokenValidator(
                JwtValidators.createDefault(),
                AudienceValidator(expectedAudience),
            ),
        )
        return decoder
    }

    @Bean
    fun securityFilterChain(
        http: HttpSecurity,
        jwtDecoder: JwtDecoder,
        @Value("\${spring.security.oauth2.resourceserver.jwt.issuer-uri}") issuerUri: String,
    ): SecurityFilterChain {
        http
            .csrf { it.disable() } // resource server sans état, aucun formulaire/cookie de session
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .authorizeHttpRequests { auth ->
                auth
                    // security.md : "pas d'endpoint public sauf ceux explicitement marqués".
                    // Actuator health (LOT0-02) + PRM (RFC 9728, ce lot) sont les deux seuls.
                    .requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
                    .requestMatchers(
                        "/.well-known/oauth-protected-resource",
                        "/.well-known/oauth-protected-resource/**",
                    ).permitAll()
                    .anyRequest().authenticated()
            }

        http.with(McpServerOAuth2Configurer.mcpServerOAuth2()) { configurer ->
            configurer
                .authorizationServer(issuerUri)
                .resourcePath("/mcp")
                .jwtDecoder(jwtDecoder)
            // validateAudienceClaim volontairement PAS activé : voir la doc de classe et
            // AudienceValidator — c'est notre décodeur (ci-dessus) qui porte le vrai contrôle.
        }

        return http.build()
    }
}
