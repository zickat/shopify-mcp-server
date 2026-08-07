package com.zickat.shopifymcpserver.vault

import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import com.zickat.shopifymcpserver.tenancy.StoreExposedServiceFake
import com.zickat.shopifymcpserver.vault.domain.StoreCredentialUseCase
import com.zickat.shopifymcpserver.vault.domain.repositories.ACTIVE_MASTER_KEY_REF
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.matchers.shouldBe
import java.util.Base64
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.slf4j.LoggerFactory

class MasterKeyLoggingSafetyTest {

    private val storeExposedService = StoreExposedServiceFake().apply { archivedByStoreId["store-1"] = false }
    private val repository = StoreCredentialFakeRepository(storeExposedService)

    private lateinit var appender: ListAppender<ILoggingEvent>
    private lateinit var logger: Logger

    @BeforeEach
    fun attachAppender() {
        logger = LoggerFactory.getLogger(StoreCredentialUseCase::class.java) as Logger
        appender = ListAppender<ILoggingEvent>()
        appender.start()
        logger.addAppender(appender)
    }

    @AfterEach
    fun detachAppender() {
        logger.detachAppender(appender)
        appender.stop()
    }

    @Test
    fun `should not leak anything when the master key is missing`() {
        val masterKeyProvider = MasterKeyProviderFake().remove(ACTIVE_MASTER_KEY_REF)
        val useCase = StoreCredentialUseCase(repository, masterKeyProvider)

        val result = useCase.store("store-1", "shpat_secret".toByteArray(), "read_products")

        result.isLeft() shouldBe true
    }

    @Test
    fun `should not leak the master key value when decryption fails with the key actually present`() {
        val masterKeyProvider = MasterKeyProviderFake()
        val masterKeyBytes = masterKeyProvider.keyFor(ACTIVE_MASTER_KEY_REF)
        val masterKeyBase64 = Base64.getEncoder().encodeToString(masterKeyBytes)
        val useCase = StoreCredentialUseCase(repository, masterKeyProvider)

        val id = useCase.store("store-1", "shpat_super-secret-admin-token".toByteArray(), "read_products")
            .shouldBeRight()

        val stored = repository.store.getValue(id.value)
        val tamperedWrappedDek = stored.wrappedDek.copyOf()
        tamperedWrappedDek[tamperedWrappedDek.size - 1] =
            (tamperedWrappedDek[tamperedWrappedDek.size - 1].toInt() xor 0xFF).toByte()
        repository.store[id.value] = stored.copy(wrappedDek = tamperedWrappedDek)

        val result = useCase.reveal(id)

        result.isLeft() shouldBe true

        val loggedText = appender.list.joinToString("\n") { event ->
            val throwableText = event.throwableProxy?.let { proxy ->
                generateSequence(proxy) { it.cause }.joinToString("\n") { it.message.orEmpty() }
            }.orEmpty()
            "${event.formattedMessage}\n$throwableText"
        }

        appender.list.isEmpty() shouldBe false
        loggedText.contains(masterKeyBase64) shouldBe false
    }
}
