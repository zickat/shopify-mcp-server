package com.zickat.shopifymcpserver.vault.domain.crypto

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import java.security.SecureRandom
import javax.crypto.AEADBadTagException
import org.junit.jupiter.api.Test

class EnvelopeCryptoTest {

    private val random = SecureRandom()
    private fun randomKey() = ByteArray(EnvelopeCrypto.KEY_LENGTH_BYTES).also { random.nextBytes(it) }
    private val aad = "store-1".toByteArray()

    @Test
    fun `decrypt should return the original plaintext after encrypt`() {
        val key = randomKey()
        val plaintext = "shpat_super-secret-admin-token".toByteArray()

        val ciphertext = EnvelopeCrypto.encrypt(plaintext, key, aad)
        val decrypted = EnvelopeCrypto.decrypt(ciphertext, key, aad)

        decrypted.contentEquals(plaintext) shouldBe true
    }

    @Test
    fun `two encryptions of the same plaintext with the same key should produce different ciphertexts`() {
        val key = randomKey()
        val plaintext = "same-secret".toByteArray()

        val first = EnvelopeCrypto.encrypt(plaintext, key, aad)
        val second = EnvelopeCrypto.encrypt(plaintext, key, aad)

        first.contentEquals(second) shouldBe false
    }

    @Test
    fun `two calls to generateDataKey should never return the same key`() {
        val first = EnvelopeCrypto.generateDataKey()
        val second = EnvelopeCrypto.generateDataKey()

        first.contentEquals(second) shouldBe false
    }

    @Test
    fun `decrypt should fail with the wrong key`() {
        val plaintext = "shpat_super-secret-admin-token".toByteArray()
        val ciphertext = EnvelopeCrypto.encrypt(plaintext, randomKey(), aad)

        shouldThrow<AEADBadTagException> {
            EnvelopeCrypto.decrypt(ciphertext, randomKey(), aad)
        }
    }

    @Test
    fun `decrypt should fail when the ciphertext has been tampered with`() {
        val key = randomKey()
        val ciphertext = EnvelopeCrypto.encrypt("shpat_super-secret".toByteArray(), key, aad)
        val tampered = ciphertext.copyOf().also { it[it.size - 1] = (it[it.size - 1].toInt() xor 0xFF).toByte() }

        shouldThrow<AEADBadTagException> {
            EnvelopeCrypto.decrypt(tampered, key, aad)
        }
    }

    @Test
    fun `decrypt should fail when the AAD does not match the one used at encryption — a payload cannot be relinked to a different store`() {
        val key = randomKey()
        val ciphertext = EnvelopeCrypto.encrypt("shpat_super-secret".toByteArray(), key, "store-1".toByteArray())

        shouldThrow<AEADBadTagException> {
            EnvelopeCrypto.decrypt(ciphertext, key, "store-2".toByteArray())
        }
    }
}
