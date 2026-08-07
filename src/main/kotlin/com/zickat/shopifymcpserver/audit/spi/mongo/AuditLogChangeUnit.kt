package com.zickat.shopifymcpserver.audit.spi.mongo

import com.zickat.shopifymcpserver.shared_kernel.ChangeUnit
import org.springframework.data.domain.Sort
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.index.Index
import org.springframework.stereotype.Component

/** `schema.md` §3 : index `(storeId, occurredAt desc)` et `(identityId, occurredAt desc)`. */
@Component
class AuditLogChangeUnit : ChangeUnit {
    override val id = "audit-log-001-read-indexes"
    override val order = 50

    override fun execute(mongoTemplate: MongoTemplate) {
        mongoTemplate.indexOps(AuditLogEntity.COLLECTION_NAME).ensureIndex(
            Index()
                .on("storeId", Sort.Direction.ASC)
                .on("occurredAt", Sort.Direction.DESC)
                .named("store_occurred_desc"),
        )
        mongoTemplate.indexOps(AuditLogEntity.COLLECTION_NAME).ensureIndex(
            Index()
                .on("identityId", Sort.Direction.ASC)
                .on("occurredAt", Sort.Direction.DESC)
                .named("identity_occurred_desc"),
        )
    }
}
