package com.zickat.shopifymcpserver.audit.domain.models

import kotlinx.datetime.Instant

/**
 * Une entrée du journal d'audit — `schema.md` §3. `LOT0-03` pose l'entité, les index et
 * l'invariant **append-only** ; le contenu métier détaillé (ce que le journal garantit en cas
 * d'échec d'écriture) est `LOT0-07`.
 *
 * `identityId`/`storeId` sont des `String` (ids hex référencés dans `identity`/`tenancy`), pas
 * leurs types de domaine respectifs — même raison que `Grant`/`StoreCredential` : un module ne
 * référence jamais le type de domaine d'un autre module directement.
 *
 * `identityId` est **nullable** : « null si non authentifié » (`schema.md` — un appel refusé avant
 * authentification est quand même journalisé).
 *
 * `toolInputDigest` est une **empreinte ou des champs sélectionnés, jamais l'entrée brute**
 * (`schema.md` §3) — modélisé ici comme une map de paires clé/valeur déjà réduites ; le contenu
 * réel de cette réduction est `LOT0-07`.
 */
data class AuditLog(
    val id: AuditLogId,
    val identityId: String?,
    val storeId: String,
    val toolName: String,
    val isMutation: Boolean,
    val outcome: String,
    val denialReason: String?,
    val toolInputDigest: Map<String, String>,
    val occurredAt: Instant,
)
