package com.zickat.shopifymcpserver.audit.domain.repositories

import arrow.core.Either
import com.zickat.shopifymcpserver.audit.domain.models.AuditLog
import com.zickat.shopifymcpserver.shared_kernel.UseCaseError

/**
 * **Append-only** (`schema.md` §3 : « aucun use case ne fait `update` ni `delete` ») — cette
 * interface ne déclare intentionnellement **aucune** méthode `update`/`delete`/`remove`. La
 * méthode d'écriture s'appelle `append`, pas `save`, pour qu'aucune ambiguïté d'upsert ne
 * s'introduise plus tard. Vérifié par
 * [com.zickat.shopifymcpserver.audit.AuditLogAppendOnlyTest].
 */
interface AuditLogRepository {
    fun append(entry: AuditLog): Either<UseCaseError, AuditLog>

    /** `schema.md` §3, index `(storeId, occurredAt desc)` : « qu'a-t-on fait sur cette boutique ». */
    fun findByStore(storeId: String): Either<UseCaseError, List<AuditLog>>

    /** `schema.md` §3, index `(identityId, occurredAt desc)` : « qu'a fait cette personne ». */
    fun findByIdentity(identityId: String): Either<UseCaseError, List<AuditLog>>
}
