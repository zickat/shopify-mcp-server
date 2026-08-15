package com.zickat.shopifymcpserver.menus

import arrow.core.Either
import com.zickat.shopifymcpserver.menus.domain.models.MenuItemNode
import com.zickat.shopifymcpserver.menus.domain.models.MenuListing
import com.zickat.shopifymcpserver.menus.domain.models.MenuNode
import com.zickat.shopifymcpserver.menus.domain.repositories.MenuCreateOutcome
import com.zickat.shopifymcpserver.menus.domain.repositories.MenuDeleteOutcome
import com.zickat.shopifymcpserver.menus.domain.repositories.MenuUpdateOutcome
import com.zickat.shopifymcpserver.menus.domain.repositories.MenusRepository
import com.zickat.shopifymcpserver.shared_kernel.UseCaseError

class MenusFakeRepository : MenusRepository {

    data class ListCall(val storeId: String, val query: String?)
    data class FetchCall(val storeId: String, val menuId: String)
    data class RewriteCall(val storeId: String, val menuId: String, val title: String, val handle: String, val items: List<MenuItemNode>)
    data class CreateCall(val storeId: String, val handle: String, val title: String, val items: List<MenuItemNode>)
    data class DeleteCall(val storeId: String, val menuId: String)

    var listResponse: Either<UseCaseError, MenuListing>? = null
    var fetchResponse: Either<UseCaseError, MenuNode?>? = null
    var rewriteResponse: Either<UseCaseError, MenuUpdateOutcome>? = null
    var createResponse: Either<UseCaseError, MenuCreateOutcome>? = null
    var deleteResponse: Either<UseCaseError, MenuDeleteOutcome>? = null

    val listCalls = mutableListOf<ListCall>()
    val fetchCalls = mutableListOf<FetchCall>()
    val rewriteCalls = mutableListOf<RewriteCall>()
    val createCalls = mutableListOf<CreateCall>()
    val deleteCalls = mutableListOf<DeleteCall>()

    override fun list(storeId: String, query: String?): Either<UseCaseError, MenuListing> {
        listCalls += ListCall(storeId, query)
        return requireNotNull(listResponse) { "listResponse must be set before calling list()" }
    }

    override fun fetch(storeId: String, menuId: String): Either<UseCaseError, MenuNode?> {
        fetchCalls += FetchCall(storeId, menuId)
        return requireNotNull(fetchResponse) { "fetchResponse must be set before calling fetch()" }
    }

    override fun rewrite(storeId: String, menuId: String, title: String, handle: String, items: List<MenuItemNode>): Either<UseCaseError, MenuUpdateOutcome> {
        rewriteCalls += RewriteCall(storeId, menuId, title, handle, items)
        return requireNotNull(rewriteResponse) { "rewriteResponse must be set before calling rewrite()" }
    }

    override fun create(storeId: String, handle: String, title: String, items: List<MenuItemNode>): Either<UseCaseError, MenuCreateOutcome> {
        createCalls += CreateCall(storeId, handle, title, items)
        return requireNotNull(createResponse) { "createResponse must be set before calling create()" }
    }

    override fun delete(storeId: String, menuId: String): Either<UseCaseError, MenuDeleteOutcome> {
        deleteCalls += DeleteCall(storeId, menuId)
        return requireNotNull(deleteResponse) { "deleteResponse must be set before calling delete()" }
    }
}
