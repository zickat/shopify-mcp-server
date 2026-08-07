package com.zickat.shopifymcpserver.audit

import com.zickat.shopifymcpserver.audit.domain.repositories.AuditLogRepository
import com.zickat.shopifymcpserver.audit.spi.mongo.AuditLogMongoRepository
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

/**
 * `schema.md` §3 / `LOT0-03` : « append-only — aucun use case ne fait `update` ni `delete` ».
 * Test de structure (par réflexion), pas un test de moteur : MongoDB n'a aucune contrainte
 * native empêchant une modification a posteriori sur une collection ordinaire (ce n'est pas une
 * collection *capped*, et aucune restriction de rôle base de données n'est mise en place à ce lot
 * — voir `progress.md`, entrée LOT0-03, pour la distinction assumée entre « aucun code ne le fait »
 * et « le moteur l'empêche »). Ce que ce test garantit : **aucune méthode `update`/`delete`/
 * `remove`/`upsert` n'existe nulle part dans le chemin `AuditLogRepository`**, ni dans le contrat,
 * ni dans son implémentation Mongo, ni dans le fake.
 */
class AuditLogAppendOnlyTest {

    private val forbiddenNamePattern = Regex("(?i)update|delete|remove|upsert")

    @Test
    fun `AuditLogRepository should not declare any update or delete method`() {
        val offending = AuditLogRepository::class.java.declaredMethods
            .filter { forbiddenNamePattern.containsMatchIn(it.name) }
            .map { it.name }

        offending shouldBe emptyList()
    }

    @Test
    fun `AuditLogMongoRepository should not declare any additional update or delete method`() {
        val offending = AuditLogMongoRepository::class.java.declaredMethods
            .filter { forbiddenNamePattern.containsMatchIn(it.name) }
            .map { it.name }

        offending shouldBe emptyList()
    }

    @Test
    fun `AuditLogFakeRepository should not declare any update or delete method`() {
        val offending = AuditLogFakeRepository::class.java.declaredMethods
            .filter { forbiddenNamePattern.containsMatchIn(it.name) }
            .map { it.name }

        offending shouldBe emptyList()
    }
}
