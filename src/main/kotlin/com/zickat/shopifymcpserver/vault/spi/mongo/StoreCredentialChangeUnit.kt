package com.zickat.shopifymcpserver.vault.spi.mongo

import com.zickat.shopifymcpserver.shared_kernel.ChangeUnit
import org.springframework.data.domain.Sort
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.index.Index
import org.springframework.data.mongodb.core.index.PartialIndexFilter
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.isEqualTo
import org.springframework.stereotype.Component

@Component
class StoreCredentialChangeUnit : ChangeUnit {
    override val id = "store-credential-001-unique-active-per-store"
    override val order = 40

    override fun execute(mongoTemplate: MongoTemplate) {
        mongoTemplate.indexOps(StoreCredentialEntity.COLLECTION_NAME).ensureIndex(
            Index()
                .on("storeId", Sort.Direction.ASC)
                .unique()
                .named("uniq_active_store")
                .partial(PartialIndexFilter.of(Criteria.where("revokedAt").isEqualTo(null))),
        )
    }
}
