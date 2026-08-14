package com.zickat.shopifymcpserver.pages.domain

import arrow.core.right
import com.zickat.shopifymcpserver.pages.PageFakeRepository
import com.zickat.shopifymcpserver.pages.domain.repositories.PageWriteOutcome
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import org.junit.jupiter.api.Test

class CreatePageUseCaseTest {

    @Test
    fun `execute should report failure when the repository reports userErrors`() {
        val repository = PageFakeRepository().apply {
            createResponse = PageWriteOutcome.Failed("title : can't be blank").right()
        }
        val useCase = CreatePageUseCase(repository)

        val result = useCase.execute("store-1", "", "<p>x</p>", null, null).shouldBeRight()

        result.outcome shouldBe CreatePageOutcome.FAILED
        requireNotNull(result.failureDetail) shouldContain "can't be blank"
    }

    @Test
    fun `execute should default to a draft when publish is omitted, and report the effective handle`() {
        val repository = PageFakeRepository().apply {
            createResponse = PageWriteOutcome.Success(
                PageNative(id = "gid://shopify/Page/1", title = "T", handle = "t", isPublished = false, body = ""),
            ).right()
        }
        val useCase = CreatePageUseCase(repository)

        val result = useCase.execute("store-1", "T", "<p>x</p>", null, null).shouldBeRight()

        result.outcome shouldBe CreatePageOutcome.CREATED
        result.isPublished shouldBe false
        result.handle shouldBe "t"
        result.pageId shouldBe "gid://shopify/Page/1"
    }
}
