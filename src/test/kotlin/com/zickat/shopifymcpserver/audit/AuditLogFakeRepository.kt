package com.zickat.shopifymcpserver.audit

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import com.zickat.shopifymcpserver.audit.domain.models.AuditLog
import com.zickat.shopifymcpserver.audit.domain.repositories.AuditLogRepository
import com.zickat.shopifymcpserver.identity.exposed_interface.IdentityExposedService
import com.zickat.shopifymcpserver.shared_kernel.NotFoundError
import com.zickat.shopifymcpserver.shared_kernel.UseCaseError
import com.zickat.shopifymcpserver.tenancy.exposed_interface.StoreExposedService

class AuditLogFakeRepository(
    private val identityExposedService: IdentityExposedService,
    private val storeExposedService: StoreExposedService,
) : AuditLogRepository {
    val entries = mutableListOf<AuditLog>()

    override fun append(entry: AuditLog): Either<UseCaseError, AuditLog> {
        entry.identityId?.let {
            if (!identityExposedService.exists(it)) return NotFoundError("auditLog.identity.not.found").left()
        }
        if (!storeExposedService.exists(entry.storeId)) {
            return NotFoundError("auditLog.store.not.found").left()
        }
        entries.add(entry)
        return entry.right()
    }

    override fun findByStore(storeId: String): Either<UseCaseError, List<AuditLog>> =
        entries.filter { it.storeId == storeId }.sortedByDescending { it.occurredAt }.right()

    override fun findByIdentity(identityId: String): Either<UseCaseError, List<AuditLog>> =
        entries.filter { it.identityId == identityId }.sortedByDescending { it.occurredAt }.right()
}
