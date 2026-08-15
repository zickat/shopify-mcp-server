package com.zickat.shopifymcpserver.menus.domain

import com.zickat.shopifymcpserver.menus.MenusFakeRepository
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class CreateMenuUseCaseTest {

    private fun useCase(repository: MenusFakeRepository = MenusFakeRepository()) = CreateMenuUseCase(repository)

    @Test
    fun `execute should refuse before any network call when an item carries neither resource_id nor url`() {
        val repository = MenusFakeRepository()

        val result = useCase(repository).execute(
            "store-1", "guides", "Guides",
            listOf(CreateMenuItemInput(title = "Sans cible")),
        ).shouldBeRight()

        result.outcome shouldBe CreateMenuOutcome.INVALID_ITEM
        repository.createCalls.shouldBeEmpty()
    }
}
