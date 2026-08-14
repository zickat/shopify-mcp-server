package com.zickat.shopifymcpserver.pages.domain

import arrow.core.right
import com.zickat.shopifymcpserver.pages.PageFakeRepository
import com.zickat.shopifymcpserver.pages.domain.repositories.PageMetafieldsWriteOutcome
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import org.junit.jupiter.api.Test

class UpdatePageMetafieldsUseCaseTest {

    @Test
    fun `execute should return NOT_FOUND before any write when the Page does not exist`() {
        val repository = PageFakeRepository()
        val useCase = UpdatePageMetafieldsUseCase(repository)

        val result = useCase.execute(
            "store-1",
            "gid://shopify/Page/404",
            listOf(PageMetafieldInput("themes", "single_line_text_field", "x")),
        ).shouldBeRight()

        result.outcome shouldBe UpdatePageMetafieldsOutcome.NOT_FOUND
        repository.setMetafieldsCalls.shouldHaveSize(0)
    }

    @Test
    fun `execute should report failure when the repository reports userErrors`() {
        val repository = PageFakeRepository().apply {
            metafieldsById["gid://shopify/Page/1"] = PageMetafieldsSnapshot("T", emptyList(), truncated = false)
            setMetafieldsResponse = PageMetafieldsWriteOutcome.Failed("value : invalid").right()
        }
        val useCase = UpdatePageMetafieldsUseCase(repository)

        val result = useCase.execute(
            "store-1",
            "gid://shopify/Page/1",
            listOf(PageMetafieldInput("themes", "single_line_text_field", "x")),
        ).shouldBeRight()

        result.outcome shouldBe UpdatePageMetafieldsOutcome.FAILED
        requireNotNull(result.failureDetail) shouldContain "invalid"
    }

    @Test
    fun `execute should update the metafields and report what was written`() {
        val repository = PageFakeRepository().apply {
            metafieldsById["gid://shopify/Page/1"] = PageMetafieldsSnapshot("Guides", emptyList(), truncated = false)
            setMetafieldsResponse = PageMetafieldsWriteOutcome.Updated.right()
        }
        val useCase = UpdatePageMetafieldsUseCase(repository)

        val result = useCase.execute(
            "store-1",
            "gid://shopify/Page/1",
            listOf(PageMetafieldInput("summary", "single_line_text_field", "Résumé")),
        ).shouldBeRight()

        result.outcome shouldBe UpdatePageMetafieldsOutcome.UPDATED
        result.title shouldBe "Guides"
        result.metafields.shouldContainExactly(PageMetafieldInput("summary", "single_line_text_field", "Résumé"))
    }
}
