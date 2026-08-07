package com.zickat.shopifymcpserver.shared_kernel

import net.javacrumbs.shedlock.core.DefaultLockingTaskExecutor
import net.javacrumbs.shedlock.core.LockConfiguration
import net.javacrumbs.shedlock.provider.mongo.MongoLockProvider
import org.bson.Document
import org.slf4j.LoggerFactory
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.stereotype.Component
import java.time.Duration
import java.time.Instant

/**
 * Exécute les [ChangeUnit] de tous les modules, une fois, dans l'ordre, sous verrou distribué
 * ShedLock — utile dès qu'une deuxième instance démarre en parallèle (pas le cas au lot 0, mais le
 * coût de le poser maintenant est nul : ShedLock est déjà dans la pile et déjà prouvé sur Boot 4.1,
 * D14).
 *
 * Idempotent : la collection `migrations` retient les `id` déjà appliqués — un changeunit déjà
 * exécuté n'est jamais rejoué. Les opérations elles-mêmes (création d'index, validateur de
 * collection) sont en plus naturellement idempotentes côté MongoDB, mais ce registre documente ce
 * qui a été appliqué et évite de rejouer une future migration plus lourde (backfill de données)
 * pour rien.
 */
@Component
class MigrationRunner(
    private val mongoTemplate: MongoTemplate,
    private val changeUnits: List<ChangeUnit>,
) : ApplicationRunner {

    private val log = LoggerFactory.getLogger(MigrationRunner::class.java)

    companion object {
        const val MIGRATIONS_COLLECTION = "migrations"
        private const val LOCK_NAME = "shopify-mcp-server-migrations"
    }

    override fun run(args: ApplicationArguments) {
        val lockProvider = MongoLockProvider(mongoTemplate.db)
        val executor = DefaultLockingTaskExecutor(lockProvider)
        executor.executeWithLock(
            Runnable { runMigrations() },
            LockConfiguration(Instant.now(), LOCK_NAME, Duration.ofMinutes(5), Duration.ofSeconds(1)),
        )
    }

    private fun runMigrations() {
        val applied = mongoTemplate.getCollection(MIGRATIONS_COLLECTION)
            .find()
            .map { it.getString("_id") }
            .toSet()

        changeUnits.sortedBy { it.order }.forEach { unit ->
            if (unit.id in applied) {
                log.info("Migration {} already applied, skipping", unit.id)
                return@forEach
            }
            log.info("Applying migration {}", unit.id)
            unit.execute(mongoTemplate)
            mongoTemplate.getCollection(MIGRATIONS_COLLECTION).insertOne(
                Document("_id", unit.id).append("appliedAt", Instant.now().toString()),
            )
        }
    }
}
