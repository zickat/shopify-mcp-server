package com.zickat.shopifymcpserver.seo.domain

import arrow.core.Either
import arrow.core.raise.either
import com.zickat.shopifymcpserver.seo.domain.models.SeoResourceType
import com.zickat.shopifymcpserver.seo.domain.repositories.SeoRepository
import com.zickat.shopifymcpserver.shared_kernel.UseCaseError

class GetSeoUseCase(
    private val seoRepository: SeoRepository,
) {

    fun execute(storeId: String, resourceType: SeoResourceType, resourceId: String): Either<UseCaseError, GetSeoResult> = either {
        val snapshot = seoRepository.get(storeId, resourceType, resourceId).bind()
        snapshot?.let { GetSeoResult.found(it.title, it.metaTitle, it.metaDescription) } ?: GetSeoResult.NotFound
    }
}
