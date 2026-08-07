package com.zickat.shopifymcpserver.audit.domain.models

enum class AuditOutcome(val wireValue: String) {
    OK("ok"),
    DENIED("denied"),
    ERROR("error"),
}
