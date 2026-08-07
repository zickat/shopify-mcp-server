package com.zickat.shopifymcpserver.shopify.domain

import com.zickat.shopifymcpserver.shared_kernel.NotFoundError
import com.zickat.shopifymcpserver.shared_kernel.TechnicalError
import com.zickat.shopifymcpserver.shopify.ShopifyAdminHttpClientFake
import com.zickat.shopifymcpserver.vault.VaultExposedServiceFake
import arrow.core.right
import io.kotest.assertions.arrow.core.shouldBeLeft
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlinx.datetime.Instant
import kotlinx.serialization.json.JsonPrimitive
import org.junit.jupiter.api.Test

class ShopifyAdminGraphQLUseCaseTest {

    private class MutableClock(private var instant: Instant) : Clock {
        override fun now(): Instant = instant
        fun advanceTo(newInstant: Instant) {
            instant = newInstant
        }
    }

    private fun VaultExposedServiceFake.withPlainCredential(storeId: String, shopDomain: String) = apply {
        plaintextByStoreId[storeId] = """{"apiKey":"key-$storeId","apiSecret":"secret-$storeId","shopDomain":"$shopDomain"}""".toByteArray()
    }

    @Test
    fun `executeGraphQL should return the http client's data payload when the credential and token resolve successfully`() {
        val vault = VaultExposedServiceFake().withPlainCredential("store-1", "store-1.myshopify.com")
        val httpClient = ShopifyAdminHttpClientFake().apply { nextGraphQLResult = JsonPrimitive("shop-data").right() }
        val useCase = ShopifyAdminGraphQLUseCase(vault, httpClient)

        val result = useCase.executeGraphQL("store-1", "query { shop { name } }", JsonPrimitive("null"))

        result.shouldBeRight() shouldBe JsonPrimitive("shop-data")
    }

    @Test
    fun `executeGraphQL should propagate a NotFoundError and never call the http client when the store has no vault credential`() {
        val vault = VaultExposedServiceFake()
        val httpClient = ShopifyAdminHttpClientFake()
        val useCase = ShopifyAdminGraphQLUseCase(vault, httpClient)

        val error = useCase.executeGraphQL("store-with-no-credential", "query { shop { name } }", JsonPrimitive("null")).shouldBeLeft()

        error.shouldBeInstanceOf<NotFoundError>()
        httpClient.exchangeAccessTokenCallCount shouldBe 0
    }

    @Test
    fun `executeGraphQL should propagate a malformed-credential error and never call the http client when the stored plaintext is not the expected JSON shape`() {
        val vault = VaultExposedServiceFake().apply { plaintextByStoreId["store-1"] = "not-json-at-all".toByteArray() }
        val httpClient = ShopifyAdminHttpClientFake()
        val useCase = ShopifyAdminGraphQLUseCase(vault, httpClient)

        val error = useCase.executeGraphQL("store-1", "query { shop { name } }", JsonPrimitive("null")).shouldBeLeft()

        error.shouldBeInstanceOf<TechnicalError>().messageKey shouldBe "shopify.credential.malformed"
        httpClient.exchangeAccessTokenCallCount shouldBe 0
    }

    @Test
    fun `a second call within 24h for the same store should not request a new access token`() {
        val vault = VaultExposedServiceFake().withPlainCredential("store-1", "store-1.myshopify.com")
        val httpClient = ShopifyAdminHttpClientFake()
        val useCase = ShopifyAdminGraphQLUseCase(vault, httpClient)

        useCase.executeGraphQL("store-1", "query one", JsonPrimitive("null")).shouldBeRight()
        useCase.executeGraphQL("store-1", "query two", JsonPrimitive("null")).shouldBeRight()

        httpClient.exchangeAccessTokenCallCount shouldBe 1
    }

