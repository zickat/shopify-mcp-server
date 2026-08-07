package com.zickat.shopifymcpserver.tenancy.spi.mongo

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
class StoreMongoRepositoryTest : WithMongoDBContainer() {

    @Autowired
    private lateinit var repository: StoreRepository

    @Autowired
    private lateinit var mongoTemplate: MongoTemplate

    @BeforeEach
    fun cleanCollection() {
        mongoTemplate.getCollection(StoreEntity.COLLECTION_NAME).deleteMany(Document())
    }

    @Test
    fun `should have a real unique index on slug in the database`() {
        val index = mongoTemplate.indexOps(StoreEntity.COLLECTION_NAME).indexInfo
            .find { it.name == "uniq_slug" }

        (index != null) shouldBe true
        index!!.isUnique shouldBe true
    }

    @Test
    fun `should reject a second store with the same slug at the engine level`() {
        val slug = "duplicate-slug-check"
        repository.save(StoreFixtures().withSlug(slug).build()).isRight() shouldBe true

        val duplicate = StoreFixtures().withSlug(slug).build()
        repository.save(duplicate).isLeft() shouldBe true
    }
}
