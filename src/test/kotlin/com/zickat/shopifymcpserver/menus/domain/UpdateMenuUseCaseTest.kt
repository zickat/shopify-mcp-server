package com.zickat.shopifymcpserver.menus.domain

import com.zickat.shopifymcpserver.menus.MenusFakeRepository
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class UpdateMenuUseCaseTest {

    private fun useCase(repository: MenusFakeRepository = MenusFakeRepository()) = UpdateMenuUseCase(MenuRewriteEngine(repository))

    @Test
    fun `execute should refuse before any read when neither title nor handle is supplied`() {
        val repository = MenusFakeRepository()

        val result = useCase(repository).execute("store-1", "gid://shopify/Menu/1", null, null, false).shouldBeRight()

        result.outcome shouldBe UpdateMenuOutcome.NO_FIELD_PROVIDED
        repository.fetchCalls.shouldBeEmpty()
    }

    @Test
    fun `execute should refuse before any read when handle is supplied without confirmation`() {
        val repository = MenusFakeRepository()

        val result = useCase(repository).execute("store-1", "gid://shopify/Menu/1", null, "new-handle", false).shouldBeRight()

        result.outcome shouldBe UpdateMenuOutcome.HANDLE_CHANGE_NOT_CONFIRMED
        result.requestedHandle shouldBe "new-handle"
        repository.fetchCalls.shouldBeEmpty()
    }
}
