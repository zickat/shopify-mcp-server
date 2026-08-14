package com.zickat.shopifymcpserver.menus

import arrow.core.Either
import com.zickat.shopifymcpserver.menus.domain.models.MenuListing
import com.zickat.shopifymcpserver.menus.domain.repositories.MenusRepository
import com.zickat.shopifymcpserver.shared_kernel.UseCaseError

class MenusFakeRepository : MenusRepository {

    data class ListCall(val storeId: String, val query: String?)

    var listResponse: Either<UseCaseError, MenuListing>? = null

    val listCalls = mutableListOf<ListCall>()

    override fun list(storeId: String, query: String?): Either<UseCaseError, MenuListing> {
        listCalls += ListCall(storeId, query)
        return requireNotNull(listResponse) { "listResponse must be set before calling list()" }
    }
}
