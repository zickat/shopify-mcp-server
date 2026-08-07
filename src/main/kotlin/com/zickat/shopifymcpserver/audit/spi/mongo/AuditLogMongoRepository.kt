package com.zickat.shopifymcpserver.audit.spi.mongo

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import com.zickat.shopifymcpserver.audit.domain.models.AuditLog
import com.zickat.shopifymcpserver.audit.domain.repositories.AuditLogRepository
import com.zickat.shopifymcpserver.identity.exposed_interface.IdentityExposedService
import com.zickat.shopifymcpserver.shared_kernel.NotFoundError
import com.zickat.shopifymcpserver.shared_kernel.UseCaseError
import org.bson.types.ObjectId
import org.springframework.data.domain.Sort
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.isEqualTo
import org.springframework.stereotype.Repository

@Repository
class AuditLogMongoRepository(
    private val mongoTemplate: MongoTemplate,
    private val identityExposedService: IdentityExposedService,
) : AuditLogRepository {

    override fun append(entry: AuditLog): Either<UseCaseError, AuditLog> {
        entry.identityId?.let {
            if (!identityExposedService.exists(it)) return NotFoundError("auditLog.identity.not.found").left()
        }
        val inserted = mongoTemplate.insert(AuditLogEntity.fromDomain(entry))
        return inserted.toDomain()
    }

    override fun findByStore(storeId: String): Either<UseCaseError, List<AuditLog>> {
        val query = Query(Criteria.where("storeId").isEqualTo(storeId))
            .with(Sort.by(Sort.Direction.DESC, "occurredAt"))
        return mongoTemplate.find(query, AuditLogEntity::class.java).map { it.toDomain() }.sequenceEither()
    }

    override fun findByIdentity(identityId: String): Either<UseCaseError, List<AuditLog>> {
        val query = Query(Criteria.where("identityId").isEqualTo(ObjectId(identityId)))
            .with(Sort.by(Sort.Direction.DESC, "occurredAt"))
        return mongoTemplate.find(query, AuditLogEntity::class.java).map { it.toDomain() }.sequenceEither()
    }
}

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
