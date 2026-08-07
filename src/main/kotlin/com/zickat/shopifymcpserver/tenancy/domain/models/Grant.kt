package com.zickat.shopifymcpserver.tenancy.domain.models

import kotlinx.datetime.Instant

data class Grant(
    val id: GrantId,
    val identityId: String,
    val storeId: StoreId,
    val role: GrantRole,
    val grantedBy: String,
    val createdAt: Instant,
    val revokedAt: Instant?,
) {
    val isActive: Boolean get() = revokedAt == null
}
