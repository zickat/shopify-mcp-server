package com.zickat.shopifymcpserver.tenancy.exposed_interface

import arrow.core.Either
import com.zickat.shopifymcpserver.shared_kernel.TenantContext
import com.zickat.shopifymcpserver.shared_kernel.UseCaseError
import com.zickat.shopifymcpserver.shared_kernel.UserContext
import org.springframework.modulith.NamedInterface

@NamedInterface("exposed_interface")
interface AccessExposedService {
    fun resolveAccess(issuer: String, subject: String, storeId: String): Either<UseCaseError, Pair<TenantContext, UserContext>>
}
