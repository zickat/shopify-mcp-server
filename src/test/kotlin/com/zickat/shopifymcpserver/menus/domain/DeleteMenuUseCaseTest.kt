package com.zickat.shopifymcpserver.menus.domain

import arrow.core.right
import com.zickat.shopifymcpserver.menus.MenusFakeRepository
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class DeleteMenuUseCaseTest {

    private fun useCase(repository: MenusFakeRepository = MenusFakeRepository()) = DeleteMenuUseCase(repository)

    @Test
    fun `execute should report not found and never call delete when the menu is missing`() {
        val repository = MenusFakeRepository().apply { fetchResponse = null.right() }

        val result = useCase(repository).execute("store-1", "gid://shopify/Menu/404", false).shouldBeRight()

        result.outcome shouldBe DeleteMenuOutcome.NOT_FOUND
        repository.deleteCalls.shouldBeEmpty()
    }
}
