package com.zickat.shopifymcpserver.audit

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import com.zickat.shopifymcpserver.audit.domain.models.AuditLog
import com.zickat.shopifymcpserver.audit.domain.repositories.AuditLogRepository
import com.zickat.shopifymcpserver.identity.exposed_interface.IdentityExposedService
import com.zickat.shopifymcpserver.shared_kernel.NotFoundError
import com.zickat.shopifymcpserver.shared_kernel.TechnicalError
import com.zickat.shopifymcpserver.shared_kernel.UseCaseError
import com.zickat.shopifymcpserver.tenancy.exposed_interface.StoreExposedService

class AuditLogFakeRepository(
    private val identityExposedService: IdentityExposedService,
    private val storeExposedService: StoreExposedService,
) : AuditLogRepository {
    val entries = mutableListOf<AuditLog>()

    /**
     * Bascule de test pour `LOT0-07` — simule une panne d'écriture (Mongo indisponible, timeout…)
     * sans dépendre d'un vrai comportement d'infra. Utilisée par `AuditLogUseCaseTest` pour
     * vérifier le fail-closed : ni cette variable, ni son usage, ne contiennent `update`/`delete`/
     * `remove`/`upsert` (voir `AuditLogAppendOnlyTest`, qui inspecte cette classe par réflexion).
     */
    var shouldFailAppend: Boolean = false

    override fun append(entry: AuditLog): Either<UseCaseError, AuditLog> {
        if (shouldFailAppend) {
            return TechnicalError("test.audit.append.failed").left()
        }
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
