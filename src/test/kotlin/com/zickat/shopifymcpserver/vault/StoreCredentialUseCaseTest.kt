package com.zickat.shopifymcpserver.vault

import com.zickat.shopifymcpserver.tenancy.StoreExposedServiceFake
import com.zickat.shopifymcpserver.vault.domain.StoreCredentialUseCase
import com.zickat.shopifymcpserver.vault.domain.models.StoreCredentialId
import com.zickat.shopifymcpserver.vault.domain.repositories.ACTIVE_MASTER_KEY_REF
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

/** `LOT0-04` — round-trip et rotation du coffre, orchestrés au niveau du use case (fakes uniquement). */
class StoreCredentialUseCaseTest {

    private val storeExposedService = StoreExposedServiceFake().apply {
        existing["store-1"] = false // existe, pas archivée
        existing["store-2"] = false
    }
    private val repository = StoreCredentialFakeRepository(storeExposedService)
    private val masterKeyProvider = MasterKeyProviderFake()
    private val useCase = StoreCredentialUseCase(repository, masterKeyProvider)

    private fun StoreCredentialUseCase.storeOrFail(storeId: String, plaintext: ByteArray, scopesGranted: String = "read_products"): StoreCredentialId =
        store(storeId, plaintext, scopesGranted).fold({ error("store failed: $it") }, { it })

    private fun StoreCredentialUseCase.revealOrFail(id: StoreCredentialId): ByteArray =
        reveal(id).fold({ error("reveal failed: $it") }, { it })

    @Test
    fun `store then reveal should return the original plaintext`() {
        val plaintext = "shpat_super-secret-admin-token".toByteArray()

        val id = useCase.storeOrFail("store-1", plaintext)
        val revealed = useCase.revealOrFail(id)

        revealed.contentEquals(plaintext) shouldBe true
    }

    @Test
    fun `stored credential should never carry the plaintext in ciphertext or wrappedDek`() {
        val plaintext = "shpat_super-secret-admin-token".toByteArray()

        val id = useCase.storeOrFail("store-1", plaintext)
        val stored = repository.store.getValue(id.value)

        stored.ciphertext.contentEquals(plaintext) shouldBe false
        stored.wrappedDek.contentEquals(plaintext) shouldBe false
    }

    @Test
    fun `two credentials for the same secret on different stores should have different ciphertext and wrappedDek`() {
        val plaintext = "same-secret".toByteArray()

        val firstId = useCase.storeOrFail("store-1", plaintext)
        val secondId = useCase.storeOrFail("store-2", plaintext)

        val first = repository.store.getValue(firstId.value)
        val second = repository.store.getValue(secondId.value)

        first.ciphertext.contentEquals(second.ciphertext) shouldBe false
        first.wrappedDek.contentEquals(second.wrappedDek) shouldBe false
    }

    @Test
    fun `store should fail when the master key cannot be resolved`() {
        masterKeyProvider.remove(ACTIVE_MASTER_KEY_REF)

        val result = useCase.store("store-1", "shpat_secret".toByteArray(), "read_products")

        result.isLeft() shouldBe true
    }

    @Test
    fun `rotate should change ciphertext and wrappedDek but keep the credential id`() {
        val originalId = useCase.storeOrFail("store-1", "shpat_original".toByteArray())
        val before = repository.store.getValue(originalId.value)

        val rotatedId = useCase.rotate(originalId, "shpat_rotated".toByteArray())
            .fold({ error("rotate failed: $it") }, { it })
        val after = repository.store.getValue(rotatedId.value)

        rotatedId shouldBe originalId
        after.ciphertext.contentEquals(before.ciphertext) shouldBe false
        after.wrappedDek.contentEquals(before.wrappedDek) shouldBe false
        (after.rotatedAt != null) shouldBe true

        useCase.revealOrFail(rotatedId).contentEquals("shpat_rotated".toByteArray()) shouldBe true
    }
}
