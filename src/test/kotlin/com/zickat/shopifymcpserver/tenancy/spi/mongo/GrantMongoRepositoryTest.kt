package com.zickat.shopifymcpserver.tenancy.spi.mongo

import com.zickat.shopifymcpserver.identity.IdentityFixtures
import com.zickat.shopifymcpserver.identity.domain.repositories.IdentityRepository
import com.zickat.shopifymcpserver.shared_kernel.WithMongoDBContainer
import com.zickat.shopifymcpserver.tenancy.GrantFixtures
import com.zickat.shopifymcpserver.tenancy.GrantRepositoryReferentialIntegrityTest
import com.zickat.shopifymcpserver.tenancy.StoreFixtures
import com.zickat.shopifymcpserver.tenancy.domain.models.StoreId
import com.zickat.shopifymcpserver.tenancy.domain.repositories.GrantRepository
import com.zickat.shopifymcpserver.tenancy.domain.repositories.StoreRepository
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.data.mongodb.core.MongoTemplate

/**
 * `LOT0-03` — les mêmes cas de [GrantRepositoryReferentialIntegrityTest] rejoués contre un vrai
 * MongoDB (patron `testing.md`), plus les vérifications propres au moteur : un index déclaré dans
 * une annotation mais jamais réellement appliqué en base est un bug silencieux classique — on ne
 * vérifie jamais seulement que le code le déclare.
 */
@SpringBootTest
class GrantMongoRepositoryTest : GrantRepositoryReferentialIntegrityTest, WithMongoDBContainer() {

    @Autowired
    override lateinit var repository: GrantRepository

    @Autowired
    private lateinit var identityRepository: IdentityRepository

    @Autowired
    private lateinit var storeRepository: StoreRepository

    @Autowired
    private lateinit var mongoTemplate: MongoTemplate

    @BeforeEach
    fun cleanCollections() {
        mongoTemplate.getCollection(GrantEntity.COLLECTION_NAME).deleteMany(org.bson.Document())
    }

    override fun registerExistingIdentity(): String =
        identityRepository.save(IdentityFixtures().build()).fold({ error("fixture setup failed: $it") }, { it.id.value })

    override fun registerStore(archived: Boolean): StoreId {
        val fixtures = StoreFixtures().let { if (archived) it.archived() else it }
        return storeRepository.save(fixtures.build()).fold({ error("fixture setup failed: $it") }, { it.id })
    }

    @Test
    fun `should have a real unique partial index on (identityId, storeId) filtered on revokedAt null`() {
        val index = mongoTemplate.indexOps(GrantEntity.COLLECTION_NAME).indexInfo
            .find { it.name == "uniq_active_identity_store" }

        (index != null) shouldBe true
        index!!.isUnique shouldBe true
        (index.partialFilterExpression != null) shouldBe true
    }

    @Test
    fun `should have a real compound read index on (identityId, storeId, revokedAt)`() {
        val index = mongoTemplate.indexOps(GrantEntity.COLLECTION_NAME).indexInfo
            .find { it.name == "identity_store_revoked" }

        (index != null) shouldBe true
    }

    @Test
    fun `should reject a second active grant for the same (identityId, storeId) at the engine level`() {
        val identityId = registerExistingIdentity()
        val storeId = registerStore(archived = false)
        val first = GrantFixtures().withIdentityId(identityId).withStoreId(storeId).withGrantedBy(identityId).build()
        repository.save(first).isRight() shouldBe true

        val second = GrantFixtures().withIdentityId(identityId).withStoreId(storeId).withGrantedBy(identityId).build()
        repository.save(second).isLeft() shouldBe true
    }

    @Test
    fun `should allow a new active grant once the previous one is revoked — the partial index only constrains active grants`() {
        val identityId = registerExistingIdentity()
        val storeId = registerStore(archived = false)
        val revoked = GrantFixtures().withIdentityId(identityId).withStoreId(storeId).withGrantedBy(identityId).revoked().build()
        repository.save(revoked).isRight() shouldBe true

        val active = GrantFixtures().withIdentityId(identityId).withStoreId(storeId).withGrantedBy(identityId).build()
        repository.save(active).isRight() shouldBe true
    }

    @Test
    fun `should reject an invalid role value by the collection schema validator, at the engine level`() {
        val identityId = registerExistingIdentity()
        val storeId = registerStore(archived = false)
        val invalidRoleDocument = org.bson.Document()
            .append("_id", org.bson.types.ObjectId())
            .append("identityId", org.bson.types.ObjectId(identityId))
            .append("storeId", org.bson.types.ObjectId(storeId.value))
            .append("role", "superadmin") // hors enum viewer|operator
            .append("grantedBy", org.bson.types.ObjectId(identityId))
            .append("createdAt", java.util.Date())
            .append("revokedAt", null)

        val exception = runCatching {
            mongoTemplate.getCollection(GrantEntity.COLLECTION_NAME).insertOne(invalidRoleDocument)
        }.exceptionOrNull()

        (exception != null) shouldBe true
    }
}
