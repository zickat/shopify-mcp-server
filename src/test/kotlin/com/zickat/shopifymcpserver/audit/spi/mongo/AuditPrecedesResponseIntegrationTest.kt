package com.zickat.shopifymcpserver.audit.spi.mongo

import arrow.core.right
import com.zickat.shopifymcpserver.audit.domain.AuditLogUseCase
import com.zickat.shopifymcpserver.audit.domain.repositories.AuditLogRepository
import com.zickat.shopifymcpserver.identity.IdentityFixtures
import com.zickat.shopifymcpserver.identity.domain.repositories.IdentityRepository
import com.zickat.shopifymcpserver.shared_kernel.WithMongoDBContainer
import com.zickat.shopifymcpserver.tenancy.StoreFixtures
import com.zickat.shopifymcpserver.tenancy.domain.repositories.StoreRepository
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import org.bson.Document
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.data.mongodb.core.MongoTemplate

/**
 * `LOT0-07.md`, « comment on vérifie que c'est fini » : « sur un scénario où l'action métier
 * réussit mais la réponse échoue ensuite (à simuler avec un point d'injection de panne), la ligne
 * d'audit existe déjà en base — elle a précédé, pas suivi. »
 *
 * **Le point d'injection de panne** : [buildResponseThatAlwaysFails] — la fonction qui, dans un
 * appelant réel, transformerait le résultat métier renvoyé par [AuditLogUseCase.execute] en
 * réponse HTTP/MCP. Elle est délibérément placée EN DEHORS de [AuditLogUseCase.execute] : c'est
 * exactement le découpage que la classe impose (KDoc `execute`, « l'écriture d'audit précède la
 * réponse ») — tout ce que l'appelant fait du résultat, y compris échouer, s'exécute après que la
 * ligne d'audit soit déjà validée en base par une vraie instance MongoDB (Testcontainers, pas de
 * fake). Ce n'est pas un contournement du test : `execute()` étant une fonction synchrone,
 * l'insertion Mongo est déjà commitée au moment où elle rend la main — rien de ce que fait
 * l'appelant ensuite ne peut plus l'annuler ou la retarder.
 */
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
        storeRepository.save(StoreFixtures().build()).fold({ error("fixture setup failed: $it") }, { it.id.value })

    private fun registerIdentity(): String =
        identityRepository.save(IdentityFixtures().build()).fold({ error("fixture setup failed: $it") }, { it.id.value })

    /** Le point d'injection de panne décrit dans la KDoc de classe. */
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

        // `execute()` a déjà rendu la main ici — l'écriture Mongo est commitée, quoi qu'il arrive
        // ensuite.
        actionResult.isRight() shouldBe true

        val downstreamFailure = runCatching { buildResponseThatAlwaysFails(actionResult) }
        downstreamFailure.isFailure shouldBe true

        val entries = auditLogRepository.findByStore(storeId).fold({ error("unexpected left: $it") }, { it })
        entries shouldHaveSize 1
        entries.first().outcome shouldBe "ok"
        entries.first().toolName shouldBe "list_products"
    }
}
