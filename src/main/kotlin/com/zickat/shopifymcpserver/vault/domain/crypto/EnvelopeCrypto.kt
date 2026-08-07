package com.zickat.shopifymcpserver.vault.domain.crypto

import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Chiffrement par enveloppe (`schema.md` §4) : primitives AES-256-GCM pures, sans Spring ni
 * MongoDB — seulement la bibliothèque standard du JDK (`javax.crypto`), ce qui n'enfreint pas
 * « le domaine ne dépend jamais d'un framework externe » (`backend.md`).
 *
 * Un seul format binaire pour tout ce qui sort d'ici : `IV (12 octets) || ciphertext+tag (GCM)`.
 * Le tag d'authentification GCM (16 octets, inclus par le JDK dans la sortie de `doFinal`) est ce
 * qui fait échouer [decrypt] avec `javax.crypto.AEADBadTagException` — message statique
 * (« Tag mismatch! »), jamais construit à partir de la clé ou du texte en clair — sur une clé
 * fausse ou des octets corrompus, au lieu de rendre silencieusement un texte incorrect.
 *
 * `LOT0-04` point 2, « deux chiffrements du même secret produisent des ciphertext différents » :
 * chaque appel à [encrypt] tire un IV neuf via [SecureRandom] — jamais réutilisé, jamais dérivé
 * d'un compteur ou de l'horloge.
 */
object EnvelopeCrypto {
    private const val ALGORITHM = "AES/GCM/NoPadding"
    private const val KEY_ALGORITHM = "AES"
    private const val IV_LENGTH_BYTES = 12
    private const val TAG_LENGTH_BITS = 128
    const val KEY_LENGTH_BYTES = 32 // AES-256

    private val secureRandom = SecureRandom()

    /** Une clé de données (DEK) neuve, aléatoire, AES-256 — jamais réutilisée entre deux credentials. */
    fun generateDataKey(): ByteArray = ByteArray(KEY_LENGTH_BYTES).also { secureRandom.nextBytes(it) }

    /**
     * Chiffre [plaintext] avec [key] (DEK ou clé maîtresse — même primitive, l'enveloppe n'est
     * qu'une question de *quelle* clé chiffre *quoi*). Retourne `IV || ciphertext+tag`.
     */
    fun encrypt(plaintext: ByteArray, key: ByteArray): ByteArray {
        val iv = ByteArray(IV_LENGTH_BYTES).also { secureRandom.nextBytes(it) }
        val cipher = Cipher.getInstance(ALGORITHM)
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, KEY_ALGORITHM), GCMParameterSpec(TAG_LENGTH_BITS, iv))
        return iv + cipher.doFinal(plaintext)
    }

    /**
     * Déchiffre une sortie de [encrypt]. Lève (ne retourne jamais `Either` ici : c'est à
     * l'appelant — [com.zickat.shopifymcpserver.vault.domain.StoreCredentialUseCase] — de
     * catch/logger sans jamais inclure [key] ni le résultat dans un message). Le résultat en
     * clair ne doit vivre qu'en mémoire, le temps de l'appel — jamais persisté, jamais retourné
     * par une méthode publique du module.
     */
    fun decrypt(payload: ByteArray, key: ByteArray): ByteArray {
        require(payload.size > IV_LENGTH_BYTES) { "payload too short to contain an IV" }
        val iv = payload.copyOfRange(0, IV_LENGTH_BYTES)
        val ciphertext = payload.copyOfRange(IV_LENGTH_BYTES, payload.size)
        val cipher = Cipher.getInstance(ALGORITHM)
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, KEY_ALGORITHM), GCMParameterSpec(TAG_LENGTH_BITS, iv))
        return cipher.doFinal(ciphertext)
    }
}
