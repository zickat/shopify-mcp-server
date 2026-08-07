package com.zickat.shopifymcpserver.vault.spi.mongo

import com.zickat.shopifymcpserver.shared_kernel.WithMongoDBContainer
import com.zickat.shopifymcpserver.tenancy.StoreFixtures
import com.zickat.shopifymcpserver.tenancy.domain.repositories.StoreRepository
import com.zickat.shopifymcpserver.vault.MasterKeyProviderFake
import com.zickat.shopifymcpserver.vault.domain.StoreCredentialUseCase
import com.zickat.shopifymcpserver.vault.domain.repositories.MasterKeyProvider
import com.zickat.shopifymcpserver.vault.exposed_interface.VaultExposedService
import io.kotest.matchers.shouldBe
import org.bson.Document
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import org.springframework.data.mongodb.core.MongoTemplate

/**
 * `LOT0-04`, « Comment on vérifie que c'est fini » : « le document `STORE_CREDENTIAL` inséré n'est
 * jamais retourné en clair par aucun endpoint/`exposed_interface` du reste de l'application ». Ce
 * que [com.zickat.shopifymcpserver.vault.VaultExposedServiceContractTest] établit par réflexion sur
 * la *forme* de l'interface, ce test l'établit **de bout en bout** : vrai Mongo (Testcontainers),
 * vrai chiffrement, vraie boutique, vrai bean Spring `VaultExposedService` — un credential
 * réellement chiffré est en base, et tout ce qu'on peut en obtenir par la frontière du module est
 * un `Boolean`.
 *
 * [TestMasterKeyConfiguration] remplace le [MasterKeyProvider] réel (`EnvMasterKeyProvider`, qui
 * lirait une vraie variable d'environnement absente ici) par [MasterKeyProviderFake] — cohérent
 * avec `security.md` : aucune valeur de clé, même de test, ne doit passer par une variable
 * d'environnement committée ou un fichier `.env`.
 */
@SpringBootTest
class VaultExposedServiceMongoIntegrationTest : WithMongoDBContainer() {

    @TestConfiguration
    class TestMasterKeyConfiguration {
        @Bean
        @Primary
        fun testMasterKeyProvider(): MasterKeyProvider = MasterKeyProviderFake()
    }

    @Autowired
    private lateinit var storeRepository: StoreRepository

    @Autowired
    private lateinit var storeCredentialUseCase: StoreCredentialUseCase

    @Autowired
    private lateinit var vaultExposedService: VaultExposedService

    @Autowired
    private lateinit var mongoTemplate: MongoTemplate

    @BeforeEach
    fun cleanCollections() {
        mongoTemplate.getCollection(StoreCredentialEntity.COLLECTION_NAME).deleteMany(Document())
    }

    @Test
    fun `a real encrypted credential is never surfaced in clear through VaultExposedService`() {
        val storeId = storeRepository.save(StoreFixtures().build())
            .fold({ error("fixture setup failed: $it") }, { it.id.value })
        val plaintext = "shpat_super-secret-admin-token".toByteArray()

        storeCredentialUseCase.store(storeId, plaintext, "read_products")
            .fold({ error("store failed: $it") }, { it })

        // La base contient bien un secret chiffré, différent du texte en clair — sinon ce test ne
        // prouverait rien (un champ vide "passerait" trivialement le test qui suit).
        val rawDocument = mongoTemplate.getCollection(StoreCredentialEntity.COLLECTION_NAME).find().first()!!
        val storedCiphertext = (rawDocument["ciphertext"] as org.bson.types.Binary).data
        storedCiphertext.contentEquals(plaintext) shouldBe false

        // La seule chose que la frontière du module rend visible au reste de l'application.
        val result = vaultExposedService.hasActiveCredential(storeId)

        result shouldBe true
    }
}
