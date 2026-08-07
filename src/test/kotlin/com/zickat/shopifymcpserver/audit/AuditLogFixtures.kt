package com.zickat.shopifymcpserver.audit

import com.zickat.shopifymcpserver.audit.domain.models.AuditLog
import com.zickat.shopifymcpserver.audit.domain.models.AuditLogId
import kotlin.time.Clock
import org.bson.types.ObjectId

class AuditLogFixtures {
    private var id = AuditLogId(ObjectId().toHexString())
    private var identityId: String? = ObjectId().toHexString()
    private var storeId = ObjectId().toHexString()
    private var toolName = "list_products"
    private var isMutation = false
    private var outcome = "ok"
    private var denialReason: String? = null
    private var toolInputDigest = mapOf("digest" to "test-digest")
    private var occurredAt = Clock.System.now()

    fun withId(id: AuditLogId) = apply { this.id = id }
    fun withIdentityId(identityId: String?) = apply { this.identityId = identityId }
    fun withStoreId(storeId: String) = apply { this.storeId = storeId }
    fun denied(reason: String) = apply { this.outcome = "denied"; this.denialReason = reason }

    fun build(): AuditLog = AuditLog(
        id = id,
        identityId = identityId,
        storeId = storeId,
        toolName = toolName,
        isMutation = isMutation,
        outcome = outcome,
        denialReason = denialReason,
        toolInputDigest = toolInputDigest,
        occurredAt = occurredAt,
    )
}
