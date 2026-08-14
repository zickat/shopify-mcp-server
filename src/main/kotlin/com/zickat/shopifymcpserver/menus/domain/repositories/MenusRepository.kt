package com.zickat.shopifymcpserver.menus.domain.repositories

import arrow.core.Either
import com.zickat.shopifymcpserver.menus.domain.models.MenuListing
import com.zickat.shopifymcpserver.shared_kernel.UseCaseError

interface MenusRepository {
    fun list(storeId: String, query: String?): Either<UseCaseError, MenuListing>
}
