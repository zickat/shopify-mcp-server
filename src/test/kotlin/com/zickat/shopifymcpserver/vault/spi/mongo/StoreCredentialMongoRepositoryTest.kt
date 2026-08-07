package com.zickat.shopifymcpserver.vault.spi.mongo

import com.zickat.shopifymcpserver.shared_kernel.WithMongoDBContainer
import com.zickat.shopifymcpserver.tenancy.StoreFixtures
import com.zickat.shopifymcpserver.tenancy.domain.repositories.StoreRepository
import com.zickat.shopifymcpserver.vault.StoreCredentialFixtures
import com.zickat.shopifymcpserver.vault.StoreCredentialRepositoryReferentialIntegrityTest
import com.zickat.shopifymcpserver.vault.domain.repositories.StoreCredentialRepository
import io.kotest.matchers.shouldBe
import org.bson.Document
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.data.mongodb.core.MongoTemplate

@SpringBootTest
class StoreCredentialMongoRepositoryTest : StoreCredentialRepositoryReferentialIntegrityTest, WithMongoDBContainer() {

    @Autowired
    override lateinit var repository: StoreCredentialRepository

    @Autowired
    private lateinit var storeRepository: StoreRepository

    @Autowired
    private lateinit var mongoTemplate: MongoTemplate

    @BeforeEach
    fun cleanCollection() {
        mongoTemplate.getCollection(StoreCredentialEntity.COLLECTION_NAME).deleteMany(Document())
    }

    override fun registerStore(archived: Boolean): String {
        val fixtures = StoreFixtures().let { if (archived) it.archived() else it }
        return storeRepository.save(fixtures.build()).fold({ error("fixture setup failed: $it") }, { it.id.value })
    }

    @Test
    fun `should have a real unique partial index on storeId filtered on revokedAt null`() {
        val index = mongoTemplate.indexOps(StoreCredentialEntity.COLLECTION_NAME).indexInfo
            .find { it.name == "uniq_active_store" }

        (index != null) shouldBe true
        index!!.isUnique shouldBe true
        (index.partialFilterExpression != null) shouldBe true
    }

    @Test
    fun `should reject a second active credential for the same store at the engine level`() {
        val storeId = registerStore(archived = false)
        val first = StoreCredentialFixtures().withStoreId(storeId).build()
        repository.save(first).isRight() shouldBe true

        val second = StoreCredentialFixtures().withStoreId(storeId).build()
        repository.save(second).isLeft() shouldBe true
    }

    @Test
    fun `should allow a new active credential once the previous one is revoked`() {
        val storeId = registerStore(archived = false)
        val revoked = StoreCredentialFixtures().withStoreId(storeId).revoked().build()
        repository.save(revoked).isRight() shouldBe true

        val active = StoreCredentialFixtures().withStoreId(storeId).build()
        repository.save(active).isRight() shouldBe true
    }
}
