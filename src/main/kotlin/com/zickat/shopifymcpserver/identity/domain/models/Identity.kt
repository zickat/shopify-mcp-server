package com.zickat.shopifymcpserver.identity.domain.models

import kotlinx.datetime.Instant

/**
 * Une identité authentifiée (opérateur) — `schema.md` §3. `subject` est opaque (le claim `sub` du
 * jeton), jamais utilisé comme identifiant d'affichage ; `(issuer, subject)` est la clé naturelle
 * (le `sub` n'est unique que chez un émetteur donné).
 */
data class Identity(
    val id: IdentityId,
    val issuer: String,
    val subject: String,
    val displayName: String,
    val createdAt: Instant,
    val revokedAt: Instant?,
) {
    val isActive: Boolean get() = revokedAt == null
}
