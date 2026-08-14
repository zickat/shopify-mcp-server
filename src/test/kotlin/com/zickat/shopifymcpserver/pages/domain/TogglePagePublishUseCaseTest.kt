package com.zickat.shopifymcpserver.pages.domain

import arrow.core.right
import com.zickat.shopifymcpserver.pages.PageFakeRepository
import com.zickat.shopifymcpserver.pages.domain.repositories.PageWriteOutcome
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import org.junit.jupiter.api.Test

class TogglePagePublishUseCaseTest {

    @Test
    fun `execute should return NOT_FOUND before any write when the Page does not exist`() {
        val repository = PageFakeRepository()
        val useCase = TogglePagePublishUseCase(repository)

        val result = useCase.execute("store-1", "gid://shopify/Page/404", target = true).shouldBeRight()

        result.outcome shouldBe TogglePagePublishOutcome.NOT_FOUND
        repository.setPublishedCalls.shouldHaveSize(0)
    }

    @Test
    fun `execute should be a no-op and emit no mutation when the Page is already in the target state`() {
        val repository = PageFakeRepository().apply {
            byId["gid://shopify/Page/1"] = PageNative(id = "gid://shopify/Page/1", title = "T", handle = "t", isPublished = true, body = "")
        }
        val useCase = TogglePagePublishUseCase(repository)

        val result = useCase.execute("store-1", "gid://shopify/Page/1", target = true).shouldBeRight()

        result.outcome shouldBe TogglePagePublishOutcome.NO_OP
        repository.setPublishedCalls.shouldHaveSize(0)
    }

    @Test
    fun `execute should toggle the Page and report the new state`() {
        val repository = PageFakeRepository().apply {
            byId["gid://shopify/Page/1"] = PageNative(id = "gid://shopify/Page/1", title = "T", handle = "t", isPublished = false, body = "")
            setPublishedResponse = PageWriteOutcome.Success(
                PageNative(id = "gid://shopify/Page/1", title = "T", handle = "t", isPublished = true, body = ""),
            ).right()
        }
        val useCase = TogglePagePublishUseCase(repository)

        val result = useCase.execute("store-1", "gid://shopify/Page/1", target = true).shouldBeRight()

        result.outcome shouldBe TogglePagePublishOutcome.TOGGLED
        result.isPublished shouldBe true
        result.targetPublished shouldBe true
    }

    @Test
    fun `execute should report failure when the repository reports userErrors`() {
        val repository = PageFakeRepository().apply {
            byId["gid://shopify/Page/1"] = PageNative(id = "gid://shopify/Page/1", title = "T", handle = "t", isPublished = false, body = "")
            setPublishedResponse = PageWriteOutcome.Failed("access denied").right()
        }
        val useCase = TogglePagePublishUseCase(repository)

        val result = useCase.execute("store-1", "gid://shopify/Page/1", target = true).shouldBeRight()

        result.outcome shouldBe TogglePagePublishOutcome.FAILED
        requireNotNull(result.failureDetail) shouldContain "access denied"
    }
}
