package com.zickat.shopifymcpserver.tenancy.domain.models

import kotlinx.datetime.Instant

data class Store(
    val id: StoreId,
    val slug: String,
    val shopDomain: String,
    val brandProfileRef: String?,
    val createdAt: Instant,
    val archivedAt: Instant?,
) {
    val isArchived: Boolean get() = archivedAt != null
}
