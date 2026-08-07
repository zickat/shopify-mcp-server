package com.zickat.shopifymcpserver.vault

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import com.zickat.shopifymcpserver.shared_kernel.TechnicalError
import com.zickat.shopifymcpserver.shared_kernel.UseCaseError
import com.zickat.shopifymcpserver.vault.domain.crypto.EnvelopeCrypto
import com.zickat.shopifymcpserver.vault.domain.repositories.ACTIVE_MASTER_KEY_REF
import com.zickat.shopifymcpserver.vault.domain.repositories.MasterKeyProvider
import java.security.SecureRandom

class MasterKeyProviderFake(
    private val keys: MutableMap<String, ByteArray> = mutableMapOf(
        ACTIVE_MASTER_KEY_REF to randomKey(),
    ),
) : MasterKeyProvider {

    companion object {
        private fun randomKey(): ByteArray =
            ByteArray(EnvelopeCrypto.KEY_LENGTH_BYTES).also { SecureRandom().nextBytes(it) }
    }

    fun keyFor(keyRef: String): ByteArray = keys.getValue(keyRef)

    fun remove(keyRef: String) = apply { keys.remove(keyRef) }

    override fun resolve(keyRef: String): Either<UseCaseError, ByteArray> =
        keys[keyRef]?.right()
            ?: TechnicalError("storeCredential.masterKey.missing", mapOf("keyRef" to keyRef)).left()
}
