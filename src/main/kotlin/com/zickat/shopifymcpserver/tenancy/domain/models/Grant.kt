package com.zickat.shopifymcpserver.tenancy.domain.models

import kotlinx.datetime.Instant

/**
 * Un droit d'une identité sur une boutique — `schema.md` §3. Un seul grant **actif**
 * (`revokedAt == null`) par couple `(identityId, storeId)`, garanti par un index unique partiel au
 * niveau du moteur (`GrantChangeUnit`), pas seulement ici.
 *
 * `identityId`/`grantedBy` sont des `String` (l'id hex de l'`Identity` référencée), **pas** le type
 * `IdentityId` du module `identity` : un module ne référence jamais le type de domaine d'un autre
 * module directement (même un simple wrapper d'id), seulement via son `exposed_interface` — ici
 * `IdentityExposedService.exists(...)`, appelé par le repository pour l'intégrité référentielle
 * (`schema.md` : « MongoDB ne garantit pas les clés étrangères »). `storeId` reste typé `StoreId`
 * car `Store` et `Grant` vivent dans le **même** module (`tenancy`) — ce n'est pas une frontière.
 */
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
