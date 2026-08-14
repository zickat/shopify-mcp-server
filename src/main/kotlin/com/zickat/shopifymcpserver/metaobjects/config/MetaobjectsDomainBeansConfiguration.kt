package com.zickat.shopifymcpserver.metaobjects.config

import com.zickat.shopifymcpserver.metaobjects.domain.CreateMetaobjectUseCase
import com.zickat.shopifymcpserver.metaobjects.domain.DeleteMetaobjectUseCase
import com.zickat.shopifymcpserver.metaobjects.domain.GetMetaobjectUseCase
import com.zickat.shopifymcpserver.metaobjects.domain.ListMetaobjectsUseCase
import com.zickat.shopifymcpserver.metaobjects.domain.UpdateMetaobjectUseCase
import com.zickat.shopifymcpserver.metaobjects.domain.repositories.MetaobjectsRepository
import com.zickat.shopifymcpserver.metaobjects.spi.shopify.MetaobjectsShopifyRepository
import com.zickat.shopifymcpserver.shopify.exposed_interface.ShopifyAdminGateway
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class MetaobjectsDomainBeansConfiguration {

    @Bean
    fun metaobjectsRepository(shopifyAdminGateway: ShopifyAdminGateway): MetaobjectsRepository =
        MetaobjectsShopifyRepository(shopifyAdminGateway)

    @Bean
    fun listMetaobjectsUseCase(metaobjectsRepository: MetaobjectsRepository) = ListMetaobjectsUseCase(metaobjectsRepository)

    @Bean
    fun getMetaobjectUseCase(metaobjectsRepository: MetaobjectsRepository) = GetMetaobjectUseCase(metaobjectsRepository)

    @Bean
    fun createMetaobjectUseCase(metaobjectsRepository: MetaobjectsRepository) = CreateMetaobjectUseCase(metaobjectsRepository)

    @Bean
    fun updateMetaobjectUseCase(metaobjectsRepository: MetaobjectsRepository) = UpdateMetaobjectUseCase(metaobjectsRepository)

    @Bean
    fun deleteMetaobjectUseCase(metaobjectsRepository: MetaobjectsRepository) = DeleteMetaobjectUseCase(metaobjectsRepository)
}
