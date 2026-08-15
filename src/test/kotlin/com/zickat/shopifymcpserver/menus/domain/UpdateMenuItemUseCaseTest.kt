package com.zickat.shopifymcpserver.menus.domain

import com.zickat.shopifymcpserver.menus.MenusFakeRepository
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class UpdateMenuItemUseCaseTest {

    private fun useCase(repository: MenusFakeRepository = MenusFakeRepository()) = UpdateMenuItemUseCase(MenuRewriteEngine(repository))

    @Test
    fun `execute should refuse before any read when no field is supplied`() {
        val repository = MenusFakeRepository()

        val result = useCase(repository).execute("store-1", "gid://shopify/Menu/1", "gid://shopify/MenuItem/1", null, null, null).shouldBeRight()

        result.outcome shouldBe UpdateMenuItemOutcome.NO_FIELD_PROVIDED
        repository.fetchCalls.shouldBeEmpty()
    }

    @Test
    fun `execute should refuse before any read when resource_id and url are both supplied`() {
        val repository = MenusFakeRepository()

        val result = useCase(repository).execute(
            "store-1", "gid://shopify/Menu/1", "gid://shopify/MenuItem/1",
            null, "gid://shopify/Collection/1", "https://example.com",
        ).shouldBeRight()

        result.outcome shouldBe UpdateMenuItemOutcome.AMBIGUOUS_TARGET
        repository.fetchCalls.shouldBeEmpty()
    }

    @Test
    fun `execute should refuse before any read when the resource_id gid prefix is unrecognized`() {
        val repository = MenusFakeRepository()

        val result = useCase(repository).execute(
            "store-1", "gid://shopify/Menu/1", "gid://shopify/MenuItem/1",
            null, "gid://shopify/Widget/1", null,
        ).shouldBeRight()

        result.outcome shouldBe UpdateMenuItemOutcome.INVALID_TARGET
        repository.fetchCalls.shouldBeEmpty()
    }
}
