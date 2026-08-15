package com.zickat.shopifymcpserver.menus.domain

import arrow.core.Either
import arrow.core.flatMap
import arrow.core.right
import com.zickat.shopifymcpserver.menus.domain.repositories.MenuDeleteOutcome
import com.zickat.shopifymcpserver.menus.domain.repositories.MenusRepository
import com.zickat.shopifymcpserver.shared_kernel.UseCaseError

class DeleteMenuUseCase(
    private val menusRepository: MenusRepository,
) {

    fun execute(storeId: String, menuId: String, confirmNonEmptyDeletion: Boolean): Either<UseCaseError, DeleteMenuResult> =
        menusRepository.fetch(storeId, menuId).flatMap { menu ->
            when {
                menu == null -> DeleteMenuResult.notFound(menuId).right()
                else -> {
                    val refusals = MenuDeletionGuards.collectMenuDeletionRefusals(menu, confirmNonEmptyDeletion)
                    if (refusals.isNotEmpty()) {
                        DeleteMenuResult.refused(menu.title, menu.handle, refusals).right()
                    } else {
                        val snapshot = MenuTreeRenderer.formatMenuBlock(menu, MAX_REEMITTABLE_DEPTH)
                        val itemCount = MenuTree.collectItemIds(menu.items).size
                        menusRepository.delete(storeId, menuId).map { outcome ->
                            when (outcome) {
                                is MenuDeleteOutcome.Deleted -> DeleteMenuResult.deleted(menu.title, menu.handle, itemCount, snapshot)
                                is MenuDeleteOutcome.Failed -> DeleteMenuResult.failed(menu.title, outcome.detail)
                            }
                        }
                    }
                }
            }
        }
}
