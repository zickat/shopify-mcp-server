package com.zickat.shopifymcpserver.identity.spi.mongo

import com.zickat.shopifymcpserver.identity.IdentityFixtures
import com.zickat.shopifymcpserver.identity.domain.repositories.IdentityRepository
import com.zickat.shopifymcpserver.shared_kernel.WithMongoDBContainer
import io.kotest.matchers.shouldBe
import org.bson.Document
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.data.mongodb.core.MongoTemplate

@SpringBootTest
class IdentityMongoRepositoryTest : WithMongoDBContainer() {

    @Autowired
    private lateinit var repository: IdentityRepository

    @Autowired
    private lateinit var mongoTemplate: MongoTemplate

    @BeforeEach
    fun cleanCollection() {
        mongoTemplate.getCollection(IdentityEntity.COLLECTION_NAME).deleteMany(Document())
    }

    @Test
    fun `should have a real unique index on (issuer, subject) in the database`() {
        val index = mongoTemplate.indexOps(IdentityEntity.COLLECTION_NAME).indexInfo
            .find { it.name == "uniq_issuer_subject" }

        (index != null) shouldBe true
        index!!.isUnique shouldBe true
    }

    @Test
    fun `should reject a second identity with the same (issuer, subject) at the engine level`() {
        val issuer = "https://idp.test/duplicate-check"
        val subject = "sub-duplicate-check"
        repository.save(IdentityFixtures().withIssuer(issuer).withSubject(subject).build()).isRight() shouldBe true

        val duplicate = IdentityFixtures().withIssuer(issuer).withSubject(subject).build()
        repository.save(duplicate).isLeft() shouldBe true
    }

    @Test
    fun `should find an identity by (issuer, subject)`() {
        val issuer = "https://idp.test/find-check"
        val subject = "sub-find-check"
        repository.save(IdentityFixtures().withIssuer(issuer).withSubject(subject).build())

        repository.findByIssuerAndSubject(issuer, subject).isRight() shouldBe true
    }
}
