package com.zickat.shopifymcpserver.audit.domain.models

import kotlinx.datetime.Instant

data class AuditLog(
    val id: AuditLogId,
    val identityId: String?,
    val storeId: String,
    val toolName: String,
    val isMutation: Boolean,
    val outcome: String,
    val denialReason: String?,
    val toolInputDigest: Map<String, String>,
    val occurredAt: Instant,
)
