package com.zickat.shopifymcpserver.tenancy.exposed_interface

import arrow.core.Either
import com.zickat.shopifymcpserver.shared_kernel.TenantContext
import com.zickat.shopifymcpserver.shared_kernel.UseCaseError
import com.zickat.shopifymcpserver.shared_kernel.UserContext
import com.zickat.shopifymcpserver.tenancy.domain.AccessResolutionUseCase
import com.zickat.shopifymcpserver.tenancy.exposed_interface.model.GrantedStore
import org.springframework.stereotype.Service

@Service
class AccessExposedServiceImpl(
    private val accessResolutionUseCase: AccessResolutionUseCase,
) : AccessExposedService {

    override fun resolveAccess(issuer: String, subject: String, storeId: String): Either<UseCaseError, Pair<TenantContext, UserContext>> =
        accessResolutionUseCase.resolve(issuer, subject, storeId).map { it.tenant to it.user }

    override fun listGrantedStores(identityId: String): Either<UseCaseError, List<GrantedStore>> =
        accessResolutionUseCase.listGrantedStores(identityId).map { stores ->
            stores.map { GrantedStore(storeId = it.id.value, slug = it.slug) }
        }
}
