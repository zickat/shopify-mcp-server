package com.zickat.shopifymcpserver.menus.domain

import arrow.core.left
import arrow.core.right
import com.zickat.shopifymcpserver.menus.MenusFakeRepository
import com.zickat.shopifymcpserver.menus.domain.models.MenuListing
import com.zickat.shopifymcpserver.menus.domain.models.MenuNode
import com.zickat.shopifymcpserver.shared_kernel.TechnicalError
import io.kotest.assertions.arrow.core.shouldBeLeft
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import org.junit.jupiter.api.Test

class ListMenusUseCaseTest {

    private fun useCase(repository: MenusFakeRepository = MenusFakeRepository()) = ListMenusUseCase(repository)

    private fun menu(id: String = "gid://shopify/Menu/1", isDefault: Boolean = false) =
        MenuNode(id = id, handle = "main-menu", title = "Main menu", isDefault = isDefault, items = emptyList())

    @Test
    fun `execute should report an empty result and hadQuery false when there is no query`() {
        val repository = MenusFakeRepository().apply { listResponse = MenuListing(emptyList(), truncated = false).right() }

        val result = useCase(repository).execute("store-1", null, null).shouldBeRight()

        result.blocks.shouldBeEmpty()
        result.hadQuery shouldBe false
        result.truncated shouldBe false
    }

    @Test
    fun `execute should report hadQuery true when a non-blank query is given`() {
        val repository = MenusFakeRepository().apply { listResponse = MenuListing(emptyList(), truncated = false).right() }

        val result = useCase(repository).execute("store-1", "title:*footer*", null).shouldBeRight()

        result.hadQuery shouldBe true
        repository.listCalls.single().query shouldBe "title:*footer*"
    }

    @Test
    fun `execute should trim a blank query down to null and report hadQuery false`() {
        val repository = MenusFakeRepository().apply { listResponse = MenuListing(emptyList(), truncated = false).right() }

        val result = useCase(repository).execute("store-1", "   ", null).shouldBeRight()

        repository.listCalls.single().query shouldBe null
        result.hadQuery shouldBe false
    }

    @Test
    fun `execute should render one block per menu returned by the repository`() {
        val repository = MenusFakeRepository().apply {
            listResponse = MenuListing(listOf(menu("gid://shopify/Menu/1"), menu("gid://shopify/Menu/2")), truncated = false).right()
        }

        val result = useCase(repository).execute("store-1", null, null).shouldBeRight()

        result.blocks.size shouldBe 2
        result.blocks[0] shouldBe MenuTreeRenderer.formatMenuBlock(menu("gid://shopify/Menu/1"), 3)
        result.blocks[1] shouldBe MenuTreeRenderer.formatMenuBlock(menu("gid://shopify/Menu/2"), 3)
    }

    @Test
    fun `execute should pass the truncated flag from the repository through untouched`() {
        val repository = MenusFakeRepository().apply { listResponse = MenuListing(listOf(menu()), truncated = true).right() }

        val result = useCase(repository).execute("store-1", null, null).shouldBeRight()

        result.truncated shouldBe true
    }

    @Test
    fun `execute should render with the default display depth of 3 when depth is not given`() {
        val nested = menu().copy(items = MenuTreeFixtures.tree())
        val repository = MenusFakeRepository().apply { listResponse = MenuListing(listOf(nested), truncated = false).right() }

        val result = useCase(repository).execute("store-1", null, null).shouldBeRight()

        result.blocks.single() shouldBe MenuTreeRenderer.formatMenuBlock(nested, 3)
    }

    @Test
    fun `execute should clamp an out-of-range depth into the 1 to 4 window instead of failing`() {
        val nested = menu().copy(items = MenuTreeFixtures.tree())
        val repository = MenusFakeRepository().apply { listResponse = MenuListing(listOf(nested), truncated = false).right() }

        val result = useCase(repository).execute("store-1", null, 99).shouldBeRight()

        result.blocks.single() shouldBe MenuTreeRenderer.formatMenuBlock(nested, 4)
    }

    @Test
    fun `execute should propagate a Left when the repository fails technically`() {
        val repository = MenusFakeRepository().apply { listResponse = TechnicalError("shopify.graphql.response.malformed").left() }

        useCase(repository).execute("store-1", null, null).shouldBeLeft()
            .shouldBeInstanceOf<TechnicalError>().messageKey shouldBe "shopify.graphql.response.malformed"
    }
}
