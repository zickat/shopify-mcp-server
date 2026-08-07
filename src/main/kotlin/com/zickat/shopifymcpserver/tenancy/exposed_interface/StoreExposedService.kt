package com.zickat.shopifymcpserver.tenancy.exposed_interface

import org.springframework.modulith.NamedInterface

/**
 * API publique du module `tenancy` vers les autres modules. Utilisée pour l'intégrité
 * référentielle inter-modules (`storeCredential → store` par `vault`, `auditLog → store` par
 * `audit` — `schema.md` §3).
 *
 * Deux méthodes distinctes et volontairement différentes : un crédential ne doit pas pouvoir être
 * créé pour une boutique archivée (`existsAndNotArchived`), mais un journal d'audit doit pouvoir
 * enregistrer une action — y compris refusée — contre une boutique archivée (`exists` seul) : lui
 * interdire l'écriture casserait la raison d'être du journal.
 */
@NamedInterface("exposed_interface")
interface StoreExposedService {
    /** Vrai si la boutique existe (peu importe son statut archivé/actif). */
    fun exists(storeId: String): Boolean

    /** Vrai si la boutique existe et n'est pas archivée. */
    fun existsAndNotArchived(storeId: String): Boolean
}
