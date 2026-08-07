package com.zickat.shopifymcpserver.vault.spi.mongo

import com.zickat.shopifymcpserver.shared_kernel.WithMongoDBContainer
import com.zickat.shopifymcpserver.tenancy.StoreFixtures
import com.zickat.shopifymcpserver.tenancy.domain.repositories.StoreRepository
import com.zickat.shopifymcpserver.vault.MasterKeyProviderFake
import com.zickat.shopifymcpserver.vault.domain.StoreCredentialUseCase
import com.zickat.shopifymcpserver.vault.domain.repositories.MasterKeyProvider
import com.zickat.shopifymcpserver.vault.exposed_interface.VaultExposedService
import io.kotest.assertions.arrow.core.shouldBeRight
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
            .shouldBeRight().id.value
        val plaintext = "shpat_super-secret-admin-token".toByteArray()

        storeCredentialUseCase.store(storeId, plaintext, "read_products")
            .shouldBeRight()

        val rawDocument = mongoTemplate.getCollection(StoreCredentialEntity.COLLECTION_NAME).find().first()!!
        val storedCiphertext = (rawDocument["ciphertext"] as org.bson.types.Binary).data
        storedCiphertext.contentEquals(plaintext) shouldBe false

        val result = vaultExposedService.hasActiveCredential(storeId)

        result shouldBe true
    }
}
