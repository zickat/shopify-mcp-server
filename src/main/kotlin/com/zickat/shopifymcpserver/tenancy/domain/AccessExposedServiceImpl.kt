package com.zickat.shopifymcpserver.tenancy.domain

import arrow.core.Either
import com.zickat.shopifymcpserver.shared_kernel.TenantContext
import com.zickat.shopifymcpserver.shared_kernel.UseCaseError
import com.zickat.shopifymcpserver.shared_kernel.UserContext
import com.zickat.shopifymcpserver.tenancy.exposed_interface.AccessExposedService
import org.springframework.stereotype.Service

/**
 * Implémentation de [AccessExposedService] — délègue à [AccessResolutionUseCase] sans rien y
 * ajouter. Voir la KDoc de [AccessExposedService] pour la raison d'être de cette classe.
 */
@Service
class AccessExposedServiceImpl(
    private val accessResolutionUseCase: AccessResolutionUseCase,
) : AccessExposedService {

    override fun resolveAccess(issuer: String, subject: String, storeId: String): Either<UseCaseError, Pair<TenantContext, UserContext>> =
        accessResolutionUseCase.resolve(issuer, subject, storeId).map { it.tenant to it.user }
}
