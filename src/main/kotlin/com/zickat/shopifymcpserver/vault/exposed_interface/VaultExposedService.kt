package com.zickat.shopifymcpserver.vault.exposed_interface

import org.springframework.modulith.NamedInterface

/**
 * API publique du module `vault` vers les autres modules.
 *
 * **Contrainte de câblage posée dès `LOT0-03`, pour que `LOT0-04` n'ait rien à corriger après
 * coup** (`schema.md` §3, `security.md`) : **aucune méthode de cette interface ne retourne
 * `StoreCredential` ni aucun de ses champs sensibles** (`ciphertext`, `wrappedDek`). Le
 * déchiffrement (`LOT0-04`) restera un détail d'implémentation du module `vault` ; les autres
 * modules ne sauront jamais qu'un credential existe autrement que par ce booléen.
 *
 * Vérifié par [com.zickat.shopifymcpserver.vault.VaultExposedServiceContractTest].
 */
@NamedInterface("exposed_interface")
interface VaultExposedService {
    /** Vrai si la boutique a un credential actif (`revokedAt == null`) — jamais le credential lui-même. */
    fun hasActiveCredential(storeId: String): Boolean
}
