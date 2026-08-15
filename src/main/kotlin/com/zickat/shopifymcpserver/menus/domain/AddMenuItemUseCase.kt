package com.zickat.shopifymcpserver.menus.domain

import arrow.core.Either
import arrow.core.right
import com.zickat.shopifymcpserver.shared_kernel.UseCaseError

class AddMenuItemUseCase(
    private val engine: MenuRewriteEngine,
) {

    fun execute(
        storeId: String,
        menuId: String,
        title: String,
        resourceId: String?,
        url: String?,
        parentItemId: String?,
        position: Int?,
    ): Either<UseCaseError, AddMenuItemResult> {
        val newItem = try {
            MenuItemFactory.buildNewMenuItem(NewMenuItemInput(title, resourceId, url), "Item \"$title\"")
        } catch (e: MenuItemValidationError) {
            return AddMenuItemResult.invalidItem(title, e.message.orEmpty()).right()
        }

        var insertedAt = 0
        return engine.rewrite(
            storeId,
            menuId,
            parentSelector = { parentItemId },
            transform = MenuTransforms.makeInsertTransform(newItem, position) { at -> insertedAt = at },
        ).map { outcome ->
            when (outcome) {
                is MenuRewriteOutcome.Written -> AddMenuItemResult.added(
                    title = title,
                    menuTitle = outcome.menu.title,
                    menuHandle = outcome.menu.handle,
                    parentItemId = parentItemId,
                    position = insertedAt,
                    target = MenuTreeRenderer.describeTarget(newItem),
                    warnings = outcome.warnings,
                )
                is MenuRewriteOutcome.Failed -> AddMenuItemResult.failed(title, outcome.detail)
            }
        }
    }
}
