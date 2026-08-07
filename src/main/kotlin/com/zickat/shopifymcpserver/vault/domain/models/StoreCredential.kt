package com.zickat.shopifymcpserver.vault.domain.models

import kotlinx.datetime.Instant

/**
 * Un credential Shopify chiffré — `schema.md` §3/§4. `LOT0-03` pose l'entité et son repository ;
 * le chiffrement enveloppe (clé maîtresse → DEK → ciphertext) est `LOT0-04` — `ciphertext` et
 * `wrappedDek` sont donc de simples `ByteArray` opaques ici, pas encore produits par un vrai
 * chiffrement.
 *
 * `storeId` est un `String` (id hex du `Store` référencé), pas le type `StoreId` du module
 * `tenancy` : un module ne référence jamais le type de domaine d'un autre module directement — voir
 * `tenancy.exposed_interface.StoreExposedService`, utilisé par le repository pour l'intégrité
 * référentielle.
 *
 * **Aucun `exposed_interface` de ce module ne doit jamais retourner ce type** (`security.md` :
 * « ne jamais inclure les credentials dans les réponses API standard ») — voir
 * `VaultExposedService`, dont le contrat ne renvoie jamais `StoreCredential` ni ses champs
 * sensibles.
 */
data class StoreCredential(
    val id: StoreCredentialId,
    val storeId: String,
    val ciphertext: ByteArray,
    val wrappedDek: ByteArray,
    val keyRef: String,
    val scopesGranted: String,
    val createdAt: Instant,
    val rotatedAt: Instant?,
    val revokedAt: Instant?,
) {
    val isActive: Boolean get() = revokedAt == null

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is StoreCredential) return false
        return id == other.id &&
            storeId == other.storeId &&
            ciphertext.contentEquals(other.ciphertext) &&
            wrappedDek.contentEquals(other.wrappedDek) &&
            keyRef == other.keyRef &&
            scopesGranted == other.scopesGranted &&
            createdAt == other.createdAt &&
            rotatedAt == other.rotatedAt &&
            revokedAt == other.revokedAt
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + storeId.hashCode()
        result = 31 * result + ciphertext.contentHashCode()
        result = 31 * result + wrappedDek.contentHashCode()
        result = 31 * result + keyRef.hashCode()
        result = 31 * result + scopesGranted.hashCode()
        result = 31 * result + createdAt.hashCode()
        result = 31 * result + (rotatedAt?.hashCode() ?: 0)
        result = 31 * result + (revokedAt?.hashCode() ?: 0)
        return result
    }
}
