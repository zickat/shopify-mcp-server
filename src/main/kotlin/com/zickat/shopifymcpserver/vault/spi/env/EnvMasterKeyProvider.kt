package com.zickat.shopifymcpserver.vault.spi.env

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import com.zickat.shopifymcpserver.shared_kernel.TechnicalError
import com.zickat.shopifymcpserver.shared_kernel.UseCaseError
import com.zickat.shopifymcpserver.vault.domain.crypto.EnvelopeCrypto
import com.zickat.shopifymcpserver.vault.domain.repositories.ACTIVE_MASTER_KEY_REF
import com.zickat.shopifymcpserver.vault.domain.repositories.MasterKeyProvider
import java.util.Base64
import org.springframework.stereotype.Component

@Component
class EnvMasterKeyProvider(
    private val keyRefToEnvVar: Map<String, String> = mapOf(ACTIVE_MASTER_KEY_REF to DEFAULT_ENV_VAR),
    private val env: (String) -> String? = System::getenv,
) : MasterKeyProvider {

    companion object {
        const val DEFAULT_ENV_VAR = "CATALOG_MASTER_KEY"
    }

    override fun resolve(keyRef: String): Either<UseCaseError, ByteArray> {
        val envVarName = keyRefToEnvVar[keyRef]
            ?: return TechnicalError("storeCredential.masterKey.unknownKeyRef", mapOf("keyRef" to keyRef)).left()

        val encoded = env(envVarName)
            ?: return TechnicalError("storeCredential.masterKey.missing", mapOf("envVar" to envVarName)).left()

        val decoded = try {
            Base64.getDecoder().decode(encoded)
        } catch (_: IllegalArgumentException) {
            return TechnicalError("storeCredential.masterKey.malformed", mapOf("envVar" to envVarName)).left()
        }

        if (decoded.size != EnvelopeCrypto.KEY_LENGTH_BYTES) {
            return TechnicalError(
                "storeCredential.masterKey.wrongLength",
                mapOf("envVar" to envVarName, "expectedBytes" to EnvelopeCrypto.KEY_LENGTH_BYTES.toString()),
            ).left()
        }

        return decoded.right()
    }
}
