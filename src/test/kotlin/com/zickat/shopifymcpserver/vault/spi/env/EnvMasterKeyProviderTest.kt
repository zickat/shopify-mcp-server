package com.zickat.shopifymcpserver.vault.spi.env

import com.zickat.shopifymcpserver.vault.domain.crypto.EnvelopeCrypto
import com.zickat.shopifymcpserver.vault.domain.repositories.ACTIVE_MASTER_KEY_REF
import io.kotest.matchers.shouldBe
import java.security.SecureRandom
import java.util.Base64
import org.junit.jupiter.api.Test

class EnvMasterKeyProviderTest {

    private val random = SecureRandom()
    private fun randomKeyBytes() = ByteArray(EnvelopeCrypto.KEY_LENGTH_BYTES).also { random.nextBytes(it) }

    @Test
    fun `should resolve the active key ref from its default environment variable`() {
        val keyBytes = randomKeyBytes()
        val provider = EnvMasterKeyProvider(
            env = { name -> if (name == EnvMasterKeyProvider.DEFAULT_ENV_VAR) Base64.getEncoder().encodeToString(keyBytes) else null },
        )

        val resolved = provider.resolve(ACTIVE_MASTER_KEY_REF).fold({ error("expected right, got $it") }, { it })

        resolved.contentEquals(keyBytes) shouldBe true
    }

    @Test
    fun `should fail for an unknown keyRef without reading the environment at all`() {
        val provider = EnvMasterKeyProvider(env = { error("must never be called for an unmapped keyRef") })

        provider.resolve("v99").isLeft() shouldBe true
    }

    @Test
    fun `should fail when the mapped environment variable is not set`() {
        val provider = EnvMasterKeyProvider(env = { null })

        provider.resolve(ACTIVE_MASTER_KEY_REF).isLeft() shouldBe true
    }

    @Test
    fun `should fail when the environment variable is not valid base64`() {
        val provider = EnvMasterKeyProvider(env = { "not-valid-base64!!" })

        provider.resolve(ACTIVE_MASTER_KEY_REF).isLeft() shouldBe true
    }

    @Test
    fun `should fail when the decoded key is not 32 bytes`() {
        val tooShort = Base64.getEncoder().encodeToString(ByteArray(16))
        val provider = EnvMasterKeyProvider(env = { tooShort })

        provider.resolve(ACTIVE_MASTER_KEY_REF).isLeft() shouldBe true
    }

    @Test
    fun `should support resolving several key refs at once, as a future rotation requires`() {
        val currentKey = randomKeyBytes()
        val nextKey = randomKeyBytes()
        val provider = EnvMasterKeyProvider(
            keyRefToEnvVar = mapOf(ACTIVE_MASTER_KEY_REF to "CATALOG_MASTER_KEY", "v2" to "CATALOG_MASTER_KEY_V2"),
            env = { name ->
                when (name) {
                    "CATALOG_MASTER_KEY" -> Base64.getEncoder().encodeToString(currentKey)
                    "CATALOG_MASTER_KEY_V2" -> Base64.getEncoder().encodeToString(nextKey)
                    else -> null
                }
            },
        )

        val resolvedCurrent = provider.resolve(ACTIVE_MASTER_KEY_REF).fold({ error("expected right, got $it") }, { it })
        val resolvedNext = provider.resolve("v2").fold({ error("expected right, got $it") }, { it })

        resolvedCurrent.contentEquals(currentKey) shouldBe true
        resolvedNext.contentEquals(nextKey) shouldBe true
    }
}
