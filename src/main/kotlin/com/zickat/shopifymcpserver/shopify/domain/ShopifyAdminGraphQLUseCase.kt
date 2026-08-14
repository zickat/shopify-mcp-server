package com.zickat.shopifymcpserver.shopify.domain

import arrow.core.Either
import arrow.core.raise.either
import com.zickat.shopifymcpserver.shared_kernel.UseCaseError
import com.zickat.shopifymcpserver.shopify.domain.models.ShopifyCredential
import com.zickat.shopifymcpserver.shopify.domain.repositories.ShopifyAdminHttpClient
import com.zickat.shopifymcpserver.vault.exposed_interface.VaultExposedService
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlinx.datetime.Instant
import kotlinx.serialization.json.JsonElement

class ShopifyAdminGraphQLUseCase(
    private val vaultExposedService: VaultExposedService,
    private val httpClient: ShopifyAdminHttpClient,
    private val clock: Clock = Clock.System,
) {
    private val tokensByStore = ConcurrentHashMap<String, CachedToken>()
    private val refreshLocksByStore = ConcurrentHashMap<String, ReentrantLock>()

    fun executeGraphQL(storeId: String, query: String, variables: JsonElement): Either<UseCaseError, JsonElement> = either {
        val credential = resolveCredential(storeId).bind()
        val accessToken = resolveAccessToken(storeId, credential).bind()
        httpClient.executeGraphQL(credential.shopDomain, accessToken, query, variables).bind()
    }

    private fun resolveCredential(storeId: String): Either<UseCaseError, ShopifyCredential> = either {
        val plaintext = vaultExposedService.resolveCredential(storeId).bind()
        ShopifyCredential.fromPlaintext(plaintext).bind()
    }

    private fun resolveAccessToken(storeId: String, credential: ShopifyCredential): Either<UseCaseError, String> {
        validCachedTokenFor(storeId)?.let { return Either.Right(it) }

        val lock = refreshLocksByStore.computeIfAbsent(storeId) { ReentrantLock() }
        return lock.withLock {
            validCachedTokenFor(storeId)?.let { return@withLock Either.Right(it) }

            httpClient.exchangeAccessToken(credential.shopDomain, credential.apiKey, credential.apiSecret).map { token ->
                tokensByStore[storeId] = CachedToken(token.value, clock.now() + TOKEN_TTL)
                token.value
            }
        }
    }

    private fun validCachedTokenFor(storeId: String): String? {
        val cached = tokensByStore[storeId] ?: return null
        return if (clock.now() < cached.expiresAt - REFRESH_MARGIN) cached.accessToken else null
    }

    private data class CachedToken(val accessToken: String, val expiresAt: Instant)

    companion object {
        private val TOKEN_TTL = 24.hours
        private val REFRESH_MARGIN = 5.minutes
    }
}
