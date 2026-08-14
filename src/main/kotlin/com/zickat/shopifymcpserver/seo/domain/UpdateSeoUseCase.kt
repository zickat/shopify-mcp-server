package com.zickat.shopifymcpserver.seo.domain

import arrow.core.Either
import arrow.core.raise.either
import com.zickat.shopifymcpserver.seo.domain.models.SeoResourceType
import com.zickat.shopifymcpserver.seo.domain.repositories.SeoRepository
import com.zickat.shopifymcpserver.seo.domain.repositories.SeoWriteOutcome
import com.zickat.shopifymcpserver.seo.exposed_interface.model.UpdateSeoResult
import com.zickat.shopifymcpserver.shared_kernel.UseCaseError

class UpdateSeoUseCase(
    private val seoRepository: SeoRepository,
) {

    fun execute(
        storeId: String,
        resourceType: SeoResourceType,
        resourceId: String,
        seoTitle: String?,
        seoDescription: String?,
    ): Either<UseCaseError, UpdateSeoResult> = either {
        if (seoTitle == null && seoDescription == null) return@either UpdateSeoResult.NoOp

        when (val outcome = seoRepository.update(storeId, resourceType, resourceId, seoTitle, seoDescription).bind()) {
            is SeoWriteOutcome.Updated -> UpdateSeoResult.updated(
                title = outcome.resourceTitle,
                finalMetaTitle = outcome.finalMetaTitle,
                finalMetaDescription = outcome.finalMetaDescription,
                titleModified = seoTitle != null,
                descriptionModified = seoDescription != null,
            )
            is SeoWriteOutcome.NotFound -> UpdateSeoResult.NotFound
            is SeoWriteOutcome.Failed -> UpdateSeoResult.failed(outcome.detail)
        }
    }
}
