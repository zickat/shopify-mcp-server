package com.zickat.shopifymcpserver.vault.domain.crypto

import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

object EnvelopeCrypto {
    private const val ALGORITHM = "AES/GCM/NoPadding"
    private const val KEY_ALGORITHM = "AES"
    private const val IV_LENGTH_BYTES = 12
    private const val TAG_LENGTH_BITS = 128
    const val KEY_LENGTH_BYTES = 32

    private val secureRandom = SecureRandom()

    fun generateDataKey(): ByteArray = ByteArray(KEY_LENGTH_BYTES).also { secureRandom.nextBytes(it) }

    fun encrypt(plaintext: ByteArray, key: ByteArray, aad: ByteArray): ByteArray {
        val iv = ByteArray(IV_LENGTH_BYTES).also { secureRandom.nextBytes(it) }
        val cipher = Cipher.getInstance(ALGORITHM)
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, KEY_ALGORITHM), GCMParameterSpec(TAG_LENGTH_BITS, iv))
        cipher.updateAAD(aad)
        return iv + cipher.doFinal(plaintext)
    }

    fun decrypt(payload: ByteArray, key: ByteArray, aad: ByteArray): ByteArray {
        require(payload.size > IV_LENGTH_BYTES) { "payload too short to contain an IV" }
        val iv = payload.copyOfRange(0, IV_LENGTH_BYTES)
        val ciphertext = payload.copyOfRange(IV_LENGTH_BYTES, payload.size)
        val cipher = Cipher.getInstance(ALGORITHM)
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, KEY_ALGORITHM), GCMParameterSpec(TAG_LENGTH_BITS, iv))
        cipher.updateAAD(aad)
        return cipher.doFinal(ciphertext)
    }
}
