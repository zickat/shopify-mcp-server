package com.zickat.shopifymcpserver.tenancy.exposed_interface

import arrow.core.Either
import com.zickat.shopifymcpserver.shared_kernel.UseCaseError
import com.zickat.shopifymcpserver.tenancy.domain.models.StoreId
import com.zickat.shopifymcpserver.tenancy.domain.repositories.StoreRepository
import org.springframework.stereotype.Service

@Service
class StoreExposedServiceImpl(
    private val storeRepository: StoreRepository,
) : StoreExposedService {

    override fun exists(storeId: String): Boolean =
        storeRepository.findById(StoreId(storeId)).isRight()

    override fun existsAndNotArchived(storeId: String): Boolean =
        storeRepository.findById(StoreId(storeId)).fold({ false }, { !it.isArchived })

    override fun resolveStoreIdBySlug(slug: String): Either<UseCaseError, String> =
        storeRepository.findBySlug(slug).map { it.id.value }
}
