package com.zickat.shopifymcpserver.tenancy.config

import com.zickat.shopifymcpserver.identity.exposed_interface.IdentityExposedService
import com.zickat.shopifymcpserver.tenancy.domain.AccessResolutionUseCase
import com.zickat.shopifymcpserver.tenancy.domain.ActiveStoreSelectionRegistry
import com.zickat.shopifymcpserver.tenancy.domain.repositories.GrantRepository
import com.zickat.shopifymcpserver.tenancy.domain.repositories.StoreRepository
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class TenancyDomainBeansConfiguration {

    @Bean
    fun activeStoreSelectionRegistry() = ActiveStoreSelectionRegistry()

    @Bean
    fun accessResolutionUseCase(
        identityExposedService: IdentityExposedService,
        grantRepository: GrantRepository,
        storeRepository: StoreRepository,
    ) = AccessResolutionUseCase(identityExposedService, grantRepository, storeRepository)
}
