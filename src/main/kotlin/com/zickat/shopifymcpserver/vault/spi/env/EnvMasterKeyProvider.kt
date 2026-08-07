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

/**
 * Implémentation `LOT0-04` de [MasterKeyProvider] — Q3 tranchée : pas de KMS, la clé maîtresse vit
 * dans la configuration de l'hôte (variable d'environnement), opérée par Val lui-même.
 *
 * **Le risque assumé par ce choix** (`LOT0-04.md`) : une seule clé chiffre tous les `wrappedDek` de
 * toutes les boutiques — quiconque obtient la configuration de l'hôte déchiffre tout le coffre. Il
 * n'y a pas de compartimentation par boutique au niveau de la clé maîtresse.
 *
 * [keyRefToEnvVar] fait le pont entre `keyRef` (identité stable, stockée en base) et *nom* de
 * variable d'environnement (jamais une valeur en dur) — c'est ce mapping, pas cette classe, qui
 * grandit le jour d'une rotation : ajouter `"v2" to "CATALOG_MASTER_KEY_V2"` permet de résoudre
 * l'ancienne et la nouvelle clé le temps d'une ré-enveloppe complète, sans toucher au code.
 *
 * [env] est injectable (défaut `System::getenv`) uniquement pour permettre aux tests de simuler
 * une variable absente ou une valeur donnée sans manipuler le véritable environnement du process
 * — la JVM ne permet pas de modifier `System.getenv()` proprement une fois démarrée.
 */
@Component
class EnvMasterKeyProvider(
    private val keyRefToEnvVar: Map<String, String> = mapOf(ACTIVE_MASTER_KEY_REF to DEFAULT_ENV_VAR),
    private val env: (String) -> String? = System::getenv,
) : MasterKeyProvider {

    companion object {
        /** Nom de la variable d'environnement — documentée, jamais sa valeur, dans `.env.example`. */
        const val DEFAULT_ENV_VAR = "CATALOG_MASTER_KEY"
    }

    override fun resolve(keyRef: String): Either<UseCaseError, ByteArray> {
        val envVarName = keyRefToEnvVar[keyRef]
            ?: return TechnicalError("storeCredential.masterKey.unknownKeyRef", mapOf("keyRef" to keyRef)).left()

        val encoded = env(envVarName)
            ?: return TechnicalError("storeCredential.masterKey.missing", mapOf("envVar" to envVarName)).left()

        // `encoded` peut être la clé elle-même : jamais dans un message d'erreur ni un log, même en
        // cas d'échec de décodage — seuls des identifiants non sensibles (keyRef, nom de variable,
        // longueur observée) apparaissent ci-dessous.
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
