package com.zickat.shopifymcpserver.menus.domain

import arrow.core.Either
import arrow.core.right
import com.zickat.shopifymcpserver.menus.domain.models.MenuItemNode
import com.zickat.shopifymcpserver.shared_kernel.UseCaseError

class UpdateMenuItemUseCase(
    private val engine: MenuRewriteEngine,
) {

    fun execute(
        storeId: String,
        menuId: String,
        itemId: String,
        title: String?,
        resourceId: String?,
        url: String?,
    ): Either<UseCaseError, UpdateMenuItemResult> {
        if (title == null && resourceId == null && url == null) {
            return UpdateMenuItemResult.noFieldProvided(itemId).right()
        }
        if (resourceId != null && url != null) {
            return UpdateMenuItemResult.ambiguousTarget(itemId).right()
        }

        val changes = try {
            val target = if (resourceId != null || url != null) {
                MenuItemTarget(
                    type = MenuTreeRenderer.deriveMenuItemType(resourceId, url),
                    resourceId = resourceId,
                    url = url?.let(MenuItemFactory::validateHref),
                )
            } else {
                null
            }
            MenuItemChanges(title = title, target = target)
        } catch (e: MenuItemValidationError) {
            return UpdateMenuItemResult.invalidTarget(itemId, e.message.orEmpty()).right()
        }

        var changed: Pair<MenuItemNode, MenuItemNode>? = null
        return engine.rewrite(
            storeId,
            menuId,
            parentSelector = MenuWriteDiff.parentOf(itemId),
            transform = MenuTransforms.makeUpdateItemTransform(itemId, changes) { before, after -> changed = before to after },
        ).map { outcome ->
            when (outcome) {
                is MenuRewriteOutcome.Written -> {
                    val (before, after) = requireNotNull(changed)
                    UpdateMenuItemResult.updated(
                        itemId = itemId,
                        menuTitle = outcome.menu.title,
                        menuHandle = outcome.menu.handle,
                        titleChange = if (changes.title != null) before.title to after.title else null,
                        targetChange = if (changes.target != null) {
                            MenuTreeRenderer.describeTarget(before) to MenuTreeRenderer.describeTarget(after)
                        } else {
                            null
                        },
                        childCount = after.items.size,
                        warnings = outcome.warnings,
                    )
                }
                is MenuRewriteOutcome.Failed -> UpdateMenuItemResult.failed(itemId, outcome.detail)
            }
        }
    }
}
