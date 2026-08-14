package com.zickat.shopifymcpserver.redirects.config

import com.zickat.shopifymcpserver.redirects.domain.CreateRedirectUseCase
import com.zickat.shopifymcpserver.redirects.domain.repositories.RedirectsRepository
import com.zickat.shopifymcpserver.redirects.spi.shopify.RedirectsShopifyRepository
import com.zickat.shopifymcpserver.shopify.exposed_interface.ShopifyAdminGateway
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class RedirectsDomainBeansConfiguration {

    @Bean
    fun redirectsRepository(shopifyAdminGateway: ShopifyAdminGateway): RedirectsRepository =
        RedirectsShopifyRepository(shopifyAdminGateway)

    @Bean
    fun createRedirectUseCase(redirectsRepository: RedirectsRepository) = CreateRedirectUseCase(redirectsRepository)
}
