package com.zickat.shopifymcpserver.identity.spi.mongo

import com.zickat.shopifymcpserver.shared_kernel.ChangeUnit
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.index.Index
import org.springframework.stereotype.Component

@Component
class IdentityChangeUnit : ChangeUnit {
    override val id = "identity-001-unique-issuer-subject"
    override val order = 10

    override fun execute(mongoTemplate: MongoTemplate) {
        mongoTemplate.indexOps(IdentityEntity.COLLECTION_NAME).ensureIndex(
            Index()
                .on("issuer", org.springframework.data.domain.Sort.Direction.ASC)
                .on("subject", org.springframework.data.domain.Sort.Direction.ASC)
                .unique()
                .named("uniq_issuer_subject"),
        )
    }
}
