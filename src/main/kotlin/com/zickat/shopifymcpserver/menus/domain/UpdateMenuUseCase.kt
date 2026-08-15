package com.zickat.shopifymcpserver.menus.domain

import arrow.core.Either
import arrow.core.right
import com.zickat.shopifymcpserver.shared_kernel.UseCaseError

class UpdateMenuUseCase(
    private val engine: MenuRewriteEngine,
) {

    fun execute(storeId: String, menuId: String, title: String?, handle: String?, confirmHandleChange: Boolean): Either<UseCaseError, UpdateMenuResult> {
        if (title == null && handle == null) {
            return UpdateMenuResult.noFieldProvided().right()
        }
        if (handle != null && !confirmHandleChange) {
            return UpdateMenuResult.handleChangeNotConfirmed(handle).right()
        }

        return engine.rewrite(
            storeId,
            menuId,
            parentSelector = { null },
            transform = { items, _ -> items },
            options = MenuRewriteOptions(
                title = title,
                handle = handle,
                precheck = { menu ->
                    if (handle != null && menu.isDefault) {
                        "Changement de handle refusé : \"${menu.handle}\" est un menu PAR DÉFAUT du store (Shopify : " +
                            "« the handle for default menus can't be updated »). Ce refus n'est pas contournable. Le " +
                            "titre de ce menu reste modifiable. Aucune modification."
                    } else {
                        null
                    }
                },
            ),
        ).map { outcome ->
            when (outcome) {
                is MenuRewriteOutcome.Written -> UpdateMenuResult.updated(
                    menuId = outcome.menu.id,
                    titleChange = if (title != null) outcome.menu.title to title else null,
                    handleChange = if (handle != null) outcome.menu.handle to handle else null,
                    itemCount = MenuTree.collectItemIds(outcome.menu.items).size,
                    warnings = outcome.warnings,
                )
                is MenuRewriteOutcome.Failed -> UpdateMenuResult.failed(outcome.detail)
            }
        }
    }
}
