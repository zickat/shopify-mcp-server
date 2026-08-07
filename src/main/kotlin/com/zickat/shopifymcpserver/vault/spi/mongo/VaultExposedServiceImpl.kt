package com.zickat.shopifymcpserver.vault.spi.mongo

import com.zickat.shopifymcpserver.vault.domain.repositories.StoreCredentialRepository
import com.zickat.shopifymcpserver.vault.exposed_interface.VaultExposedService
import org.springframework.stereotype.Service

@Service
class VaultExposedServiceImpl(
    private val storeCredentialRepository: StoreCredentialRepository,
) : VaultExposedService {

    override fun hasActiveCredential(storeId: String): Boolean =
        storeCredentialRepository.findActiveByStore(storeId).isRight()
}
