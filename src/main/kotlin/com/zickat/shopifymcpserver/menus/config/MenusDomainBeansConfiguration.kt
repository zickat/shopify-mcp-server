package com.zickat.shopifymcpserver.menus.config

import com.zickat.shopifymcpserver.menus.domain.AddMenuItemUseCase
import com.zickat.shopifymcpserver.menus.domain.CreateMenuUseCase
import com.zickat.shopifymcpserver.menus.domain.DeleteMenuUseCase
import com.zickat.shopifymcpserver.menus.domain.ListMenusUseCase
import com.zickat.shopifymcpserver.menus.domain.MenuRewriteEngine
import com.zickat.shopifymcpserver.menus.domain.RemoveMenuItemUseCase
import com.zickat.shopifymcpserver.menus.domain.ReorderMenuItemsUseCase
import com.zickat.shopifymcpserver.menus.domain.UpdateMenuItemUseCase
import com.zickat.shopifymcpserver.menus.domain.UpdateMenuUseCase
import com.zickat.shopifymcpserver.menus.domain.repositories.MenusRepository
import com.zickat.shopifymcpserver.menus.spi.shopify.MenusShopifyRepository
import com.zickat.shopifymcpserver.shopify.exposed_interface.ShopifyAdminGateway
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class MenusDomainBeansConfiguration {

    @Bean
    fun menusRepository(shopifyAdminGateway: ShopifyAdminGateway): MenusRepository =
        MenusShopifyRepository(shopifyAdminGateway)

    @Bean
    fun menuRewriteEngine(menusRepository: MenusRepository) = MenuRewriteEngine(menusRepository)

    @Bean
    fun listMenusUseCase(menusRepository: MenusRepository) = ListMenusUseCase(menusRepository)

    @Bean
    fun addMenuItemUseCase(menuRewriteEngine: MenuRewriteEngine) = AddMenuItemUseCase(menuRewriteEngine)

    @Bean
    fun removeMenuItemUseCase(menuRewriteEngine: MenuRewriteEngine) = RemoveMenuItemUseCase(menuRewriteEngine)

    @Bean
    fun reorderMenuItemsUseCase(menuRewriteEngine: MenuRewriteEngine) = ReorderMenuItemsUseCase(menuRewriteEngine)

    @Bean
    fun updateMenuItemUseCase(menuRewriteEngine: MenuRewriteEngine) = UpdateMenuItemUseCase(menuRewriteEngine)

    @Bean
    fun updateMenuUseCase(menuRewriteEngine: MenuRewriteEngine) = UpdateMenuUseCase(menuRewriteEngine)

    @Bean
    fun createMenuUseCase(menusRepository: MenusRepository) = CreateMenuUseCase(menusRepository)

    @Bean
    fun deleteMenuUseCase(menusRepository: MenusRepository) = DeleteMenuUseCase(menusRepository)
}
