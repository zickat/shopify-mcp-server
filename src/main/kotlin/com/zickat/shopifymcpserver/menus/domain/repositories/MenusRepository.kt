package com.zickat.shopifymcpserver.menus.domain.repositories

import arrow.core.Either
import com.zickat.shopifymcpserver.menus.domain.models.MenuItemNode
import com.zickat.shopifymcpserver.menus.domain.models.MenuListing
import com.zickat.shopifymcpserver.menus.domain.models.MenuNode
import com.zickat.shopifymcpserver.shared_kernel.UseCaseError

sealed interface MenuUpdateOutcome {
    data class Success(val items: List<MenuItemNode>) : MenuUpdateOutcome
    data class Failed(val detail: String) : MenuUpdateOutcome
}

sealed interface MenuCreateOutcome {
    data class Success(val menu: MenuNode) : MenuCreateOutcome
    data class Failed(val detail: String) : MenuCreateOutcome
}

sealed interface MenuDeleteOutcome {
    data class Deleted(val deletedMenuId: String) : MenuDeleteOutcome
    data class Failed(val detail: String) : MenuDeleteOutcome
}

interface MenusRepository {
    fun list(storeId: String, query: String?): Either<UseCaseError, MenuListing>
    fun fetch(storeId: String, menuId: String): Either<UseCaseError, MenuNode?>
    fun rewrite(storeId: String, menuId: String, title: String, handle: String, items: List<MenuItemNode>): Either<UseCaseError, MenuUpdateOutcome>
    fun create(storeId: String, handle: String, title: String, items: List<MenuItemNode>): Either<UseCaseError, MenuCreateOutcome>
    fun delete(storeId: String, menuId: String): Either<UseCaseError, MenuDeleteOutcome>
}
