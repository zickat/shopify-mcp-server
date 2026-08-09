package com.zickat.shopifymcpserver.menus.domain

import arrow.core.Either
import com.zickat.shopifymcpserver.menus.exposed_interface.MenusExposedService
import com.zickat.shopifymcpserver.menus.exposed_interface.model.ListMenusResult
import com.zickat.shopifymcpserver.shared_kernel.UseCaseError
import org.springframework.stereotype.Service

@Service
class MenusExposedServiceImpl(
    private val listMenusUseCase: ListMenusUseCase,
) : MenusExposedService {

    override fun listMenus(storeId: String, query: String?, depth: Int?): Either<UseCaseError, ListMenusResult> =
        listMenusUseCase.execute(storeId, query, depth)
}
