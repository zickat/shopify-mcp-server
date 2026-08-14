package com.zickat.shopifymcpserver.audit.exposed_interface

import arrow.core.Either
import com.zickat.shopifymcpserver.audit.domain.AuditLogUseCase
import com.zickat.shopifymcpserver.shared_kernel.UseCaseError
import org.springframework.stereotype.Service

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
