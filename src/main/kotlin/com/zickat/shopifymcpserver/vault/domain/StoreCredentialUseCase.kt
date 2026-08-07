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

@Component
class StoreCredentialUseCase(
    private val repository: StoreCredentialRepository,
    private val masterKeyProvider: MasterKeyProvider,
    private val clock: Clock = Clock.System,
) {
    private val log = LoggerFactory.getLogger(StoreCredentialUseCase::class.java)

    fun store(storeId: String, plaintext: ByteArray, scopesGranted: String): Either<UseCaseError, StoreCredentialId> = either {
        val masterKey = masterKeyProvider.resolve(ACTIVE_MASTER_KEY_REF).bind()
        val dek = EnvelopeCrypto.generateDataKey()
        val ciphertext = encryptLoggingOnlyStoreIdAndStepOnFailure(plaintext, dek, storeId = storeId, step = "encrypt").bind()
        val wrappedDek = encryptLoggingOnlyStoreIdAndStepOnFailure(dek, masterKey, storeId = storeId, step = "wrap").bind()

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

    fun rotate(id: StoreCredentialId, newPlaintext: ByteArray): Either<UseCaseError, StoreCredentialId> = either {
        val existing = repository.findById(id).bind()
        val masterKey = masterKeyProvider.resolve(ACTIVE_MASTER_KEY_REF).bind()
        val dek = EnvelopeCrypto.generateDataKey()
        val ciphertext = encryptLoggingOnlyStoreIdAndStepOnFailure(newPlaintext, dek, storeId = existing.storeId, step = "encrypt").bind()
        val wrappedDek = encryptLoggingOnlyStoreIdAndStepOnFailure(dek, masterKey, storeId = existing.storeId, step = "wrap").bind()

        val rotated = existing.copy(
            ciphertext = ciphertext,
            wrappedDek = wrappedDek,
            keyRef = ACTIVE_MASTER_KEY_REF,
            rotatedAt = clock.now(),
        )
        repository.save(rotated).bind().id
    }

    fun reveal(id: StoreCredentialId): Either<UseCaseError, ByteArray> = either {
        val credential = repository.findById(id).bind()
        val masterKey = masterKeyProvider.resolve(credential.keyRef).bind()
        val dek = decryptLoggingOnlyCredentialMetadataOnFailure(credential.wrappedDek, masterKey, credential, step = "unwrap").bind()
        decryptLoggingOnlyCredentialMetadataOnFailure(credential.ciphertext, dek, credential, step = "decrypt").bind()
    }

    private fun encryptLoggingOnlyStoreIdAndStepOnFailure(plaintext: ByteArray, key: ByteArray, storeId: String, step: String): Either<UseCaseError, ByteArray> =
        runCatching { EnvelopeCrypto.encrypt(plaintext, key) }
            .fold(
                onSuccess = { Either.Right(it) },
                onFailure = { e ->
                    log.error("Vault {} failed for store {} ({})", step, storeId, e.javaClass.simpleName)
                    Either.Left(TechnicalError("storeCredential.crypto.failed", mapOf("step" to step)))
                },
            )

    private fun decryptLoggingOnlyCredentialMetadataOnFailure(payload: ByteArray, key: ByteArray, credential: StoreCredential, step: String): Either<UseCaseError, ByteArray> =
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
