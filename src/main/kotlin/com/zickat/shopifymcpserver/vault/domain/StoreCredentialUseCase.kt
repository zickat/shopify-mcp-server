package com.zickat.shopifymcpserver.vault.domain

import arrow.core.Either
import arrow.core.raise.either
import com.zickat.shopifymcpserver.shared_kernel.TechnicalError
import com.zickat.shopifymcpserver.shared_kernel.UseCaseError
import com.zickat.shopifymcpserver.vault.domain.crypto.EnvelopeCrypto
import com.zickat.shopifymcpserver.vault.domain.models.StoreCredential
import com.zickat.shopifymcpserver.vault.domain.models.StoreCredentialId
import com.zickat.shopifymcpserver.vault.domain.repositories.ACTIVE_MASTER_KEY_REF
import com.zickat.shopifymcpserver.vault.domain.repositories.MasterKeyProvider
import com.zickat.shopifymcpserver.vault.domain.repositories.StoreCredentialRepository
import kotlin.time.Clock
import org.bson.types.ObjectId
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

/**
 * Cas d'usage du coffre — chiffrement enveloppe des credentials Shopify (`LOT0-04`).
 *
 * **Frontière Modulith** : cette classe vit dans `vault/domain/`, un sous-package non exposé (voir
 * `com.zickat.shopifymcpserver.shared_kernel.UseCaseError`, doc de classe) — invisible aux autres
 * modules par construction, sans annotation supplémentaire. C'est ici, et nulle part ailleurs, que
 * `ciphertext`/`wrappedDek` existent en clair (le texte en clair d'un secret, pas les octets
 * chiffrés eux-mêmes) — [reveal] ne doit **jamais** être appelée depuis `exposed_interface`.
 *
 * Aucun appelant ne câble encore [reveal] au lot 0 (« Ce que cette tâche ne fait pas » —
 * `LOT0-04.md`) : elle existe pour que le lot 2 (premier outil qui parle à Shopify) n'ait pas à
 * revenir ici.
 */
@Component
class StoreCredentialUseCase(
    private val repository: StoreCredentialRepository,
    private val masterKeyProvider: MasterKeyProvider,
    private val clock: Clock = Clock.System,
) {
    private val log = LoggerFactory.getLogger(StoreCredentialUseCase::class.java)

    /** Stocke un nouveau secret en clair pour [storeId] — jamais persisté ni loggué autrement que chiffré. */
    fun store(storeId: String, plaintext: ByteArray, scopesGranted: String): Either<UseCaseError, StoreCredentialId> = either {
        val masterKey = masterKeyProvider.resolve(ACTIVE_MASTER_KEY_REF).bind()
        val dek = EnvelopeCrypto.generateDataKey()
        val ciphertext = encryptOrRaise(plaintext, dek, storeId = storeId, step = "encrypt").bind()
        val wrappedDek = encryptOrRaise(dek, masterKey, storeId = storeId, step = "wrap").bind()

        val credential = StoreCredential(
            id = StoreCredentialId(ObjectId().toHexString()),
            storeId = storeId,
            ciphertext = ciphertext,
            wrappedDek = wrappedDek,
            keyRef = ACTIVE_MASTER_KEY_REF,
            scopesGranted = scopesGranted,
            createdAt = clock.now(),
            rotatedAt = null,
            revokedAt = null,
        )
        repository.save(credential).bind().id
    }

    /**
     * Rotation d'un credential : réécrit `ciphertext`+`wrappedDek` avec une DEK neuve, ne touche
     * pas la clé maîtresse (`schema.md` §4). Use case posé, rien ne l'appelle encore (`LOT0-04.md`,
     * « ce que cette tâche ne fait pas » : pas d'outil de rotation automatisé au lot 0).
     */
    fun rotate(id: StoreCredentialId, newPlaintext: ByteArray): Either<UseCaseError, StoreCredentialId> = either {
        val existing = repository.findById(id).bind()
        val masterKey = masterKeyProvider.resolve(ACTIVE_MASTER_KEY_REF).bind()
        val dek = EnvelopeCrypto.generateDataKey()
        val ciphertext = encryptOrRaise(newPlaintext, dek, storeId = existing.storeId, step = "encrypt").bind()
        val wrappedDek = encryptOrRaise(dek, masterKey, storeId = existing.storeId, step = "wrap").bind()

        val rotated = existing.copy(
            ciphertext = ciphertext,
            wrappedDek = wrappedDek,
            keyRef = ACTIVE_MASTER_KEY_REF,
            rotatedAt = clock.now(),
        )
        repository.save(rotated).bind().id
    }

    /**
     * Déchiffre le secret d'un credential — **valeur en clair produite en mémoire uniquement**,
     * jamais persistée, jamais retournée par une méthode `exposed_interface`. Réservée aux
     * appelants internes du module `vault` (lot 2 : l'outil qui appelle réellement Shopify).
     */
    fun reveal(id: StoreCredentialId): Either<UseCaseError, ByteArray> = either {
        val credential = repository.findById(id).bind()
        val masterKey = masterKeyProvider.resolve(credential.keyRef).bind()
        val dek = decryptOrRaise(credential.wrappedDek, masterKey, credential, step = "unwrap").bind()
        decryptOrRaise(credential.ciphertext, dek, credential, step = "decrypt").bind()
    }

    /**
     * `security.md` / `LOT0-04` point 3 : sur échec de chiffrement, le log ne porte que des
     * identifiants non sensibles (`storeId`, `step`) — jamais [key] ni [plaintext].
     */
    private fun encryptOrRaise(plaintext: ByteArray, key: ByteArray, storeId: String, step: String): Either<UseCaseError, ByteArray> =
        runCatching { EnvelopeCrypto.encrypt(plaintext, key) }
            .fold(
                onSuccess = { Either.Right(it) },
                onFailure = { e ->
                    log.error("Vault {} failed for store {} ({})", step, storeId, e.javaClass.simpleName)
                    Either.Left(TechnicalError("storeCredential.crypto.failed", mapOf("step" to step)))
                },
            )

    /**
     * `security.md` / `LOT0-04` point 3 : sur échec de déchiffrement (clé fausse, octets
     * corrompus — `AEADBadTagException`), le log ne porte que l'id du credential, son `keyRef` et
     * le type d'exception — **jamais** la clé maîtresse, la DEK, ni le texte en clair.
     */
    private fun decryptOrRaise(payload: ByteArray, key: ByteArray, credential: StoreCredential, step: String): Either<UseCaseError, ByteArray> =
        runCatching { EnvelopeCrypto.decrypt(payload, key) }
            .fold(
                onSuccess = { Either.Right(it) },
                onFailure = { e ->
                    log.warn(
                        "Vault {} failed for credential {} (keyRef={}, reason={})",
                        step,
                        credential.id.value,
                        credential.keyRef,
                        e.javaClass.simpleName,
                    )
                    Either.Left(TechnicalError("storeCredential.crypto.failed", mapOf("step" to step)))
                },
            )
}
