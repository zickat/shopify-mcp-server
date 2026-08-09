package com.zickat.shopifymcpserver.menus.exposed_interface

import arrow.core.Either
import com.zickat.shopifymcpserver.menus.exposed_interface.model.ListMenusResult
import com.zickat.shopifymcpserver.shared_kernel.UseCaseError
import org.springframework.modulith.NamedInterface

@NamedInterface("exposed_interface")
interface MenusExposedService {
    fun listMenus(storeId: String, query: String?, depth: Int?): Either<UseCaseError, ListMenusResult>
}
