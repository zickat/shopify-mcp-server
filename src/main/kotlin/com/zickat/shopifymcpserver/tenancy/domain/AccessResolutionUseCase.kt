package com.zickat.shopifymcpserver.tenancy.domain

import arrow.core.Either
import arrow.core.raise.either
import com.zickat.shopifymcpserver.identity.exposed_interface.IdentityExposedService
import com.zickat.shopifymcpserver.shared_kernel.ForbiddenError
import com.zickat.shopifymcpserver.shared_kernel.NotFoundError
import com.zickat.shopifymcpserver.shared_kernel.TenantContext
import com.zickat.shopifymcpserver.shared_kernel.UseCaseError
import com.zickat.shopifymcpserver.shared_kernel.UserContext
import com.zickat.shopifymcpserver.tenancy.domain.models.AccessContext
import com.zickat.shopifymcpserver.tenancy.domain.models.StoreId
import com.zickat.shopifymcpserver.tenancy.domain.models.toAccessRole
import com.zickat.shopifymcpserver.tenancy.domain.repositories.GrantRepository
import com.zickat.shopifymcpserver.tenancy.domain.repositories.StoreRepository
import org.springframework.stereotype.Component

@Component
class AccessResolutionUseCase(
    private val identityExposedService: IdentityExposedService,
    private val grantRepository: GrantRepository,
    private val storeRepository: StoreRepository,
) {
    fun resolve(issuer: String, subject: String, storeId: String): Either<UseCaseError, AccessContext> = either {
        val identityId = identityExposedService.resolve(issuer, subject).bind()

        if (!identityExposedService.isActive(identityId)) {
            raise(accessDenied(storeId))
        }

        val store = storeRepository.findById(StoreId(storeId)).orNullIfNotFound().bind()
        if (store == null || store.isArchived) {
            raise(accessDenied(storeId))
        }

        val grant = grantRepository.findActiveByIdentityAndStore(identityId, StoreId(storeId)).orNullIfNotFound().bind()
        if (grant == null) {
            raise(accessDenied(storeId))
        }

        AccessContext(
            tenant = TenantContext(storeId = storeId),
            user = UserContext(identityId = identityId, role = grant.role.toAccessRole()),
        )
    }

    private fun accessDenied(storeId: String) = ForbiddenError("access.denied", mapOf("storeId" to storeId))

    private fun <T> Either<UseCaseError, T>.orNullIfNotFound(): Either<UseCaseError, T?> =
        fold(
            ifLeft = { error -> if (error is NotFoundError) Either.Right(null) else Either.Left(error) },
            ifRight = { Either.Right(it) },
        )
}
