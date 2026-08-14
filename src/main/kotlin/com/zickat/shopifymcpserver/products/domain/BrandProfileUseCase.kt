package com.zickat.shopifymcpserver.products.domain

import arrow.core.Either
import arrow.core.raise.either
import arrow.core.right
import com.zickat.shopifymcpserver.products.domain.models.BrandProfile
import com.zickat.shopifymcpserver.products.domain.repositories.BrandProfileRepository
import com.zickat.shopifymcpserver.shared_kernel.UseCaseError
import java.util.concurrent.ConcurrentHashMap

class BrandProfileUseCase(
    private val repository: BrandProfileRepository,
) {
    private val cacheByStoreId = ConcurrentHashMap<String, BrandProfile>()

    fun getFor(storeId: String, brandProfileRef: String): Either<UseCaseError, BrandProfile> =
        cacheByStoreId[storeId]?.right()
            ?: either {
                val raw = repository.findRawProfile(brandProfileRef).bind()
                val profile = BrandProfileParser.parse(raw, brandProfileRef).bind()
                cacheByStoreId[storeId] = profile
                profile
            }
}
