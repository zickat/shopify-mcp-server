package com.zickat.shopifymcpserver.vault.domain.models

import kotlinx.datetime.Instant

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
