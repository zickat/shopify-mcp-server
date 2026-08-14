package com.zickat.shopifymcpserver.products.config

import com.zickat.shopifymcpserver.products.domain.GetEnrichedContentUseCase
import com.zickat.shopifymcpserver.products.domain.GetRawContentUseCase
import com.zickat.shopifymcpserver.products.domain.ListOrphanProductsUseCase
import com.zickat.shopifymcpserver.products.domain.ListToReviewUseCase
import com.zickat.shopifymcpserver.products.domain.MarkBlockedUseCase
import com.zickat.shopifymcpserver.products.domain.PublishResourceUseCase
import com.zickat.shopifymcpserver.products.domain.SearchProductsUseCase
import com.zickat.shopifymcpserver.products.domain.UnpublishResourceUseCase
import com.zickat.shopifymcpserver.products.domain.repositories.ProductRepository
import com.zickat.shopifymcpserver.products.spi.shopify.ProductShopifyRepository
import com.zickat.shopifymcpserver.shopify.exposed_interface.ShopifyAdminGateway
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class ProductsDomainBeansConfiguration {

    @Bean
    fun productRepository(shopifyAdminGateway: ShopifyAdminGateway): ProductRepository = ProductShopifyRepository(shopifyAdminGateway)

    @Bean
    fun searchProductsUseCase(productRepository: ProductRepository) = SearchProductsUseCase(productRepository)

    @Bean
    fun getRawContentUseCase(productRepository: ProductRepository) = GetRawContentUseCase(productRepository)

    @Bean
    fun getEnrichedContentUseCase(productRepository: ProductRepository) = GetEnrichedContentUseCase(productRepository)

    @Bean
    fun listToReviewUseCase(productRepository: ProductRepository) = ListToReviewUseCase(productRepository)

    @Bean
    fun listOrphanProductsUseCase(productRepository: ProductRepository) = ListOrphanProductsUseCase(productRepository)

    @Bean
    fun markBlockedUseCase(productRepository: ProductRepository) = MarkBlockedUseCase(productRepository)

    @Bean
    fun publishResourceUseCase(productRepository: ProductRepository) = PublishResourceUseCase(productRepository)

    @Bean
    fun unpublishResourceUseCase(productRepository: ProductRepository) = UnpublishResourceUseCase(productRepository)
}
