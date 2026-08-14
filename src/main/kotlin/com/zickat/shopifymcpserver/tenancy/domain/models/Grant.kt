package com.zickat.shopifymcpserver.tenancy.domain.models

import kotlinx.datetime.Instant

data class Grant(
    val id: GrantId,
    val identityId: String,
    val storeId: StoreId,
    val role: GrantRole,
    val grantedBy: String,
    val createdAt: Instant,
    val expiresAt: Instant?,
    val revokedAt: Instant?,
) {
    val isActive: Boolean get() = revokedAt == null

    fun isUsable(now: Instant): Boolean = isActive && (expiresAt == null || now < expiresAt)
}
