package com.zickat.shopifymcpserver.menus.config

import com.zickat.shopifymcpserver.menus.domain.ListMenusUseCase
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
    fun listMenusUseCase(menusRepository: MenusRepository) = ListMenusUseCase(menusRepository)
}
