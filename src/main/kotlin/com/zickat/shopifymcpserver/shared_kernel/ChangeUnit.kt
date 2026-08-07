package com.zickat.shopifymcpserver.shared_kernel

import org.springframework.data.mongodb.core.MongoTemplate

/**
 * Une évolution ordonnée et idempotente du schéma MongoDB (index, validateur de collection).
 *
 * `LOT0-03` : mécanisme de migration choisi — exécuteur maison sur ShedLock (voir `progress.md`,
 * entrée LOT0-03, pour la justification face aux deux autres options posées par la tâche). Chaque
 * module contribue ses propres `ChangeUnit` dans son `spi/mongo/` — [MigrationRunner] (ici, dans
 * `shared_kernel`) ne connaît que cette interface, jamais les types concrets des autres modules :
 * Spring les découvre par injection de type (`List<ChangeUnit>`), pas par import direct, donc
 * aucune frontière Modulith n'est franchie.
 */
interface ChangeUnit {
    /** Identifiant stable, jamais réutilisé — sert de clé dans la collection `migrations`. */
    val id: String

    /** Ordre d'exécution — les changeunits s'exécutent du plus petit au plus grand. */
    val order: Int

    fun execute(mongoTemplate: MongoTemplate)
}