    @Test
    fun `a call made well before the 5-minute refresh margin should reuse the cached token`() {
        val clock = MutableClock(Instant.fromEpochMilliseconds(0))
        val vault = VaultExposedServiceFake().withPlainCredential("store-1", "store-1.myshopify.com")
        val httpClient = ShopifyAdminHttpClientFake()
        val useCase = ShopifyAdminGraphQLUseCase(vault, httpClient, clock)

        useCase.executeGraphQL("store-1", "query one", JsonPrimitive("null")).shouldBeRight()
        clock.advanceTo(Instant.fromEpochMilliseconds(0) + 1.hours)
        useCase.executeGraphQL("store-1", "query two", JsonPrimitive("null")).shouldBeRight()

        httpClient.exchangeAccessTokenCallCount shouldBe 1
    }

    @Test
    fun `a call made inside the 5-minute refresh margin before expiry should request a new access token`() {
        val clock = MutableClock(Instant.fromEpochMilliseconds(0))
        val vault = VaultExposedServiceFake().withPlainCredential("store-1", "store-1.myshopify.com")
        val httpClient = ShopifyAdminHttpClientFake()
        val useCase = ShopifyAdminGraphQLUseCase(vault, httpClient, clock)

        useCase.executeGraphQL("store-1", "query one", JsonPrimitive("null")).shouldBeRight()
        clock.advanceTo(Instant.fromEpochMilliseconds(0) + 24.hours - 4.minutes)
        useCase.executeGraphQL("store-1", "query two", JsonPrimitive("null")).shouldBeRight()

        httpClient.exchangeAccessTokenCallCount shouldBe 2
    }

    @Test
    fun `two different stores should have independent tokens — one store's cache never serves another store's call`() {
        val vault = VaultExposedServiceFake()
            .withPlainCredential("store-1", "store-1.myshopify.com")
            .withPlainCredential("store-2", "store-2.myshopify.com")
        val httpClient = ShopifyAdminHttpClientFake()
        val useCase = ShopifyAdminGraphQLUseCase(vault, httpClient)

        useCase.executeGraphQL("store-1", "query one", JsonPrimitive("null")).shouldBeRight()
        useCase.executeGraphQL("store-2", "query two", JsonPrimitive("null")).shouldBeRight()
        useCase.executeGraphQL("store-1", "query three", JsonPrimitive("null")).shouldBeRight()
        useCase.executeGraphQL("store-2", "query four", JsonPrimitive("null")).shouldBeRight()

        httpClient.exchangeAccessTokenCallCount shouldBe 2
        httpClient.executeGraphQLCalls.map { it.first }.toSet() shouldBe setOf("store-1.myshopify.com", "store-2.myshopify.com")
        val tokenPerDomain = httpClient.executeGraphQLCalls.toMap()
        tokenPerDomain.getValue("store-1.myshopify.com") shouldBe tokenPerDomain.getValue("store-1.myshopify.com")
        (tokenPerDomain.getValue("store-1.myshopify.com") == tokenPerDomain.getValue("store-2.myshopify.com")) shouldBe false
    }

    @Test
    fun `concurrent calls for the same store racing a cold cache should exchange exactly one access token`() {
        val vault = VaultExposedServiceFake().withPlainCredential("store-1", "store-1.myshopify.com")
        val httpClient = ShopifyAdminHttpClientFake(exchangeAccessTokenDelayMillis = 100)
        val useCase = ShopifyAdminGraphQLUseCase(vault, httpClient)
        val threadCount = 8
        val readyLatch = CountDownLatch(threadCount)
        val startLatch = CountDownLatch(1)
        val doneLatch = CountDownLatch(threadCount)

        repeat(threadCount) {
            Thread {
                readyLatch.countDown()
                startLatch.await()
                useCase.executeGraphQL("store-1", "query", JsonPrimitive("null"))
                doneLatch.countDown()
            }.start()
        }
        readyLatch.await(5, TimeUnit.SECONDS)
        startLatch.countDown()
        doneLatch.await(5, TimeUnit.SECONDS)

        httpClient.exchangeAccessTokenCallCount shouldBe 1
    }
}
