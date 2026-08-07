package com.zickat.shopifymcpserver.tenancy.spi.mongo

import com.zickat.shopifymcpserver.shared_kernel.WithMongoDBContainer
import com.zickat.shopifymcpserver.tenancy.StoreFixtures
import com.zickat.shopifymcpserver.tenancy.domain.models.StoreId
import com.zickat.shopifymcpserver.tenancy.domain.repositories.StoreRepository
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import java.util.Date
import kotlinx.datetime.Instant
import org.bson.Document
import org.bson.types.ObjectId
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query

@SpringBootTest
class InstantMongoDateConversionTest : WithMongoDBContainer() {

    @Autowired
    private lateinit var repository: StoreRepository

    @Autowired
    private lateinit var mongoTemplate: MongoTemplate

    @BeforeEach
    fun cleanCollection() {
        mongoTemplate.getCollection(StoreEntity.COLLECTION_NAME).deleteMany(Document())
    }

    @Test
    fun `createdAt should be persisted as a real BSON date and found by a date range query, not as a nested epochSeconds object`() {
        val saved = repository.save(StoreFixtures().build()).shouldBeRight()

        val rawDocument = mongoTemplate.getCollection(StoreEntity.COLLECTION_NAME).find().first()!!
        rawDocument["createdAt"].shouldBeInstanceOf<Date>()

        val query = Query(Criteria.where("createdAt").gt(Date.from(java.time.Instant.EPOCH)))
        val found = mongoTemplate.find(query, StoreEntity::class.java)
        (found.any { it._id == ObjectId(saved.id.value) }) shouldBe true
    }

    @Test
    fun `a document written by a third party with a real BSON date is readable by the repository and found by a date range query`() {
        val id = ObjectId()
        val thirdPartyCreatedAt = Date.from(java.time.Instant.parse("2026-01-15T00:00:00Z"))
        val thirdPartyDocument = Document()
            .append("_id", id)
            .append("slug", "third-party-store")
            .append("shopDomain", "third-party.myshopify.com")
            .append("brandProfileRef", null)
            .append("createdAt", thirdPartyCreatedAt)
            .append("archivedAt", null)
        mongoTemplate.getCollection(StoreEntity.COLLECTION_NAME).insertOne(thirdPartyDocument)

        val found = repository.findById(StoreId(id.toHexString())).shouldBeRight()
        found.createdAt shouldBe Instant.fromEpochMilliseconds(thirdPartyCreatedAt.time)

        val query = Query(Criteria.where("createdAt").gt(Date.from(java.time.Instant.parse("2026-01-01T00:00:00Z"))))
        val results = mongoTemplate.find(query, StoreEntity::class.java)
        (results.any { it._id == id }) shouldBe true
    }
}
