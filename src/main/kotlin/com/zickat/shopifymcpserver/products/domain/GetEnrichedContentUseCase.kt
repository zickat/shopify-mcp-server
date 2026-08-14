package com.zickat.shopifymcpserver.products.domain

import arrow.core.Either
import com.zickat.shopifymcpserver.products.domain.models.ProductEnrichedFetch
import com.zickat.shopifymcpserver.products.domain.models.ProductEnrichedSnapshot
import com.zickat.shopifymcpserver.products.domain.repositories.ProductRepository
import com.zickat.shopifymcpserver.shared_kernel.UseCaseError

class GetEnrichedContentUseCase(
    private val productRepository: ProductRepository,
) {

    fun execute(storeId: String, productId: String): Either<UseCaseError, GetEnrichedContentResult> =
        productRepository.enrichedContent(storeId, productId).map { fetch ->
            fetch?.let { GetEnrichedContentResult.found(it.toSnapshot()) } ?: GetEnrichedContentResult.notFound(productId)
        }

    private fun ProductEnrichedFetch.toSnapshot(): ProductEnrichedSnapshot =
        ProductEnrichedSnapshot(
            title = title,
            descriptionHtml = descriptionHtml,
            status = status,
            pipelineStatus = deriveContentStatus(contentStatusValue, summaryPoints.isNotEmpty()),
            productType = productType,
            tags = tags,
            originalTitle = originalTitle,
            originalDescriptionHtml = originalDescriptionHtml,
            summaryPoints = summaryPoints,
            whyRecommend = whyRecommend,
            howToUse = howToUse,
            specs = specs,
            faq = faq,
            complementaryProducts = complementaryProducts,
            relatedGuides = relatedGuides,
            relatedGuidesSource = deriveRelatedGuidesSource(relatedGuidesSourceRaw),
            idealFor = idealFor,
        )
}
