package com.zickat.shopifymcpserver.products.domain

import arrow.core.right
import com.zickat.shopifymcpserver.products.ProductsFakeRepository
import com.zickat.shopifymcpserver.products.domain.models.ProductEnrichedFetch
import com.zickat.shopifymcpserver.products.domain.models.RelatedGuidesSource
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class GetEnrichedContentUseCaseTest {

    private fun useCase(repository: ProductsFakeRepository = ProductsFakeRepository()) = GetEnrichedContentUseCase(repository)

    private fun fetch(
        contentStatusValue: String? = null,
        summaryPoints: List<String> = emptyList(),
        relatedGuidesSourceRaw: String? = null,
    ) = ProductEnrichedFetch(
        title = "T",
        descriptionHtml = "",
        status = "DRAFT",
        contentStatusValue = contentStatusValue,
        productType = "",
        tags = emptyList(),
        originalTitle = null,
        originalDescriptionHtml = null,
        summaryPoints = summaryPoints,
        whyRecommend = "",
        howToUse = "",
        specs = emptyList(),
        faq = emptyList(),
        complementaryProducts = emptyList(),
        relatedGuides = emptyList(),
        relatedGuidesSourceRaw = relatedGuidesSourceRaw,
        idealFor = emptyList(),
    )

    @Test
    fun `should return NotFound carrying the productId when the repository returns null`() {
        val repository = ProductsFakeRepository().apply { enrichedContentResponse = null.right() }

        val result = useCase(repository).execute("store-1", "gid://shopify/Product/999").shouldBeRight()

        result.outcome shouldBe GetEnrichedContentOutcome.NOT_FOUND
        result.productId shouldBe "gid://shopify/Product/999"
    }

    @Test
    fun `should derive untreated when neither content_status nor summary_points is set`() {
        val repository = ProductsFakeRepository().apply { enrichedContentResponse = fetch().right() }

        val result = useCase(repository).execute("store-1", "gid://shopify/Product/1").shouldBeRight()

        result.snapshot?.pipelineStatus shouldBe "untreated"
    }

    @Test
    fun `should derive published when summary_points exist but content_status is absent — BUG-01`() {
        val repository = ProductsFakeRepository().apply {
            enrichedContentResponse = fetch(summaryPoints = listOf("a")).right()
        }

        val result = useCase(repository).execute("store-1", "gid://shopify/Product/1").shouldBeRight()

        result.snapshot?.pipelineStatus shouldBe "published"
    }

    @Test
    fun `should keep the explicit content_status when present`() {
        val repository = ProductsFakeRepository().apply {
            enrichedContentResponse = fetch(contentStatusValue = "to_review").right()
        }

        val result = useCase(repository).execute("store-1", "gid://shopify/Product/1").shouldBeRight()

        result.snapshot?.pipelineStatus shouldBe "to_review"
    }

    @Test
    fun `should derive MANUAL only when the raw source is exactly manual`() {
        val repository = ProductsFakeRepository().apply {
            enrichedContentResponse = fetch(relatedGuidesSourceRaw = "manual").right()
        }

        val result = useCase(repository).execute("store-1", "gid://shopify/Product/1").shouldBeRight()

        result.snapshot?.relatedGuidesSource shouldBe RelatedGuidesSource.MANUAL
    }

    @Test
    fun `should derive AUTO for any raw source other than manual, including null`() {
        val repository = ProductsFakeRepository().apply { enrichedContentResponse = fetch(relatedGuidesSourceRaw = null).right() }

        val result = useCase(repository).execute("store-1", "gid://shopify/Product/1").shouldBeRight()

        result.snapshot?.relatedGuidesSource shouldBe RelatedGuidesSource.AUTO
    }
}
