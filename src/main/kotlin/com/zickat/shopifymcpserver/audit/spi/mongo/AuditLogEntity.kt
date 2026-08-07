package com.zickat.shopifymcpserver.audit.spi.mongo

import arrow.core.Either
import arrow.core.right
import com.zickat.shopifymcpserver.audit.domain.models.AuditLog
import com.zickat.shopifymcpserver.audit.domain.models.AuditLogId
import com.zickat.shopifymcpserver.shared_kernel.UseCaseError
import kotlinx.datetime.Instant
import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import org.bson.types.ObjectId
import org.springframework.data.mongodb.core.mapping.Document

@Serializable
@Document(AuditLogEntity.COLLECTION_NAME)
data class AuditLogEntity(
    @Contextual val _id: ObjectId,
    @Contextual val identityId: ObjectId?,
    val storeId: String,
    val toolName: String,
    val isMutation: Boolean,
    val outcome: String,
    val denialReason: String?,
    val toolInputDigest: Map<String, String>,
    val occurredAt: Instant,
) {
    companion object {
        const val COLLECTION_NAME = "auditLogs"
        const val STORE_ID_MAX_LENGTH = 128

        fun fromDomain(domain: AuditLog): AuditLogEntity = AuditLogEntity(
            _id = ObjectId(domain.id.value),
            identityId = domain.identityId?.let { ObjectId(it) },
            storeId = domain.storeId.take(STORE_ID_MAX_LENGTH),
            toolName = domain.toolName,
            isMutation = domain.isMutation,
            outcome = domain.outcome,
            denialReason = domain.denialReason,
            toolInputDigest = domain.toolInputDigest,
            occurredAt = domain.occurredAt,
        )
    }

    fun toDomain(): Either<UseCaseError, AuditLog> = AuditLog(
        id = AuditLogId(_id.toHexString()),
        identityId = identityId?.toHexString(),
        storeId = storeId,
        toolName = toolName,
        isMutation = isMutation,
        outcome = outcome,
        denialReason = denialReason,
        toolInputDigest = toolInputDigest,
        occurredAt = occurredAt,
    ).right()
}
