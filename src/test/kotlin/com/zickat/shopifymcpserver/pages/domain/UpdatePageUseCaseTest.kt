package com.zickat.shopifymcpserver.pages.domain

import arrow.core.right
import com.zickat.shopifymcpserver.pages.PageFakeRepository
import com.zickat.shopifymcpserver.pages.domain.repositories.PageWriteOutcome
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import org.junit.jupiter.api.Test

class UpdatePageUseCaseTest {

    @Test
    fun `execute should report NO_OP and never call the repository when every field is omitted`() {
        val repository = PageFakeRepository()
        val useCase = UpdatePageUseCase(repository)

        val result = useCase.execute("store-1", "gid://shopify/Page/1", null, null, null).shouldBeRight()

        result.outcome shouldBe UpdatePageOutcome.NO_OP
        repository.updateCalls.shouldHaveSize(0)
    }

    @Test
    fun `execute should return NOT_FOUND before any write when the Page does not exist`() {
        val repository = PageFakeRepository()
        val useCase = UpdatePageUseCase(repository)

        val result = useCase.execute("store-1", "gid://shopify/Page/404", "New title", null, null).shouldBeRight()

        result.outcome shouldBe UpdatePageOutcome.NOT_FOUND
        repository.updateCalls.shouldHaveSize(0)
    }

    @Test
    fun `execute should update the Page and report which fields changed`() {
        val repository = PageFakeRepository().apply {
            byId["gid://shopify/Page/1"] = PageNative(id = "gid://shopify/Page/1", title = "Old", handle = "old", isPublished = true, body = "")
            updateResponse = PageWriteOutcome.Success(
                PageNative(id = "gid://shopify/Page/1", title = "New title", handle = "new-handle", isPublished = true, body = ""),
            ).right()
        }
        val useCase = UpdatePageUseCase(repository)

        val result = useCase.execute("store-1", "gid://shopify/Page/1", "New title", null, "new-handle").shouldBeRight()

        result.outcome shouldBe UpdatePageOutcome.UPDATED
        result.changedFields.shouldContainExactly("title", "handle")
        result.effectiveHandle shouldBe "new-handle"
    }

    @Test
    fun `execute should report failure when the repository reports userErrors`() {
        val repository = PageFakeRepository().apply {
            byId["gid://shopify/Page/1"] = PageNative(id = "gid://shopify/Page/1", title = "Old", handle = "old", isPublished = true, body = "")
            updateResponse = PageWriteOutcome.Failed("handle : already taken").right()
        }
        val useCase = UpdatePageUseCase(repository)

        val result = useCase.execute("store-1", "gid://shopify/Page/1", null, null, "taken-handle").shouldBeRight()

        result.outcome shouldBe UpdatePageOutcome.FAILED
        requireNotNull(result.failureDetail) shouldContain "already taken"
    }
}
