package com.zickat.shopifymcpserver.vault

import com.zickat.shopifymcpserver.vault.domain.models.StoreCredential
import com.zickat.shopifymcpserver.vault.domain.models.StoreCredentialId
import kotlin.time.Clock
import kotlinx.datetime.Instant
import org.bson.types.ObjectId

class StoreCredentialFixtures {
    private var id = StoreCredentialId(ObjectId().toHexString())
    private var storeId = ObjectId().toHexString()
    private var ciphertext = "not-really-encrypted-yet".toByteArray()
    private var wrappedDek = "not-really-a-wrapped-dek-yet".toByteArray()
    private var keyRef = "test-key-ref"
    private var scopesGranted = "read_products,write_products"
    private var createdAt = Clock.System.now()
    private var rotatedAt: Instant? = null
    private var revokedAt: Instant? = null

    fun withId(id: StoreCredentialId) = apply { this.id = id }
    fun withStoreId(storeId: String) = apply { this.storeId = storeId }
    fun revoked() = apply { this.revokedAt = Clock.System.now() }

    fun build(): StoreCredential = StoreCredential(
        id = id,
        storeId = storeId,
        ciphertext = ciphertext,
        wrappedDek = wrappedDek,
        keyRef = keyRef,
        scopesGranted = scopesGranted,
        createdAt = createdAt,
        rotatedAt = rotatedAt,
        revokedAt = revokedAt,
    )
}
