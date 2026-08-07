package com.zickat.shopifymcpserver.vault.spi.mongo

import com.zickat.shopifymcpserver.shared_kernel.NotFoundError
import com.zickat.shopifymcpserver.tenancy.StoreExposedServiceFake
import com.zickat.shopifymcpserver.vault.MasterKeyProviderFake
import com.zickat.shopifymcpserver.vault.StoreCredentialFakeRepository
import com.zickat.shopifymcpserver.vault.domain.StoreCredentialUseCase
import io.kotest.assertions.arrow.core.shouldBeLeft
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import org.junit.jupiter.api.Test

class VaultExposedServiceImplTest {

    private val storeExposedService = StoreExposedServiceFake().apply { archivedByStoreId["store-1"] = false }
    private val repository = StoreCredentialFakeRepository(storeExposedService)
    private val storeCredentialUseCase = StoreCredentialUseCase(repository, MasterKeyProviderFake())
    private val vaultExposedService = VaultExposedServiceImpl(repository, storeCredentialUseCase)

    @Test
    fun `resolveCredential should return the decrypted plaintext for a store with an active credential`() {
        val plaintext = """{"apiKey":"key","apiSecret":"secret","shopDomain":"store.myshopify.com"}""".toByteArray()
        storeCredentialUseCase.store("store-1", plaintext, "read_products").shouldBeRight()

        val revealed = vaultExposedService.resolveCredential("store-1").shouldBeRight()

        revealed.contentEquals(plaintext) shouldBe true
    }

    @Test
    fun `resolveCredential should return an explicit NotFoundError, never a null or an untyped exception, when a store has no active credential`() {
        val result = vaultExposedService.resolveCredential("store-with-no-credential")

        result.shouldBeLeft().shouldBeInstanceOf<NotFoundError>()
    }
}
