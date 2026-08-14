package com.zickat.shopifymcpserver.pages.config

import com.zickat.shopifymcpserver.pages.domain.CreatePageUseCase
import com.zickat.shopifymcpserver.pages.domain.DeletePageUseCase
import com.zickat.shopifymcpserver.pages.domain.GetPageMetafieldsUseCase
import com.zickat.shopifymcpserver.pages.domain.ListPagesUseCase
import com.zickat.shopifymcpserver.pages.domain.TogglePagePublishUseCase
import com.zickat.shopifymcpserver.pages.domain.UpdatePageMetafieldsUseCase
import com.zickat.shopifymcpserver.pages.domain.UpdatePageUseCase
import com.zickat.shopifymcpserver.pages.domain.repositories.PageRepository
import com.zickat.shopifymcpserver.pages.spi.shopify.PageShopifyRepository
import com.zickat.shopifymcpserver.shopify.exposed_interface.ShopifyAdminGateway
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class PagesDomainBeansConfiguration {

    @Bean
    fun pageRepository(shopifyAdminGateway: ShopifyAdminGateway): PageRepository =
        PageShopifyRepository(shopifyAdminGateway)

    @Bean
    fun listPagesUseCase(pageRepository: PageRepository) = ListPagesUseCase(pageRepository)

    @Bean
    fun createPageUseCase(pageRepository: PageRepository) = CreatePageUseCase(pageRepository)

    @Bean
    fun updatePageUseCase(pageRepository: PageRepository) = UpdatePageUseCase(pageRepository)

    @Bean
    fun deletePageUseCase(pageRepository: PageRepository) = DeletePageUseCase(pageRepository)

    @Bean
    fun togglePagePublishUseCase(pageRepository: PageRepository) = TogglePagePublishUseCase(pageRepository)

    @Bean
    fun getPageMetafieldsUseCase(pageRepository: PageRepository) = GetPageMetafieldsUseCase(pageRepository)

    @Bean
    fun updatePageMetafieldsUseCase(pageRepository: PageRepository) = UpdatePageMetafieldsUseCase(pageRepository)
}
