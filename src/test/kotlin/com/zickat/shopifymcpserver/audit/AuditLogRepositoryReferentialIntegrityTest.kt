package com.zickat.shopifymcpserver.audit

import com.zickat.shopifymcpserver.audit.domain.repositories.AuditLogRepository
import io.kotest.matchers.shouldBe
import org.bson.types.ObjectId
import org.junit.jupiter.api.Test

/**
 * `schema.md` §3 — `auditLog → identity` (si non nul) et `auditLog → store` : invariants
 * applicatifs, couverts ici. **Existence seule** — une identité révoquée ou une boutique archivée
 * doivent pouvoir être journalisées (voir `AuditLogMongoRepository`, KDoc).
 */
interface AuditLogRepositoryReferentialIntegrityTest {
    val repository: AuditLogRepository

    fun registerExistingIdentity(): String
    fun registerExistingStore(): String

    @Test
    fun `should reject an entry whose identityId is an orphan identity`() {
        val storeId = registerExistingStore()
        val orphanIdentity = ObjectId().toHexString()
        val entry = AuditLogFixtures().withIdentityId(orphanIdentity).withStoreId(storeId).build()

        repository.append(entry).isLeft() shouldBe true
    }

    @Test
    fun `should reject an entry whose storeId does not exist`() {
        val identityId = registerExistingIdentity()
        val orphanStoreId = ObjectId().toHexString()
        val entry = AuditLogFixtures().withIdentityId(identityId).withStoreId(orphanStoreId).build()

        repository.append(entry).isLeft() shouldBe true
    }

    @Test
    fun `should accept an entry with a null identityId — unauthenticated calls are still logged`() {
        val storeId = registerExistingStore()
        val entry = AuditLogFixtures().withIdentityId(null).withStoreId(storeId).build()

        repository.append(entry).isRight() shouldBe true
    }

    @Test
    fun `should accept an entry whose identity and store both exist`() {
        val identityId = registerExistingIdentity()
        val storeId = registerExistingStore()
        val entry = AuditLogFixtures().withIdentityId(identityId).withStoreId(storeId).build()

        repository.append(entry).isRight() shouldBe true
    }
}
