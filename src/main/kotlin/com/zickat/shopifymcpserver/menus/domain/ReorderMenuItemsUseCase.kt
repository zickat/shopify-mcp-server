package com.zickat.shopifymcpserver.menus.domain

import arrow.core.Either
import com.zickat.shopifymcpserver.shared_kernel.UseCaseError

class ReorderMenuItemsUseCase(
    private val engine: MenuRewriteEngine,
) {

    fun execute(storeId: String, menuId: String, parentItemId: String?, orderedItemIds: List<String>): Either<UseCaseError, ReorderMenuItemsResult> {
        val scopeLabel = if (parentItemId != null) "enfants de $parentItemId" else "de premier niveau"
        return engine.rewrite(
            storeId,
            menuId,
            parentSelector = { parentItemId },
            transform = MenuTransforms.makeReorderTransform(orderedItemIds, scopeLabel),
        ).map { outcome ->
            when (outcome) {
                is MenuRewriteOutcome.Written -> ReorderMenuItemsResult.reordered(outcome.menu.title, outcome.menu.handle, scopeLabel, orderedItemIds, outcome.warnings)
                is MenuRewriteOutcome.Failed -> ReorderMenuItemsResult.failed(outcome.detail)
            }
        }
    }
}
