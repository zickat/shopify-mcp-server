package com.zickat.shopifymcpserver.vault.domain.repositories

import arrow.core.Either
import com.zickat.shopifymcpserver.shared_kernel.UseCaseError

/**
 * `keyRef` identifiant la clé maîtresse *actuellement active* — celle utilisée pour chiffrer tout
 * nouveau `wrappedDek` (`store`/`rotate` dans [com.zickat.shopifymcpserver.vault.domain.StoreCredentialUseCase]).
 *
 * Vit dans le domaine (pas dans l'adaptateur `EnvMasterKeyProvider`) parce que « quelle clé est
 * active » est une décision métier, pas un détail d'infrastructure — l'adaptateur ne fait que
 * savoir *résoudre* un `keyRef` donné, jamais en choisir un. Une rotation future changera cette
 * constante et ajoutera une entrée dans le mapping `keyRef → variable d'environnement` de
 * l'adaptateur, sans toucher au reste du domaine.
 */
const val ACTIVE_MASTER_KEY_REF = "v1"

/**
 * Port résolvant la clé maîtresse de chiffrement du coffre, **par `keyRef`** — jamais un accès
 * direct à une seule variable (`LOT0-04`, Q3 tranchée : pas de KMS, clé opérée par Val sur l'hôte).
 *
 * Cette indirection par référence est ce qui rend une rotation future possible sans refonte :
 * introduire une seconde clé (nouveau `keyRef`, nouvelle variable d'environnement), ré-envelopper
 * les `wrappedDek` existants un par un via [com.zickat.shopifymcpserver.vault.domain.StoreCredentialUseCase.rotate],
 * puis ne retirer l'ancienne variable qu'une fois la ré-enveloppe complète — **aucun outil de
 * rotation n'est construit dans ce lot**, seule cette conception qui ne l'empêche pas.
 *
 * `security.md` / `LOT0-04` point 3 : la clé résolue ne doit **jamais** apparaître dans un log, une
 * trace, ou un message d'erreur — y compris en cas d'échec de résolution.
 */
interface MasterKeyProvider {
    /** Résout la clé maîtresse identifiée par [keyRef]. `Left` si le `keyRef` est inconnu ou la variable absente/invalide — jamais avec la valeur en clair dans le message. */
    fun resolve(keyRef: String): Either<UseCaseError, ByteArray>
}
