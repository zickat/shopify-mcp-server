package com.zickat.shopifymcpserver.audit.spi.mongo

import com.zickat.shopifymcpserver.audit.AuditLogFixtures
import com.zickat.shopifymcpserver.audit.AuditLogRepositoryReferentialIntegrityTest
import com.zickat.shopifymcpserver.audit.domain.repositories.AuditLogRepository
import com.zickat.shopifymcpserver.identity.IdentityFixtures
import com.zickat.shopifymcpserver.identity.domain.repositories.IdentityRepository
import com.zickat.shopifymcpserver.shared_kernel.WithMongoDBContainer
import com.zickat.shopifymcpserver.tenancy.StoreFixtures
import com.zickat.shopifymcpserver.tenancy.domain.repositories.StoreRepository
import io.kotest.matchers.shouldBe
import org.bson.Document
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.data.mongodb.core.MongoTemplate

@SpringBootTest
class AuditLogMongoRepositoryTest : AuditLogRepositoryReferentialIntegrityTest, WithMongoDBContainer() {

    @Autowired
    override lateinit var repository: AuditLogRepository

    @Autowired
    private lateinit var identityRepository: IdentityRepository

    @Autowired
    private lateinit var storeRepository: StoreRepository

    @Autowired
    private lateinit var mongoTemplate: MongoTemplate

    @BeforeEach
    fun cleanCollection() {
        mongoTemplate.getCollection(AuditLogEntity.COLLECTION_NAME).deleteMany(Document())
    }

    override fun registerExistingIdentity(): String =
        identityRepository.save(IdentityFixtures().build()).fold({ error("fixture setup failed: $it") }, { it.id.value })

    override fun registerExistingStore(): String =
        storeRepository.save(StoreFixtures().build()).fold({ error("fixture setup failed: $it") }, { it.id.value })

    @Test
    fun `should have real indexes on (storeId, occurredAt desc) and (identityId, occurredAt desc)`() {
        val indexes = mongoTemplate.indexOps(AuditLogEntity.COLLECTION_NAME).indexInfo

        (indexes.any { it.name == "store_occurred_desc" }) shouldBe true
        (indexes.any { it.name == "identity_occurred_desc" }) shouldBe true
    }

    @Test
    fun `should return entries for a store ordered by occurredAt descending`() {
        val storeId = registerExistingStore()
        val identityId = registerExistingIdentity()
        val older = AuditLogFixtures().withIdentityId(identityId).withStoreId(storeId).build()
        Thread.sleep(5)
        val newer = AuditLogFixtures().withIdentityId(identityId).withStoreId(storeId).build()
        repository.append(older)
        repository.append(newer)

        val result = repository.findByStore(storeId).fold({ error("unexpected left: $it") }, { it })
        result.first().id shouldBe newer.id
    }
}
