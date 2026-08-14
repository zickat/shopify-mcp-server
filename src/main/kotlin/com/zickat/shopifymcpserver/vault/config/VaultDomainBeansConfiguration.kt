package com.zickat.shopifymcpserver.vault.config

import com.zickat.shopifymcpserver.vault.domain.StoreCredentialUseCase
import com.zickat.shopifymcpserver.vault.domain.repositories.MasterKeyProvider
import com.zickat.shopifymcpserver.vault.domain.repositories.StoreCredentialRepository
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class VaultDomainBeansConfiguration {

    @Bean
    fun storeCredentialUseCase(repository: StoreCredentialRepository, masterKeyProvider: MasterKeyProvider) =
        StoreCredentialUseCase(repository, masterKeyProvider)
}
