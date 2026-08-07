package com.zickat.shopifymcpserver.vault.spi.mongo

import arrow.core.Either
import arrow.core.right
import com.zickat.shopifymcpserver.shared_kernel.UseCaseError
import com.zickat.shopifymcpserver.vault.domain.models.StoreCredential
import com.zickat.shopifymcpserver.vault.domain.models.StoreCredentialId
import kotlinx.datetime.Instant
import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import org.bson.types.ObjectId
import org.springframework.data.mongodb.core.mapping.Document

@Serializable
@Document(StoreCredentialEntity.COLLECTION_NAME)
data class StoreCredentialEntity(
    @Contextual val _id: ObjectId,
    @Contextual val storeId: ObjectId,
    val ciphertext: ByteArray,
    val wrappedDek: ByteArray,
    val keyRef: String,
    val scopesGranted: String,
    val createdAt: Instant,
    val rotatedAt: Instant?,
    val revokedAt: Instant?,
) {
    companion object {
        const val COLLECTION_NAME = "storeCredentials"

        fun fromDomain(domain: StoreCredential): StoreCredentialEntity = StoreCredentialEntity(
            _id = ObjectId(domain.id.value),
            storeId = ObjectId(domain.storeId),
            ciphertext = domain.ciphertext,
            wrappedDek = domain.wrappedDek,
            keyRef = domain.keyRef,
            scopesGranted = domain.scopesGranted,
            createdAt = domain.createdAt,
            rotatedAt = domain.rotatedAt,
            revokedAt = domain.revokedAt,
        )
    }

    fun toDomain(): Either<UseCaseError, StoreCredential> = StoreCredential(
        id = StoreCredentialId(_id.toHexString()),
        storeId = storeId.toHexString(),
        ciphertext = ciphertext,
        wrappedDek = wrappedDek,
        keyRef = keyRef,
        scopesGranted = scopesGranted,
        createdAt = createdAt,
        rotatedAt = rotatedAt,
        revokedAt = revokedAt,
    ).right()

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is StoreCredentialEntity) return false
        return _id == other._id &&
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
        var result = _id.hashCode()
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
