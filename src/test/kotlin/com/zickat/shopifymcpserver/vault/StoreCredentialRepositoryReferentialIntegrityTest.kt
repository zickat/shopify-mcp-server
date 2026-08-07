package com.zickat.shopifymcpserver.vault

import com.zickat.shopifymcpserver.vault.domain.repositories.StoreCredentialRepository
import io.kotest.matchers.shouldBe
import org.bson.types.ObjectId
import org.junit.jupiter.api.Test

interface StoreCredentialRepositoryReferentialIntegrityTest {
    val repository: StoreCredentialRepository

    fun registerStore(archived: Boolean): String

    @Test
    fun `should reject a credential for an archived store`() {
        val storeId = registerStore(archived = true)
        val credential = StoreCredentialFixtures().withStoreId(storeId).build()

        repository.save(credential).isLeft() shouldBe true
    }

    @Test
    fun `should reject a credential for a store that does not exist`() {
        val orphanStoreId = ObjectId().toHexString()
        val credential = StoreCredentialFixtures().withStoreId(orphanStoreId).build()

        repository.save(credential).isLeft() shouldBe true
    }

    @Test
    fun `should accept a credential for an existing, non-archived store`() {
        val storeId = registerStore(archived = false)
        val credential = StoreCredentialFixtures().withStoreId(storeId).build()

        repository.save(credential).isRight() shouldBe true
    }
}
