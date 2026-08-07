package com.zickat.shopifymcpserver.tenancy.domain.models

import kotlinx.datetime.Instant

/**
 * Une boutique — `schema.md` §3. `brandProfileRef` est un **pointeur**, pas le contenu (le profil
 * de marque reste un fichier versionné hors de cette base).
 */
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
