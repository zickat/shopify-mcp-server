package com.zickat.shopifymcpserver.pages.domain

import arrow.core.right
import com.zickat.shopifymcpserver.pages.PageFakeRepository
import com.zickat.shopifymcpserver.pages.domain.repositories.PageDeleteOutcome
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import org.junit.jupiter.api.Test

class DeletePageUseCaseTest {

    @Test
    fun `execute should return NOT_FOUND before any deletion when the Page does not exist`() {
        val repository = PageFakeRepository()
        val useCase = DeletePageUseCase(repository)

        val result = useCase.execute("store-1", "gid://shopify/Page/404").shouldBeRight()

        result.outcome shouldBe DeletePageOutcome.NOT_FOUND
        repository.deleteCalls.shouldHaveSize(0)
    }

    @Test
    fun `execute should delete the Page and report its title and handle read before deletion`() {
        val repository = PageFakeRepository().apply {
            byId["gid://shopify/Page/1"] = PageNative(id = "gid://shopify/Page/1", title = "Contact", handle = "contact", isPublished = true, body = "")
            deleteResponse = PageDeleteOutcome.Deleted.right()
        }
        val useCase = DeletePageUseCase(repository)

        val result = useCase.execute("store-1", "gid://shopify/Page/1").shouldBeRight()

        result.outcome shouldBe DeletePageOutcome.DELETED
        result.title shouldBe "Contact"
        result.handle shouldBe "contact"
    }

    @Test
    fun `execute should report failure when the repository reports userErrors`() {
        val repository = PageFakeRepository().apply {
            byId["gid://shopify/Page/1"] = PageNative(id = "gid://shopify/Page/1", title = "Contact", handle = "contact", isPublished = true, body = "")
            deleteResponse = PageDeleteOutcome.Failed("access denied").right()
        }
        val useCase = DeletePageUseCase(repository)

        val result = useCase.execute("store-1", "gid://shopify/Page/1").shouldBeRight()

        result.outcome shouldBe DeletePageOutcome.FAILED
        requireNotNull(result.failureDetail) shouldContain "access denied"
    }
}
