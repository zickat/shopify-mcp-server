package com.zickat.shopifymcpserver.pages.domain

import com.zickat.shopifymcpserver.pages.PageFakeRepository
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class GetPageMetafieldsUseCaseTest {

    @Test
    fun `execute should report page not found when the page does not exist`() {
        val repository = PageFakeRepository()
        val useCase = GetPageMetafieldsUseCase(repository)

        val result = useCase.execute("store-1", "gid://shopify/Page/999", null).shouldBeRight()

        result.outcome shouldBe GetPageMetafieldsOutcome.NOT_FOUND
        result.pageId shouldBe "gid://shopify/Page/999"
    }

    @Test
    fun `execute should report no metafield when the page has none`() {
        val repository = PageFakeRepository().apply {
            metafieldsById["gid://shopify/Page/1"] = PageMetafieldsSnapshot("Contact", emptyList(), truncated = false)
        }
        val useCase = GetPageMetafieldsUseCase(repository)

        val result = useCase.execute("store-1", "gid://shopify/Page/1", null).shouldBeRight()

        result.outcome shouldBe GetPageMetafieldsOutcome.FOUND
        result.title shouldBe "Contact"
        result.metafields.shouldBeEmpty()
        result.requestedKeys shouldBe null
        result.truncated shouldBe false
    }

    @Test
    fun `execute should carry every metafield found untouched`() {
        val metafields = listOf(PageMetafield("summary", "single_line_text_field", "Résumé"))
        val repository = PageFakeRepository().apply {
            metafieldsById["gid://shopify/Page/1"] = PageMetafieldsSnapshot("Guides", metafields, truncated = false)
        }
        val useCase = GetPageMetafieldsUseCase(repository)

        val result = useCase.execute("store-1", "gid://shopify/Page/1", null).shouldBeRight()

        result.metafields.shouldContainExactly(metafields)
    }

    @Test
    fun `execute should carry the requested keys through untouched`() {
        val repository = PageFakeRepository().apply {
            metafieldsById["gid://shopify/Page/1"] = PageMetafieldsSnapshot(
                "Guides",
                listOf(
                    PageMetafield("summary", "single_line_text_field", "Résumé"),
                    PageMetafield("themes", "list.metaobject_reference", "[]"),
                ),
                truncated = false,
            )
        }
        val useCase = GetPageMetafieldsUseCase(repository)

        val result = useCase.execute("store-1", "gid://shopify/Page/1", listOf("themes", "nonexistent_key")).shouldBeRight()

        result.requestedKeys shouldBe listOf("themes", "nonexistent_key")
    }

    @Test
    fun `execute should flag a truncated metafields page explicitly`() {
        val repository = PageFakeRepository().apply {
            metafieldsById["gid://shopify/Page/1"] = PageMetafieldsSnapshot(
                "Guides",
                listOf(PageMetafield("summary", "single_line_text_field", "Résumé")),
                truncated = true,
            )
        }
        val useCase = GetPageMetafieldsUseCase(repository)

        val result = useCase.execute("store-1", "gid://shopify/Page/1", null).shouldBeRight()

        result.truncated shouldBe true
    }
}
