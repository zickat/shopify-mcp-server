package com.zickat.shopifymcpserver.audit.spi.mongo

import arrow.core.right
import com.zickat.shopifymcpserver.audit.domain.AuditLogUseCase
import com.zickat.shopifymcpserver.audit.domain.repositories.AuditLogRepository
import com.zickat.shopifymcpserver.identity.IdentityFixtures
import com.zickat.shopifymcpserver.identity.domain.repositories.IdentityRepository
import com.zickat.shopifymcpserver.shared_kernel.WithMongoDBContainer
import com.zickat.shopifymcpserver.tenancy.StoreFixtures
import com.zickat.shopifymcpserver.tenancy.domain.repositories.StoreRepository
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import org.bson.Document
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.data.mongodb.core.MongoTemplate

@SpringBootTest
class AuditPrecedesResponseIntegrationTest : WithMongoDBContainer() {

    @Autowired
    private lateinit var auditLogUseCase: AuditLogUseCase

    @Autowired
    private lateinit var auditLogRepository: AuditLogRepository

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

    private fun registerStore(): String =
        storeRepository.save(StoreFixtures().build()).shouldBeRight().id.value

    private fun registerIdentity(): String =
        identityRepository.save(IdentityFixtures().build()).shouldBeRight().id.value

    private fun buildResponseThatAlwaysFails(businessResult: Any): Nothing =
        throw IllegalStateException("simulated failure while building the response, downstream of a committed audit write")

    @Test
    fun `audit line already exists in a real MongoDB when something fails after the business action succeeded`() {
        val storeId = registerStore()
        val identityId = registerIdentity()

        val actionResult = auditLogUseCase.execute(
            identityId = identityId,
            storeId = storeId,
            toolName = "list_products",
            isMutation = false,
            toolInput = mapOf("query" to "shoes"),
        ) { "business result".right() }

        actionResult.isRight() shouldBe true

        val downstreamFailure = runCatching { buildResponseThatAlwaysFails(actionResult) }
        downstreamFailure.isFailure shouldBe true

        val entries = auditLogRepository.findByStore(storeId).shouldBeRight()
        entries shouldHaveSize 1
        entries.first().outcome shouldBe "ok"
        entries.first().toolName shouldBe "list_products"
    }
}
