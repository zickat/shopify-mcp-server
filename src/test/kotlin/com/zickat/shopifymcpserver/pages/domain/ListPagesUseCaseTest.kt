package com.zickat.shopifymcpserver.pages.domain

import arrow.core.right
import com.zickat.shopifymcpserver.pages.PageFakeRepository
import com.zickat.shopifymcpserver.pages.domain.repositories.PageListing
import com.zickat.shopifymcpserver.pages.domain.repositories.PageSummary
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class ListPagesUseCaseTest {

    @Test
    fun `execute without query should report an empty listing and no query`() {
        val repository = PageFakeRepository().apply { listing = PageListing(emptyList(), truncated = false).right() }
        val useCase = ListPagesUseCase(repository)

        val result = useCase.execute("store-1", null).shouldBeRight()

        result.pages.shouldBeEmpty()
        result.hadQuery shouldBe false
    }

    @Test
    fun `execute with a query should report an empty listing and hadQuery true`() {
        val repository = PageFakeRepository().apply { listing = PageListing(emptyList(), truncated = false).right() }
        val useCase = ListPagesUseCase(repository)

        val result = useCase.execute("store-1", "title:*nope*").shouldBeRight()

        result.pages.shouldBeEmpty()
        result.hadQuery shouldBe true
    }

    @Test
    fun `execute without query should list every page found`() {
        val repository = PageFakeRepository().apply {
            listing = PageListing(listOf(PageSummary("gid://shopify/Page/1", "Contact", "contact")), truncated = false).right()
        }
        val useCase = ListPagesUseCase(repository)

        val result = useCase.execute("store-1", null).shouldBeRight()

        result.pages.shouldContainExactly(PageSummary("gid://shopify/Page/1", "Contact", "contact"))
        result.truncated shouldBe false
    }

    @Test
    fun `execute should send a trimmed and blank-to-null query to the repository`() {
        val repository = PageFakeRepository().apply { listing = PageListing(emptyList(), truncated = false).right() }
        val useCase = ListPagesUseCase(repository)

        useCase.execute("store-1", "   ").shouldBeRight()

        repository.listCalls.single().query shouldBe null
    }

    @Test
    fun `execute should report a truncated listing`() {
        val pages = (1..10).map { PageSummary("gid://shopify/Page/$it", "Page $it", "page-$it") }
        val repository = PageFakeRepository().apply { listing = PageListing(pages, truncated = true).right() }
        val useCase = ListPagesUseCase(repository)

        val result = useCase.execute("store-1", null).shouldBeRight()

        result.truncated shouldBe true
        result.pages shouldBe pages
    }
}
