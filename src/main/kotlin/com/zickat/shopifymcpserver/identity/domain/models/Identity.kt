package com.zickat.shopifymcpserver.identity.domain.models

import kotlinx.datetime.Instant

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
