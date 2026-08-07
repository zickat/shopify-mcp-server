package com.zickat.shopifymcpserver.shared_kernel

/**
 * Contrat que tout futur use case exposé comme outil MCP devra implémenter — câblage réel des
 * outils : `LOT0-08`. schema.md §6 : « la source de la classification lecture/mutation est portée
 * par la déclaration de chaque use case Kotlin ».
 *
 * **Fermé par défaut** : [kind] vaut [UseCaseKind.MUTATION] tant qu'il n'est pas explicitement
 * redéclaré `READ`. Un use case qui oublie de se classer est donc traité comme mutant et refusé à
 * un `viewer` — jamais l'inverse, même discipline que le manifeste des outils relayés côté
 * TypeScript (schema.md §6, « un outil absent du manifeste est refusé, jamais laissé passer »),
 * transposée ici au mécanisme natif Kotlin. Vérifié par `ToolAccessControlTest`.
 */
interface ToolUseCase {
    val kind: UseCaseKind get() = UseCaseKind.MUTATION
}
