package com.zickat.shopifymcpserver.audit.domain

import arrow.core.Either
import com.zickat.shopifymcpserver.audit.exposed_interface.AuditExposedService
import com.zickat.shopifymcpserver.shared_kernel.UseCaseError
import org.springframework.stereotype.Service

/**
 * Implémentation de [AuditExposedService] — délègue à [AuditLogUseCase] sans rien y ajouter. Voir
 * la KDoc de [AuditExposedService] pour la raison d'être de cette classe.
 */
@Service
class AuditExposedServiceImpl(
    private val auditLogUseCase: AuditLogUseCase,
) : AuditExposedService {

    override fun <T> execute(
        identityId: String?,
        storeId: String,
        toolName: String,
        isMutation: Boolean,
        toolInput: Map<String, String>,
        action: () -> Either<UseCaseError, T>,
    ): Either<UseCaseError, T> =
        auditLogUseCase.execute(identityId, storeId, toolName, isMutation, toolInput, action)
}
