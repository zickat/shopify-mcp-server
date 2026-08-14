package com.zickat.shopifymcpserver.catalog_status.config

import com.zickat.shopifymcpserver.catalog_status.domain.SearchResourcesUseCase
import com.zickat.shopifymcpserver.catalog_status.domain.repositories.CatalogStatusRepository
import com.zickat.shopifymcpserver.catalog_status.spi.shopify.CatalogStatusShopifyRepository
import com.zickat.shopifymcpserver.shopify.exposed_interface.ShopifyAdminGateway
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class CatalogStatusDomainBeansConfiguration {

    @Bean
    fun catalogStatusRepository(shopifyAdminGateway: ShopifyAdminGateway): CatalogStatusRepository =
        CatalogStatusShopifyRepository(shopifyAdminGateway)

    @Bean
    fun searchResourcesUseCase(catalogStatusRepository: CatalogStatusRepository) = SearchResourcesUseCase(catalogStatusRepository)
}
