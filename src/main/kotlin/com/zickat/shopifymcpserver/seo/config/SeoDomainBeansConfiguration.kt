package com.zickat.shopifymcpserver.seo.config

import com.zickat.shopifymcpserver.seo.domain.GetSeoUseCase
import com.zickat.shopifymcpserver.seo.domain.UpdateSeoUseCase
import com.zickat.shopifymcpserver.seo.domain.repositories.SeoRepository
import com.zickat.shopifymcpserver.seo.spi.shopify.SeoShopifyRepository
import com.zickat.shopifymcpserver.shopify.exposed_interface.ShopifyAdminGateway
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class SeoDomainBeansConfiguration {

    @Bean
    fun seoRepository(shopifyAdminGateway: ShopifyAdminGateway): SeoRepository = SeoShopifyRepository(shopifyAdminGateway)

    @Bean
    fun getSeoUseCase(seoRepository: SeoRepository) = GetSeoUseCase(seoRepository)

    @Bean
    fun updateSeoUseCase(seoRepository: SeoRepository) = UpdateSeoUseCase(seoRepository)
}
