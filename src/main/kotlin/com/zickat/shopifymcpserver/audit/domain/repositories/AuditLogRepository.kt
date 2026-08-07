package com.zickat.shopifymcpserver.audit.domain.repositories

import arrow.core.Either
import com.zickat.shopifymcpserver.audit.domain.models.AuditLog
import com.zickat.shopifymcpserver.shared_kernel.UseCaseError

interface AuditLogRepository {
    fun append(entry: AuditLog): Either<UseCaseError, AuditLog>
    fun findByStore(storeId: String): Either<UseCaseError, List<AuditLog>>
    fun findByIdentity(identityId: String): Either<UseCaseError, List<AuditLog>>
}
