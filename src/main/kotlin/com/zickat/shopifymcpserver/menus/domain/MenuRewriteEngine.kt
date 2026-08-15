package com.zickat.shopifymcpserver.menus.domain

import arrow.core.Either
import arrow.core.flatMap
import arrow.core.right
import com.zickat.shopifymcpserver.menus.domain.models.MenuItemNode
import com.zickat.shopifymcpserver.menus.domain.models.MenuNode
import com.zickat.shopifymcpserver.menus.domain.repositories.MenuUpdateOutcome
import com.zickat.shopifymcpserver.menus.domain.repositories.MenusRepository
import com.zickat.shopifymcpserver.shared_kernel.UseCaseError

sealed interface MenuRewriteOutcome {
    data class Written(val menu: MenuNode, val warnings: List<String>) : MenuRewriteOutcome
    data class Failed(val detail: String) : MenuRewriteOutcome
}

data class MenuRewriteOptions(
    val title: String? = null,
    val handle: String? = null,
    val precheck: ((MenuNode) -> String?)? = null,
)

class MenuRewriteEngine(
    private val menusRepository: MenusRepository,
) {

    fun rewrite(
        storeId: String,
        menuId: String,
        parentSelector: (List<MenuItemNode>) -> String?,
        transform: (List<MenuItemNode>, Int) -> List<MenuItemNode>,
        expectedRemovalsSelector: (List<MenuItemNode>) -> List<String> = { emptyList() },
        options: MenuRewriteOptions = MenuRewriteOptions(),
    ): Either<UseCaseError, MenuRewriteOutcome> =
        menusRepository.fetch(storeId, menuId).flatMap { menu ->
            when {
                menu == null -> MenuRewriteOutcome.Failed("Menu introuvable : $menuId").right()
                else -> options.precheck?.invoke(menu)?.let { detail -> MenuRewriteOutcome.Failed(detail).right() }
                    ?: applyGuardsAndWrite(storeId, menu, parentSelector, transform, expectedRemovalsSelector, options)
            }
        }

    private fun applyGuardsAndWrite(
        storeId: String,
        menu: MenuNode,
        parentSelector: (List<MenuItemNode>) -> String?,
        transform: (List<MenuItemNode>, Int) -> List<MenuItemNode>,
        expectedRemovalsSelector: (List<MenuItemNode>) -> List<String>,
        options: MenuRewriteOptions,
    ): Either<UseCaseError, MenuRewriteOutcome> {
        val rebuiltAndRemovals = try {
            MenuIntegrityGuards.assertNoUnreadableDepth(menu.items)
            val parentItemId = parentSelector(menu.items)
            val expectedRemovals = expectedRemovalsSelector(menu.items)
            val rebuilt = MenuTree.mapChildList(menu.items, parentItemId, transform)
            MenuIntegrityGuards.assertNoSilentLoss(menu.items, rebuilt, expectedRemovals)
            MenuIntegrityGuards.assertDepthNotWorsened(menu.items, rebuilt)
            rebuilt to expectedRemovals
        } catch (e: MenuItemValidationError) {
            return MenuRewriteOutcome.Failed(e.message.orEmpty()).right()
        }

        val (rebuiltItems, expectedRemovals) = rebuiltAndRemovals
        return menusRepository.rewrite(storeId, menu.id, options.title ?: menu.title, options.handle ?: menu.handle, rebuiltItems)
            .map { outcome ->
                when (outcome) {
                    is MenuUpdateOutcome.Success -> MenuRewriteOutcome.Written(
                        menu = menu,
                        warnings = MenuWriteDiff.diffAfterWrite(menu.items, outcome.items, expectedRemovals),
                    )
                    is MenuUpdateOutcome.Failed -> MenuRewriteOutcome.Failed(outcome.detail)
                }
            }
    }
}
