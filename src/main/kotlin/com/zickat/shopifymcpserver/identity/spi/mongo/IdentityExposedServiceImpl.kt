package com.zickat.shopifymcpserver.identity.spi.mongo

import arrow.core.Either
import com.zickat.shopifymcpserver.identity.domain.IdentityUseCase
import com.zickat.shopifymcpserver.identity.domain.models.IdentityId
import com.zickat.shopifymcpserver.identity.domain.repositories.IdentityRepository
import com.zickat.shopifymcpserver.identity.exposed_interface.IdentityExposedService
import com.zickat.shopifymcpserver.shared_kernel.UseCaseError
import org.springframework.stereotype.Service

@Service
class IdentityExposedServiceImpl(
    private val identityRepository: IdentityRepository,
    private val identityUseCase: IdentityUseCase,
) : IdentityExposedService {

    override fun exists(identityId: String): Boolean =
        identityRepository.findById(IdentityId(identityId)).isRight()

    override fun isActive(identityId: String): Boolean =
        identityRepository.findById(IdentityId(identityId)).fold({ false }, { it.isActive })

    override fun resolve(issuer: String, subject: String): Either<UseCaseError, String> =
        identityUseCase.findOrCreate(issuer, subject, displayName = subject).map { it.id.value }
}
