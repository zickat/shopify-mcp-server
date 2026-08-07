package com.zickat.shopifymcpserver.api.mcp

import arrow.core.Either
import arrow.core.left
import com.zickat.shopifymcpserver.shared_kernel.ForbiddenError
import com.zickat.shopifymcpserver.shared_kernel.TenantContext
import com.zickat.shopifymcpserver.shared_kernel.UseCaseError
import com.zickat.shopifymcpserver.shared_kernel.UserContext
import com.zickat.shopifymcpserver.tenancy.exposed_interface.AccessExposedService

class AccessExposedServiceFake : AccessExposedService {
    var result: Either<UseCaseError, Pair<TenantContext, UserContext>> = ForbiddenError("access.denied").left()
    var lastCall: Triple<String, String, String>? = null

    override fun resolveAccess(issuer: String, subject: String, storeId: String): Either<UseCaseError, Pair<TenantContext, UserContext>> {
        lastCall = Triple(issuer, subject, storeId)
        return result
    }
}
