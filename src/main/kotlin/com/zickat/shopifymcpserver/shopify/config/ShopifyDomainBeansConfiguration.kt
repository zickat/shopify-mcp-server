package com.zickat.shopifymcpserver.shopify.config

import com.zickat.shopifymcpserver.shopify.domain.ShopifyAdminGraphQLUseCase
import com.zickat.shopifymcpserver.shopify.domain.repositories.ShopifyAdminHttpClient
import com.zickat.shopifymcpserver.vault.exposed_interface.VaultExposedService
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class ShopifyDomainBeansConfiguration {

    @Bean
    fun shopifyAdminGraphQLUseCase(vaultExposedService: VaultExposedService, httpClient: ShopifyAdminHttpClient) =
        ShopifyAdminGraphQLUseCase(vaultExposedService, httpClient)
}
