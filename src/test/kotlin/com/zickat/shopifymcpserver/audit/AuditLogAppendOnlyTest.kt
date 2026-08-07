package com.zickat.shopifymcpserver.audit

import com.zickat.shopifymcpserver.audit.domain.repositories.AuditLogRepository
import com.zickat.shopifymcpserver.audit.spi.mongo.AuditLogMongoRepository
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

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
