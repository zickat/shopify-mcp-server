package com.zickat.shopifymcpserver.seo.domain.repositories

import arrow.core.Either
import com.zickat.shopifymcpserver.seo.domain.models.SeoResourceType
import com.zickat.shopifymcpserver.seo.domain.models.SeoSnapshot
import com.zickat.shopifymcpserver.shared_kernel.UseCaseError

sealed interface SeoWriteOutcome {
    data class Updated(val resourceTitle: String, val finalMetaTitle: String?, val finalMetaDescription: String?) : SeoWriteOutcome
    data object NotFound : SeoWriteOutcome
    data class Failed(val detail: String) : SeoWriteOutcome
}

interface SeoRepository {
    fun get(storeId: String, resourceType: SeoResourceType, resourceId: String): Either<UseCaseError, SeoSnapshot?>
    fun update(
        storeId: String,
        resourceType: SeoResourceType,
        resourceId: String,
        seoTitle: String?,
        seoDescription: String?,
    ): Either<UseCaseError, SeoWriteOutcome>
}
