package com.zickat.shopifymcpserver.tenancy.spi.mongo

import com.zickat.shopifymcpserver.tenancy.domain.repositories.StoreRepository
import com.zickat.shopifymcpserver.tenancy.domain.models.StoreId
import com.zickat.shopifymcpserver.tenancy.exposed_interface.StoreExposedService
import org.springframework.stereotype.Service

@Service
class StoreExposedServiceImpl(
    private val storeRepository: StoreRepository,
) : StoreExposedService {

    override fun exists(storeId: String): Boolean =
        storeRepository.findById(StoreId(storeId)).isRight()

    override fun existsAndNotArchived(storeId: String): Boolean =
        storeRepository.findById(StoreId(storeId)).fold({ false }, { !it.isArchived })
}
