package com.zickat.shopifymcpserver.audit.spi.mongo

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import com.zickat.shopifymcpserver.audit.domain.models.AuditLog
import com.zickat.shopifymcpserver.audit.domain.repositories.AuditLogRepository
import com.zickat.shopifymcpserver.identity.exposed_interface.IdentityExposedService
import com.zickat.shopifymcpserver.shared_kernel.NotFoundError
import com.zickat.shopifymcpserver.shared_kernel.UseCaseError
import com.zickat.shopifymcpserver.tenancy.exposed_interface.StoreExposedService
import org.bson.types.ObjectId
import org.springframework.data.domain.Sort
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.isEqualTo
import org.springframework.stereotype.Repository

/**
 * **Append-only par construction, pas seulement par convention** : cette classe n'appelle jamais
 * `MongoTemplate.save()` (upsert), `.remove()` ni `.updateFirst()` — uniquement `.insert()`
 * (échoue sur un `_id` déjà présent, ne met jamais à jour) et `.find()`. Pas de repository Spring
 * Data ici : `MongoRepository<T,ID>` synthétiserait `delete`/`deleteById`, exactement ce que
 * l'invariant append-only interdit.
 *
 * `schema.md` §3 — `auditLog → identity` (si non nul) et `auditLog → store` : vérifiés via les
 * `exposed_interface` de `identity`/`tenancy`. **Existence seule, jamais « actif »/« non
 * archivé »** : une identité révoquée ou une boutique archivée doivent pouvoir être journalisées
 * (ex. « accès refusé sur boutique archivée ») — le journal ne doit pas refuser d'écrire ce
 * qu'il existe justement pour tracer.
 */
@Repository
class AuditLogMongoRepository(
    private val mongoTemplate: MongoTemplate,
    private val identityExposedService: IdentityExposedService,
    private val storeExposedService: StoreExposedService,
) : AuditLogRepository {

    override fun append(entry: AuditLog): Either<UseCaseError, AuditLog> {
        entry.identityId?.let {
            if (!identityExposedService.exists(it)) return NotFoundError("auditLog.identity.not.found").left()
        }
        if (!storeExposedService.exists(entry.storeId)) {
            return NotFoundError("auditLog.store.not.found").left()
        }
        val inserted = mongoTemplate.insert(AuditLogEntity.fromDomain(entry))
        return inserted.toDomain()
    }

    override fun findByStore(storeId: String): Either<UseCaseError, List<AuditLog>> {
        val query = Query(Criteria.where("storeId").isEqualTo(ObjectId(storeId)))
            .with(Sort.by(Sort.Direction.DESC, "occurredAt"))
        return mongoTemplate.find(query, AuditLogEntity::class.java).map { it.toDomain() }.sequenceEither()
    }

    override fun findByIdentity(identityId: String): Either<UseCaseError, List<AuditLog>> {
        val query = Query(Criteria.where("identityId").isEqualTo(ObjectId(identityId)))
            .with(Sort.by(Sort.Direction.DESC, "occurredAt"))
        return mongoTemplate.find(query, AuditLogEntity::class.java).map { it.toDomain() }.sequenceEither()
    }
}

/** Séquence une liste d'`Either` en `Either<Erreur, Liste>` — petit utilitaire local. */
private fun <T> List<Either<UseCaseError, T>>.sequenceEither(): Either<UseCaseError, List<T>> {
    val result = mutableListOf<T>()
    for (either in this) {
        when (either) {
            is Either.Left -> return either
            is Either.Right -> result.add(either.value)
        }
    }
    return result.right()
}
