package com.zickat.shopifymcpserver.api.mcp

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import com.zickat.shopifymcpserver.shared_kernel.ForbiddenError
import com.zickat.shopifymcpserver.shared_kernel.TenantContext
import com.zickat.shopifymcpserver.shared_kernel.UseCaseError
import com.zickat.shopifymcpserver.shared_kernel.UserContext
import com.zickat.shopifymcpserver.tenancy.exposed_interface.AccessExposedService
import com.zickat.shopifymcpserver.tenancy.exposed_interface.model.GrantedStore

class AccessExposedServiceFake : AccessExposedService {
    var result: Either<UseCaseError, Pair<TenantContext, UserContext>> = ForbiddenError("access.denied").left()
    var lastCall: Triple<String, String, String>? = null
    var grantedStoresByIdentity: MutableMap<String, List<GrantedStore>> = mutableMapOf()

    override fun resolveAccess(issuer: String, subject: String, storeId: String): Either<UseCaseError, Pair<TenantContext, UserContext>> {
        lastCall = Triple(issuer, subject, storeId)
        return result
    }

    override fun listGrantedStores(identityId: String): Either<UseCaseError, List<GrantedStore>> =
        grantedStoresByIdentity.getOrDefault(identityId, emptyList()).right()
}
