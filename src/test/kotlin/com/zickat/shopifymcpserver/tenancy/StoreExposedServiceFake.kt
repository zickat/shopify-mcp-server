package com.zickat.shopifymcpserver.tenancy

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import com.zickat.shopifymcpserver.shared_kernel.NotFoundError
import com.zickat.shopifymcpserver.shared_kernel.UseCaseError
import com.zickat.shopifymcpserver.tenancy.exposed_interface.StoreExposedService

class StoreExposedServiceFake : StoreExposedService {
    val archivedByStoreId = mutableMapOf<String, Boolean>()
    val storeIdBySlug = mutableMapOf<String, String>()

    override fun exists(storeId: String): Boolean = archivedByStoreId.containsKey(storeId)

    override fun existsAndNotArchived(storeId: String): Boolean = archivedByStoreId[storeId] == false

    override fun resolveStoreIdBySlug(slug: String): Either<UseCaseError, String> =
        storeIdBySlug[slug]?.right() ?: NotFoundError("store.not.found").left()
}
