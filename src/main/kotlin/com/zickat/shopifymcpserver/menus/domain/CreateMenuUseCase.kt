package com.zickat.shopifymcpserver.menus.domain

import arrow.core.Either
import arrow.core.right
import com.zickat.shopifymcpserver.menus.domain.repositories.MenuCreateOutcome
import com.zickat.shopifymcpserver.menus.domain.repositories.MenusRepository
import com.zickat.shopifymcpserver.shared_kernel.UseCaseError

class CreateMenuUseCase(
    private val menusRepository: MenusRepository,
) {

    fun execute(storeId: String, handle: String, title: String, items: List<CreateMenuItemInput>): Either<UseCaseError, CreateMenuResult> {
        val newItems = try {
            items.mapIndexed { index, raw ->
                MenuItemFactory.buildNewMenuItem(NewMenuItemInput(raw.title, raw.resource_id, raw.url), "items[$index] (\"${raw.title}\")")
            }
        } catch (e: MenuItemValidationError) {
            return CreateMenuResult.invalidItem(title, e.message.orEmpty()).right()
        }

        return menusRepository.create(storeId, handle, title, newItems).map { outcome ->
            when (outcome) {
                is MenuCreateOutcome.Success -> {
                    val created = outcome.menu
                    val warnings = buildList {
                        if (created.items.size != newItems.size) {
                            add(
                                "⚠ ÉCART APRÈS ÉCRITURE — ${newItems.size} item(s) demandé(s), ${created.items.size} présent(s) " +
                                    "dans le menu créé. Shopify a rejeté une partie de la liste sans le signaler en userErrors : " +
                                    "vérifier les cibles des items manquants et les rajouter par add_menu_item.",
                            )
                        }
                        if (created.handle != handle) {
                            add(
                                "⚠ Handle NORMALISÉ par Shopify : \"$handle\" demandé → \"${created.handle}\" attribué. " +
                                    "C'est \"${created.handle}\" qu'un thème devra référencer.",
                            )
                        }
                    }
                    CreateMenuResult.created(
                        title = created.title,
                        handle = created.handle,
                        menuId = created.id,
                        block = MenuTreeRenderer.formatMenuBlock(created, MAX_REEMITTABLE_DEPTH),
                        warnings = warnings,
                    )
                }
                is MenuCreateOutcome.Failed -> CreateMenuResult.failed(title, outcome.detail)
            }
        }
    }
}
