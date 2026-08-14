package com.zickat.shopifymcpserver.menus.spi.shopify

import arrow.core.Either
import arrow.core.raise.either
import com.zickat.shopifymcpserver.menus.domain.models.MenuListing
import com.zickat.shopifymcpserver.menus.domain.models.MenuNode
import com.zickat.shopifymcpserver.menus.domain.repositories.MenusRepository
import com.zickat.shopifymcpserver.shared_kernel.MAX_SEARCH_PAGES
import com.zickat.shopifymcpserver.shared_kernel.TechnicalError
import com.zickat.shopifymcpserver.shared_kernel.UseCaseError
import com.zickat.shopifymcpserver.shopify.exposed_interface.ShopifyAdminGateway
import org.springframework.stereotype.Repository

@Repository
class MenusShopifyRepository(
    private val shopifyAdminGateway: ShopifyAdminGateway,
) : MenusRepository {

    override fun list(storeId: String, query: String?): Either<UseCaseError, MenuListing> = either {
        val menus = mutableListOf<MenuNode>()
        var cursor: String? = null
        var page = 0
        var truncated = false

        do {
            val variables = MenusGraphQL.listMenusVariables(query, cursor)
            val response = shopifyAdminGateway.executeGraphQL(storeId, MenusGraphQL.LIST_MENUS_QUERY, variables).bind()
            val connection = MenusGraphQL.menusConnection(response)
                ?: raise(TechnicalError("shopify.graphql.response.malformed"))

            menus += MenusGraphQL.menuNodes(connection)

            val hasNextPage = MenusGraphQL.hasNextPage(connection)
            cursor = if (hasNextPage) MenusGraphQL.endCursor(connection) else null
            page += 1
            if (cursor != null && page >= MAX_SEARCH_PAGES) {
                truncated = true
                cursor = null
            }
        } while (cursor != null)

        MenuListing(menus, truncated)
    }
}
