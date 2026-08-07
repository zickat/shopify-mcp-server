package com.zickat.shopifymcpserver.tenancy.exposed_interface

import arrow.core.Either
import com.zickat.shopifymcpserver.shared_kernel.UseCaseError
import org.springframework.modulith.NamedInterface

@NamedInterface("exposed_interface")
interface StoreExposedService {
    fun exists(storeId: String): Boolean
    fun existsAndNotArchived(storeId: String): Boolean
    fun resolveStoreIdBySlug(slug: String): Either<UseCaseError, String>
}
