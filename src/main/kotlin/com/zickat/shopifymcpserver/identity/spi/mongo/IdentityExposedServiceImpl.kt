package com.zickat.shopifymcpserver.identity.spi.mongo

import com.zickat.shopifymcpserver.identity.domain.models.IdentityId
import com.zickat.shopifymcpserver.identity.domain.repositories.IdentityRepository
import com.zickat.shopifymcpserver.identity.exposed_interface.IdentityExposedService
import org.springframework.stereotype.Service

@Service
class IdentityExposedServiceImpl(
    private val identityRepository: IdentityRepository,
) : IdentityExposedService {

    override fun exists(identityId: String): Boolean =
        identityRepository.findById(IdentityId(identityId)).isRight()
}
