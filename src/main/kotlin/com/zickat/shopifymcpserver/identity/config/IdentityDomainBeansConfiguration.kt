package com.zickat.shopifymcpserver.identity.config

import com.zickat.shopifymcpserver.identity.domain.IdentityUseCase
import com.zickat.shopifymcpserver.identity.domain.repositories.IdentityRepository
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class IdentityDomainBeansConfiguration {

    @Bean
    fun identityUseCase(identityRepository: IdentityRepository) = IdentityUseCase(identityRepository)
}
