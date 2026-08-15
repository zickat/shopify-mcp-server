package com.zickat.shopifymcpserver.menus.spi.shopify

import arrow.core.Either
import arrow.core.raise.either
import com.zickat.shopifymcpserver.menus.domain.models.MenuItemNode
import com.zickat.shopifymcpserver.menus.domain.models.MenuListing
import com.zickat.shopifymcpserver.menus.domain.models.MenuNode
import com.zickat.shopifymcpserver.menus.domain.repositories.MenuCreateOutcome
import com.zickat.shopifymcpserver.menus.domain.repositories.MenuDeleteOutcome
import com.zickat.shopifymcpserver.menus.domain.repositories.MenuUpdateOutcome
import com.zickat.shopifymcpserver.menus.domain.repositories.MenusRepository
import com.zickat.shopifymcpserver.shared_kernel.MAX_SEARCH_PAGES
import com.zickat.shopifymcpserver.shared_kernel.TechnicalError
import com.zickat.shopifymcpserver.shared_kernel.UseCaseError
import com.zickat.shopifymcpserver.shopify.exposed_interface.ShopifyAdminGateway
import com.zickat.shopifymcpserver.shopify.exposed_interface.ShopifyUserErrors
import kotlinx.serialization.json.JsonObject
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

    override fun fetch(storeId: String, menuId: String): Either<UseCaseError, MenuNode?> = either {
        val variables = MenusGraphQL.fetchMenuVariables(menuId)
        val response = shopifyAdminGateway.executeGraphQL(storeId, MenusGraphQL.FETCH_MENU_QUERY, variables).bind()
        MenusGraphQL.menuFromFetchResponse(response)
    }

    override fun rewrite(storeId: String, menuId: String, title: String, handle: String, items: List<MenuItemNode>): Either<UseCaseError, MenuUpdateOutcome> = either {
        val variables = MenusGraphQL.updateMenuVariables(menuId, title, handle, items)
        val response = shopifyAdminGateway.executeGraphQL(storeId, MenusGraphQL.UPDATE_MENU_MUTATION, variables).bind()
        val payload = MenusGraphQL.menuUpdatePayload(response) ?: raise(TechnicalError("shopify.graphql.response.malformed"))
        val userErrors = ShopifyUserErrors.parse(payload)
        val menuPayload = payload["menu"] as? JsonObject

        if (userErrors.isNotEmpty() || menuPayload == null) {
            MenuUpdateOutcome.Failed(ShopifyUserErrors.format(userErrors))
        } else {
            MenuUpdateOutcome.Success(MenusGraphQL.updatedItems(payload))
        }
    }

    override fun create(storeId: String, handle: String, title: String, items: List<MenuItemNode>): Either<UseCaseError, MenuCreateOutcome> = either {
        val variables = MenusGraphQL.createMenuVariables(handle, title, items)
        val response = shopifyAdminGateway.executeGraphQL(storeId, MenusGraphQL.CREATE_MENU_MUTATION, variables).bind()
        val payload = MenusGraphQL.menuCreatePayload(response) ?: raise(TechnicalError("shopify.graphql.response.malformed"))
        val userErrors = ShopifyUserErrors.parse(payload)
        val created = MenusGraphQL.createdMenu(payload)

        if (userErrors.isNotEmpty() || created == null) {
            MenuCreateOutcome.Failed(ShopifyUserErrors.format(userErrors))
        } else {
            MenuCreateOutcome.Success(created)
        }
    }

    override fun delete(storeId: String, menuId: String): Either<UseCaseError, MenuDeleteOutcome> = either {
        val variables = MenusGraphQL.deleteMenuVariables(menuId)
        val response = shopifyAdminGateway.executeGraphQL(storeId, MenusGraphQL.DELETE_MENU_MUTATION, variables).bind()
        val payload = MenusGraphQL.menuDeletePayload(response) ?: raise(TechnicalError("shopify.graphql.response.malformed"))
        val userErrors = ShopifyUserErrors.parse(payload)
        val deletedId = MenusGraphQL.deletedMenuId(payload)

        if (userErrors.isNotEmpty() || deletedId == null) {
            MenuDeleteOutcome.Failed(ShopifyUserErrors.format(userErrors))
        } else {
            MenuDeleteOutcome.Deleted(deletedId)
        }
    }
}
