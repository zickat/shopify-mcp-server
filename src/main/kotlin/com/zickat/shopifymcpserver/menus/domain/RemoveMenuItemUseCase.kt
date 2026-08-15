package com.zickat.shopifymcpserver.menus.domain

import arrow.core.Either
import com.zickat.shopifymcpserver.shared_kernel.UseCaseError

class RemoveMenuItemUseCase(
    private val engine: MenuRewriteEngine,
) {

    fun execute(storeId: String, menuId: String, itemId: String, withChildren: Boolean): Either<UseCaseError, RemoveMenuItemResult> {
        var removedIds: List<String> = emptyList()
        return engine.rewrite(
            storeId,
            menuId,
            parentSelector = MenuWriteDiff.parentOf(itemId),
            transform = MenuTransforms.makeRemoveTransform(itemId, withChildren),
            expectedRemovalsSelector = { tree ->
                val node = MenuWriteDiff.locateItem(tree, itemId).last()
                removedIds = if (withChildren) MenuTree.collectItemIds(listOf(node)) else listOf(itemId)
                removedIds
            },
        ).map { outcome ->
            when (outcome) {
                is MenuRewriteOutcome.Written -> RemoveMenuItemResult.removed(itemId, outcome.menu.title, outcome.menu.handle, removedIds, outcome.warnings)
                is MenuRewriteOutcome.Failed -> RemoveMenuItemResult.failed(itemId, outcome.detail)
            }
        }
    }
}
