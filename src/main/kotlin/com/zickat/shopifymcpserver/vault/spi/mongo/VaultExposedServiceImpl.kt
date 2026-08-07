package com.zickat.shopifymcpserver.vault.spi.mongo

import arrow.core.Either
import arrow.core.raise.either
import com.zickat.shopifymcpserver.shared_kernel.UseCaseError
import com.zickat.shopifymcpserver.vault.domain.StoreCredentialUseCase
import com.zickat.shopifymcpserver.vault.domain.repositories.StoreCredentialRepository
import com.zickat.shopifymcpserver.vault.exposed_interface.VaultExposedService
import org.springframework.stereotype.Service

@Service
class VaultExposedServiceImpl(
    private val storeCredentialRepository: StoreCredentialRepository,
    private val storeCredentialUseCase: StoreCredentialUseCase,
) : VaultExposedService {

    override fun hasActiveCredential(storeId: String): Boolean =
        storeCredentialRepository.findActiveByStore(storeId).isRight()

    override fun resolveCredential(storeId: String): Either<UseCaseError, ByteArray> = either {
        val credential = storeCredentialRepository.findActiveByStore(storeId).bind()
        storeCredentialUseCase.reveal(credential.id).bind()
    }
}
